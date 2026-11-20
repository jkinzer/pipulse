package com.kinzer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Real network client communicating with Pi-hole v6 REST API (/api/...) and
 * Pi-hole v5 legacy API (/admin/api.php) over HTTP or HTTPS.
 */
class PiholeApiClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val v6Sessions = ConcurrentHashMap<String, String>()

    private fun buildBaseUrl(profile: PiholeDeviceProfile): String {
        val scheme = if (profile.useHttps) "https" else "http"
        val cleanHost = profile.host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val cleanBase = profile.basePath.trim().trim('/')
        val baseSegment = if (cleanBase.isEmpty()) "" else "/$cleanBase"
        return "$scheme://$cleanHost:${profile.port}$baseSegment"
    }

    private fun sessionKey(profile: PiholeDeviceProfile): String =
        if (profile.id.isNotBlank()) profile.id else "${profile.host}:${profile.port}"

    fun clearSession(deviceId: String) {
        if (deviceId.isNotBlank()) {
            v6Sessions.remove(deviceId)
        }
    }

    suspend fun testConnection(
        profile: PiholeDeviceProfile,
        secret: String
    ): PiholeConnectionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val summary = when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> {
                    v6Sessions.remove(sessionKey(profile))
                    if (secret.isNotBlank()) {
                        authenticateV6(profile, secret)
                    }
                    fetchV6Summary(profile, secret)
                }
                PiholeApiVersion.V5 -> fetchV5Summary(profile, secret)
            }
            val latency = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            PiholeConnectionResult(
                connected = true,
                message = "Connected in ${latency}ms • ${summary.totalQueries} queries today"
            )
        } catch (e: Exception) {
            PiholeConnectionResult(
                connected = false,
                message = humanizeNetworkError(e)
            )
        }
    }

    suspend fun fetchTelemetrySnapshot(
        profile: PiholeDeviceProfile,
        secret: String
    ): Result<PiholeTelemetrySnapshot> = withContext(Dispatchers.IO) {
        try {
            val snapshot = when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> fetchV6Telemetry(profile, secret)
                PiholeApiVersion.V5 -> fetchV5Telemetry(profile, secret)
            }
            Result.success(snapshot)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun fetchQueryLogs(
        profile: PiholeDeviceProfile,
        secret: String,
        limit: Int = 150
    ): Result<List<DnsQueryLogEntry>> = withContext(Dispatchers.IO) {
        try {
            val entries = when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> fetchV6Queries(profile, secret, limit)
                PiholeApiVersion.V5 -> fetchV5Queries(profile, secret, limit)
            }
            Result.success(entries)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun setBlocking(
        profile: PiholeDeviceProfile,
        secret: String,
        enabled: Boolean,
        timerSeconds: Int? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> {
                    val body = JSONObject().apply {
                        put("blocking", enabled)
                        if (!enabled && timerSeconds != null && timerSeconds > 0) {
                            put("timer", timerSeconds)
                        } else {
                            put("timer", JSONObject.NULL)
                        }
                    }
                    executeV6Request(
                        profile = profile,
                        secret = secret,
                        method = "POST",
                        path = "/api/dns/blocking",
                        jsonBody = body.toString()
                    )
                }
                PiholeApiVersion.V5 -> {
                    val actionParam = if (enabled) {
                        "enable"
                    } else if (timerSeconds != null && timerSeconds > 0) {
                        "disable=$timerSeconds"
                    } else {
                        "disable"
                    }
                    executeV5Get(profile, secret, actionParam)
                }
            }
            Result.success(enabled)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun fetchDomainRules(
        profile: PiholeDeviceProfile,
        secret: String
    ): Result<List<DomainListRule>> = withContext(Dispatchers.IO) {
        try {
            val rules = when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> fetchV6Domains(profile, secret)
                PiholeApiVersion.V5 -> fetchV5Domains(profile, secret)
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun addDomainRule(
        profile: PiholeDeviceProfile,
        secret: String,
        domain: String,
        listType: DomainListType,
        regexRule: Boolean,
        comment: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanDomain = domain.trim()
        if (cleanDomain.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Domain cannot be empty."))
        }
        try {
            when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> {
                    val kind = if (regexRule) "regex" else "exact"
                    val payload = JSONObject().apply {
                        put("domain", cleanDomain)
                        put("enabled", true)
                        if (comment.isNotBlank()) {
                            put("comment", comment.trim())
                        }
                    }
                    executeV6Request(
                        profile = profile,
                        secret = secret,
                        method = "POST",
                        path = "/api/domains/${listType.apiPath}/$kind",
                        jsonBody = payload.toString()
                    )
                }
                PiholeApiVersion.V5 -> {
                    val v5List = if (regexRule) listType.v5Regex else listType.v5Exact
                    val encodedDomain = encodeUrl(cleanDomain)
                    val commentQuery = if (comment.isNotBlank()) "&comment=${encodeUrl(comment.trim())}" else ""
                    executeV5Get(profile, secret, "list=$v5List&add=$encodedDomain$commentQuery")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun removeDomainRule(
        profile: PiholeDeviceProfile,
        secret: String,
        rule: DomainListRule
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> {
                    val kind = if (rule.regexRule) "regex" else "exact"
                    val encodedDomain = encodeUrl(rule.domain)
                    executeV6Request(
                        profile = profile,
                        secret = secret,
                        method = "DELETE",
                        path = "/api/domains/${rule.listType.apiPath}/$kind/$encodedDomain",
                        jsonBody = null
                    )
                }
                PiholeApiVersion.V5 -> {
                    val v5List = if (rule.regexRule) rule.listType.v5Regex else rule.listType.v5Exact
                    executeV5Get(profile, secret, "list=$v5List&sub=${encodeUrl(rule.domain)}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun triggerGravityUpdate(
        profile: PiholeDeviceProfile,
        secret: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (profile.parsedApiVersion == PiholeApiVersion.V6) {
                executeV6Request(
                    profile = profile,
                    secret = secret,
                    method = "POST",
                    path = "/api/action/gravity",
                    jsonBody = "{}"
                )
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Remote Gravity updates require Pi-hole v6 API."))
            }
        } catch (e: Exception) {
            Result.failure(Exception(humanizeNetworkError(e), e))
        }
    }

    suspend fun fetchDeviceHealth(
        profile: PiholeDeviceProfile,
        secret: String
    ): DeviceHealthMetrics = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            when (profile.parsedApiVersion) {
                PiholeApiVersion.V6 -> fetchV6DeviceHealth(profile, secret, startMs)
                PiholeApiVersion.V5 -> fetchV5DeviceHealth(profile, secret, startMs)
            }
        } catch (e: Exception) {
            DeviceHealthMetrics(
                online = false,
                errorMessage = humanizeNetworkError(e)
            )
        }
    }

    // =========================================================================
    // PI-HOLE V6 REST API IMPLEMENTATION
    // =========================================================================

    private fun authenticateV6(profile: PiholeDeviceProfile, password: String): String {
        val url = "${buildBaseUrl(profile)}/api/auth"
        val body = JSONObject().put("password", password).toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Pi-hole v6 auth failed (HTTP ${response.code}). Check password.")
            }
            val json = JSONObject(raw)
            val sessionObj = json.optJSONObject("session")
            val valid = sessionObj?.optBoolean("valid", false) ?: false
            val sid = sessionObj?.optString("sid", "").orEmpty()
            if (!valid || sid.isBlank()) {
                val msg = sessionObj?.optString("message").orEmpty().ifBlank { "Invalid Pi-hole password." }
                throw IllegalStateException(msg)
            }
            v6Sessions[sessionKey(profile)] = sid
            return sid
        }
    }

    private fun executeV6Request(
        profile: PiholeDeviceProfile,
        secret: String,
        method: String,
        path: String,
        jsonBody: String? = null,
        allowReauth: Boolean = true
    ): String {
        val key = sessionKey(profile)
        var sid = v6Sessions[key]
        if (sid.isNullOrBlank() && secret.isNotBlank()) {
            sid = authenticateV6(profile, secret)
        }

        val url = "${buildBaseUrl(profile)}$path"
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
        if (!sid.isNullOrBlank()) {
            builder.header("X-FTL-SID", sid)
        }

        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post((jsonBody ?: "{}").toRequestBody(jsonMediaType))
            "DELETE" -> if (jsonBody != null) builder.delete(jsonBody.toRequestBody(jsonMediaType)) else builder.delete()
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.code == 401 && allowReauth && secret.isNotBlank()) {
                v6Sessions.remove(key)
                authenticateV6(profile, secret)
                return executeV6Request(profile, secret, method, path, jsonBody, allowReauth = false)
            }
            if (!response.isSuccessful) {
                val detail = extractV6ErrorMessage(raw)
                throw IllegalStateException(
                    if (detail.isNotBlank()) "HTTP ${response.code}: $detail"
                    else "Pi-hole returned HTTP ${response.code}"
                )
            }
            return raw
        }
    }

    private fun fetchV6Summary(profile: PiholeDeviceProfile, secret: String): PiholeSummaryStats {
        val summaryRaw = executeV6Request(profile, secret, "GET", "/api/stats/summary")
        val json = JSONObject(summaryRaw)
        val queries = json.optJSONObject("queries") ?: JSONObject()
        val clients = json.optJSONObject("clients") ?: JSONObject()
        val gravity = json.optJSONObject("gravity") ?: JSONObject()

        var blockingEnabled = true
        var timerSec: Long? = null
        runCatching {
            val blockJson = JSONObject(executeV6Request(profile, secret, "GET", "/api/dns/blocking"))
            val state = blockJson.optString("blocking", "enabled")
            blockingEnabled = state.equals("enabled", ignoreCase = true)
            if (!blockJson.isNull("timer")) {
                timerSec = blockJson.optDouble("timer", 0.0).toLong().takeIf { it > 0L }
            }
        }

        return PiholeSummaryStats(
            totalQueries = queries.optLong("total", 0L),
            blockedQueries = queries.optLong("blocked", 0L),
            percentBlocked = queries.optDouble("percent_blocked", 0.0),
            domainsOnBlocklist = gravity.optLong("domains_being_blocked", 0L),
            activeClients = clients.optLong("active", clients.optLong("total", 0L)),
            uniqueDomains = queries.optLong("unique_domains", 0L),
            queriesForwarded = queries.optLong("forwarded", 0L),
            queriesCached = queries.optLong("cached", 0L),
            blockingEnabled = blockingEnabled,
            disableRemainingSeconds = timerSec
        )
    }

    private fun fetchV6DeviceHealth(
        profile: PiholeDeviceProfile,
        secret: String,
        startMs: Long
    ): DeviceHealthMetrics {
        val systemRaw = executeV6Request(profile, secret, "GET", "/api/info/system")
        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val systemRoot = JSONObject(systemRaw).optJSONObject("system") ?: JSONObject()

        val uptimeSeconds = systemRoot.optLong("uptime", -1L).takeIf { it >= 0L }

        val cpuObj = systemRoot.optJSONObject("cpu")
        val cpuPercent = cpuObj?.let { optDoubleOrNull(it, "%cpu") }
        val cpuCores = cpuObj?.optInt("nprocs", 0)?.takeIf { it > 0 }
        val loadRaw = cpuObj?.optJSONObject("load")?.optJSONArray("raw")
        val load1m = loadRaw?.takeIf { it.length() > 0 }?.optDouble(0)?.takeIf { !it.isNaN() }

        val ramObj = systemRoot.optJSONObject("memory")?.optJSONObject("ram")
        val memPercent = ramObj?.let { optDoubleOrNull(it, "%used") }
        val memTotalKb = ramObj?.optLong("total", 0L)?.takeIf { it > 0L }
        val memUsedKb = ramObj?.optLong("used", -1L)?.takeIf { it >= 0L }
        val memTotalMb = memTotalKb?.let { it / 1024L }
        val memUsedMb = memUsedKb?.let { it / 1024L }

        var cpuTempC: Double? = null
        var hotLimitC: Double? = null
        runCatching {
            val sensorRaw = executeV6Request(profile, secret, "GET", "/api/info/sensors")
            val sensors = JSONObject(sensorRaw).optJSONObject("sensors")
            if (sensors != null) {
                val unit = sensors.optString("unit", "C").uppercase()
                val rawTemp = optDoubleOrNull(sensors, "cpu_temp")
                val rawHot = optDoubleOrNull(sensors, "hot_limit")
                cpuTempC = rawTemp?.let { normalizeCelsius(it, unit) }
                hotLimitC = rawHot?.let { normalizeCelsius(it, unit) }
            }
        }

        var blockingEnabled: Boolean? = null
        runCatching {
            val blockJson = JSONObject(executeV6Request(profile, secret, "GET", "/api/dns/blocking"))
            blockingEnabled = blockJson.optString("blocking", "").equals("enabled", ignoreCase = true)
        }

        var coreVersion: String? = null
        var ftlVersion: String? = null
        var updateAvailable = false
        runCatching {
            val verRaw = executeV6Request(profile, secret, "GET", "/api/info/version")
            val versionObj = JSONObject(verRaw).optJSONObject("version")
            if (versionObj != null) {
                val core = versionObj.optJSONObject("core")
                val ftl = versionObj.optJSONObject("ftl")
                val web = versionObj.optJSONObject("web")
                coreVersion = core?.optJSONObject("local")?.optString("version")?.takeIf { it.isNotBlank() && it != "null" }
                ftlVersion = ftl?.optJSONObject("local")?.optString("version")?.takeIf { it.isNotBlank() && it != "null" }
                updateAvailable = listOf(core, ftl, web).any { component ->
                    val localVer = component?.optJSONObject("local")?.optString("version").orEmpty()
                    val remoteVer = component?.optJSONObject("remote")?.optString("version").orEmpty()
                    localVer.isNotBlank() && remoteVer.isNotBlank() &&
                        !remoteVer.equals("null", ignoreCase = true) &&
                        !localVer.equals(remoteVer, ignoreCase = true)
                }
            }
        }

        return DeviceHealthMetrics(
            online = true,
            latencyMs = latencyMs,
            blockingEnabled = blockingEnabled,
            cpuPercent = cpuPercent,
            cpuLoad1m = load1m,
            cpuCores = cpuCores,
            memoryUsedPercent = memPercent,
            memoryUsedMb = memUsedMb,
            memoryTotalMb = memTotalMb,
            cpuTempCelsius = cpuTempC,
            tempHotLimitCelsius = hotLimitC,
            uptimeSeconds = uptimeSeconds,
            coreVersion = coreVersion,
            ftlVersion = ftlVersion,
            updateAvailable = updateAvailable
        )
    }

    private fun fetchV5DeviceHealth(
        profile: PiholeDeviceProfile,
        secret: String,
        startMs: Long
    ): DeviceHealthMetrics {
        val summary = fetchV5Summary(profile, secret)
        val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        return DeviceHealthMetrics(
            online = true,
            latencyMs = latencyMs,
            blockingEnabled = summary.blockingEnabled
        )
    }

    private fun optDoubleOrNull(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.optDouble(key, Double.NaN)
        return if (value.isNaN() || value.isInfinite()) null else value
    }

    private fun normalizeCelsius(value: Double, unit: String): Double = when (unit) {
        "F" -> (value - 32.0) * 5.0 / 9.0
        "K" -> value - 273.15
        else -> value
    }

    private fun fetchV6Telemetry(profile: PiholeDeviceProfile, secret: String): PiholeTelemetrySnapshot {
        val summary = fetchV6Summary(profile, secret)

        val history = runCatching {
            val raw = executeV6Request(profile, secret, "GET", "/api/history")
            val arr = JSONObject(raw).optJSONArray("history") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        HistoryBucket(
                            timestampEpoch = obj.optLong("timestamp", 0L),
                            totalQueries = obj.optLong("total", 0L),
                            blockedQueries = obj.optLong("blocked", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        val topBlocked = runCatching {
            val raw = executeV6Request(profile, secret, "GET", "/api/stats/top_domains?blocked=true&count=12")
            parseV6RankedList(JSONObject(raw).optJSONArray("domains"), "domain", "count")
        }.getOrDefault(emptyList())

        val topPermitted = runCatching {
            val raw = executeV6Request(profile, secret, "GET", "/api/stats/top_domains?blocked=false&count=12")
            parseV6RankedList(JSONObject(raw).optJSONArray("domains"), "domain", "count")
        }.getOrDefault(emptyList())

        val topClients = runCatching {
            val raw = executeV6Request(profile, secret, "GET", "/api/stats/top_clients?count=12")
            val arr = JSONObject(raw).optJSONArray("clients") ?: JSONArray()
            val maxCount = (0 until arr.length()).maxOfOrNull {
                arr.optJSONObject(it)?.optLong("count", 0L) ?: 0L
            }?.coerceAtLeast(1L) ?: 1L
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").ifBlank { obj.optString("ip", "Unknown") }
                    val ip = obj.optString("ip", "")
                    val count = obj.optLong("count", 0L)
                    add(
                        RankedMetricItem(
                            label = name,
                            count = count,
                            sharePercent = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f),
                            secondaryLabel = if (ip != name) ip else ""
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())

        val queryTypes = runCatching {
            val raw = executeV6Request(profile, secret, "GET", "/api/stats/query_types")
            val typesObj = JSONObject(raw).optJSONObject("types") ?: JSONObject()
            val total = typesObj.keys().asSequence().sumOf { typesObj.optLong(it, 0L) }.coerceAtLeast(1L)
            typesObj.keys().asSequence()
                .map { typeKey ->
                    val count = typesObj.optLong(typeKey, 0L)
                    QueryTypeMetric(
                        type = typeKey,
                        percentage = (count.toFloat() / total.toFloat()) * 100f,
                        count = count
                    )
                }
                .filter { it.count > 0L }
                .sortedByDescending { it.count }
                .toList()
        }.getOrDefault(emptyList())

        return PiholeTelemetrySnapshot(
            summary = summary,
            history = history,
            topBlockedDomains = topBlocked,
            topPermittedDomains = topPermitted,
            topClients = topClients,
            queryTypes = queryTypes
        )
    }

    private fun fetchV6Queries(
        profile: PiholeDeviceProfile,
        secret: String,
        limit: Int
    ): List<DnsQueryLogEntry> {
        val raw = executeV6Request(profile, secret, "GET", "/api/queries?length=$limit")
        val arr = JSONObject(raw).optJSONArray("queries") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val statusStr = obj.optString("status", "UNKNOWN")
                val clientObj = obj.optJSONObject("client")
                val clientDisplay = clientObj?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: clientObj?.optString("ip")?.takeIf { it.isNotBlank() }
                    ?: obj.optString("client", "Unknown")
                val replyObj = obj.optJSONObject("reply")
                val replyTimeSec = replyObj?.optDouble("time", -1.0) ?: -1.0
                val latencyMs = if (replyTimeSec >= 0.0) replyTimeSec * 1000.0 else null
                add(
                    DnsQueryLogEntry(
                        id = obj.optString("id", "${obj.optDouble("time")}_$i"),
                        timestampEpoch = obj.optDouble("time", 0.0).toLong(),
                        queryType = obj.optString("type", "A"),
                        domain = obj.optString("domain", ""),
                        client = clientDisplay,
                        statusLabel = humanizeV6Status(statusStr),
                        category = categorizeStatus(statusStr),
                        responseTimeMs = latencyMs
                    )
                )
            }
        }
    }

    private fun fetchV6Domains(profile: PiholeDeviceProfile, secret: String): List<DomainListRule> {
        val raw = executeV6Request(profile, secret, "GET", "/api/domains")
        val arr = JSONObject(raw).optJSONArray("domains") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val typeStr = obj.optString("type", "deny")
                val kindStr = obj.optString("kind", "exact")
                val listType = if (typeStr.equals("allow", ignoreCase = true)) DomainListType.ALLOW else DomainListType.DENY
                add(
                    DomainListRule(
                        id = obj.optString("id", "${typeStr}_${kindStr}_${obj.optString("domain")}_$i"),
                        domain = obj.optString("domain", ""),
                        listType = listType,
                        regexRule = kindStr.equals("regex", ignoreCase = true),
                        comment = if (obj.isNull("comment")) "" else obj.optString("comment", ""),
                        dateAddedEpoch = obj.optLong("date_added", 0L)
                    )
                )
            }
        }.sortedByDescending { it.dateAddedEpoch }
    }

    // =========================================================================
    // PI-HOLE V5 LEGACY API IMPLEMENTATION (/admin/api.php)
    // =========================================================================

    private fun executeV5Get(
        profile: PiholeDeviceProfile,
        token: String,
        query: String
    ): String {
        val authPart = if (token.isNotBlank()) "&auth=${encodeUrl(token)}" else ""
        val base = buildBaseUrl(profile)
        val endpoint = if (base.endsWith("/admin")) "$base/api.php" else "$base/admin/api.php"
        val url = "$endpoint?$query$authPart"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Pi-hole v5 API returned HTTP ${response.code}")
            }
            if (raw.trim() == "[]" && query.contains("getAllQueries")) {
                throw IllegalStateException("Pi-hole v5 returned empty auth response. Verify API token.")
            }
            return raw
        }
    }

    private fun fetchV5Summary(profile: PiholeDeviceProfile, token: String): PiholeSummaryStats {
        val raw = executeV5Get(profile, token, "summaryRaw")
        val json = JSONObject(raw)
        val status = json.optString("status", "enabled")
        return PiholeSummaryStats(
            totalQueries = json.optLong("dns_queries_today", 0L),
            blockedQueries = json.optLong("ads_blocked_today", 0L),
            percentBlocked = json.optDouble("ads_percentage_today", 0.0),
            domainsOnBlocklist = json.optLong("domains_being_blocked", 0L),
            activeClients = json.optLong("unique_clients", 0L),
            uniqueDomains = json.optLong("unique_domains", 0L),
            queriesForwarded = json.optLong("queries_forwarded", 0L),
            queriesCached = json.optLong("queries_cached", 0L),
            blockingEnabled = status.equals("enabled", ignoreCase = true)
        )
    }

    private fun fetchV5Telemetry(profile: PiholeDeviceProfile, token: String): PiholeTelemetrySnapshot {
        val summary = fetchV5Summary(profile, token)

        val history = runCatching {
            val json = JSONObject(executeV5Get(profile, token, "overTimeData10mins"))
            val domainsObj = json.optJSONObject("domains_over_time") ?: JSONObject()
            val adsObj = json.optJSONObject("ads_over_time") ?: JSONObject()
            domainsObj.keys().asSequence()
                .mapNotNull { key ->
                    val ts = key.toLongOrNull() ?: return@mapNotNull null
                    HistoryBucket(
                        timestampEpoch = ts,
                        totalQueries = domainsObj.optLong(key, 0L),
                        blockedQueries = adsObj.optLong(key, 0L)
                    )
                }
                .sortedBy { it.timestampEpoch }
                .toList()
        }.getOrDefault(emptyList())

        var topPermitted = emptyList<RankedMetricItem>()
        var topBlocked = emptyList<RankedMetricItem>()
        runCatching {
            val json = JSONObject(executeV5Get(profile, token, "topItems=12"))
            topPermitted = parseV5RankedObject(json.optJSONObject("top_queries"))
            topBlocked = parseV5RankedObject(json.optJSONObject("top_ads"))
        }

        val topClients = runCatching {
            val json = JSONObject(executeV5Get(profile, token, "topClients=12"))
            parseV5RankedObject(json.optJSONObject("top_sources"))
        }.getOrDefault(emptyList())

        val queryTypes = runCatching {
            val json = JSONObject(executeV5Get(profile, token, "getQueryTypes"))
            val obj = json.optJSONObject("querytypes") ?: JSONObject()
            obj.keys().asSequence()
                .map { k ->
                    QueryTypeMetric(
                        type = k,
                        percentage = obj.optDouble(k, 0.0).toFloat()
                    )
                }
                .filter { it.percentage > 0f }
                .sortedByDescending { it.percentage }
                .toList()
        }.getOrDefault(emptyList())

        return PiholeTelemetrySnapshot(
            summary = summary,
            history = history,
            topBlockedDomains = topBlocked,
            topPermittedDomains = topPermitted,
            topClients = topClients,
            queryTypes = queryTypes
        )
    }

    private fun fetchV5Queries(
        profile: PiholeDeviceProfile,
        token: String,
        limit: Int
    ): List<DnsQueryLogEntry> {
        val raw = executeV5Get(profile, token, "getAllQueries=$limit")
        val arr = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in arr.length() - 1 downTo 0) {
                val row = arr.optJSONArray(i) ?: continue
                if (row.length() < 5) continue
                val ts = row.optString(0).toLongOrNull() ?: 0L
                val qType = row.optString(1, "A")
                val domain = row.optString(2, "")
                val client = row.optString(3, "")
                val statusCode = row.optString(4, "0").toIntOrNull() ?: 0
                val latency = if (row.length() > 7) row.optDouble(7, -1.0).takeIf { it >= 0.0 }?.div(10.0) else null
                add(
                    DnsQueryLogEntry(
                        id = "${ts}_${domain}_$i",
                        timestampEpoch = ts,
                        queryType = qType,
                        domain = domain,
                        client = client,
                        statusLabel = humanizeV5Status(statusCode),
                        category = categorizeV5Status(statusCode),
                        responseTimeMs = latency
                    )
                )
            }
        }
    }

    private fun fetchV5Domains(profile: PiholeDeviceProfile, token: String): List<DomainListRule> {
        val specs = listOf(
            Triple("white", DomainListType.ALLOW, false),
            Triple("regex_white", DomainListType.ALLOW, true),
            Triple("black", DomainListType.DENY, false),
            Triple("regex_black", DomainListType.DENY, true)
        )
        return buildList {
            for ((queryParam, listType, isRegex) in specs) {
                val raw = runCatching { executeV5Get(profile, token, "list=$queryParam") }.getOrNull() ?: continue
                val dataArr = runCatching { JSONObject(raw).optJSONArray("data") }.getOrNull() ?: continue
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.optJSONObject(i) ?: continue
                    add(
                        DomainListRule(
                            id = obj.optString("id", "${queryParam}_${obj.optString("domain")}_$i"),
                            domain = obj.optString("domain", ""),
                            listType = listType,
                            regexRule = isRegex,
                            comment = if (obj.isNull("comment")) "" else obj.optString("comment", ""),
                            dateAddedEpoch = obj.optLong("date_added", 0L)
                        )
                    )
                }
            }
        }.sortedByDescending { it.dateAddedEpoch }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun parseV6RankedList(arr: JSONArray?, keyField: String, countField: String): List<RankedMetricItem> {
        if (arr == null || arr.length() == 0) return emptyList()
        val maxCount = (0 until arr.length()).maxOfOrNull {
            arr.optJSONObject(it)?.optLong(countField, 0L) ?: 0L
        }?.coerceAtLeast(1L) ?: 1L
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val label = obj.optString(keyField, "")
                val count = obj.optLong(countField, 0L)
                if (label.isNotBlank()) {
                    add(
                        RankedMetricItem(
                            label = label,
                            count = count,
                            sharePercent = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }
    }

    private fun parseV5RankedObject(obj: JSONObject?): List<RankedMetricItem> {
        if (obj == null) return emptyList()
        val pairs = obj.keys().asSequence().map { k -> k to obj.optLong(k, 0L) }.sortedByDescending { it.second }.toList()
        val maxCount = pairs.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        return pairs.map { (rawKey, count) ->
            val parts = rawKey.split("|")
            RankedMetricItem(
                label = parts.firstOrNull().orEmpty().ifBlank { rawKey },
                count = count,
                sharePercent = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f),
                secondaryLabel = parts.getOrNull(1).orEmpty()
            )
        }
    }

    private fun categorizeStatus(v6Status: String): QueryStatusCategory {
        val upper = v6Status.uppercase()
        return when {
            upper.contains("GRAVITY") || upper.contains("DENY") || upper.contains("BLACK") || upper.contains("BLOCK") || upper.contains("EXTERNAL") -> QueryStatusCategory.BLOCKED
            upper.contains("CACHE") -> QueryStatusCategory.CACHED
            upper.contains("FORWARD") || upper.contains("ALLOW") || upper.contains("OK") || upper.contains("RETRIED") -> QueryStatusCategory.PERMITTED
            else -> QueryStatusCategory.PERMITTED
        }
    }

    private fun humanizeV6Status(status: String): String = when (status.uppercase()) {
        "GRAVITY" -> "Blocked (Gravity)"
        "REGEX" -> "Blocked (Regex)"
        "DENYLIST", "BLACKLIST" -> "Blocked (Blacklist)"
        "EXTERNAL_BLOCKED_IP", "EXTERNAL_BLOCKED_NULL", "EXTERNAL_BLOCKED_NXRA" -> "Blocked (Upstream)"
        "CACHE", "CACHE_STALE" -> "Cached"
        "FORWARDED" -> "Forwarded"
        "ALLOWLIST" -> "Whitelisted"
        else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun categorizeV5Status(code: Int): QueryStatusCategory = when (code) {
        1, 4, 5, 6, 7, 8, 9, 10, 11 -> QueryStatusCategory.BLOCKED
        3 -> QueryStatusCategory.CACHED
        else -> QueryStatusCategory.PERMITTED
    }

    private fun humanizeV5Status(code: Int): String = when (code) {
        1 -> "Blocked (Gravity)"
        2 -> "Forwarded"
        3 -> "Cached"
        4 -> "Blocked (Regex)"
        5 -> "Blocked (Blacklist)"
        6, 7, 8 -> "Blocked (Upstream)"
        9, 10, 11 -> "Blocked (Deep CNAME)"
        12, 13 -> "Retried"
        else -> "Permitted"
    }

    private fun extractV6ErrorMessage(raw: String): String = runCatching {
        val json = JSONObject(raw)
        json.optJSONObject("error")?.optString("message").orEmpty()
            .ifBlank { json.optString("message", "") }
    }.getOrDefault("")

    private fun encodeUrl(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun humanizeNetworkError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Unable to resolve host", ignoreCase = true) ->
                "Host unreachable. Verify the IP/hostname and ensure VPN or LAN access is active."
            msg.contains("Failed to connect", ignoreCase = true) || msg.contains("ECONNREFUSED", ignoreCase = true) ->
                "Connection refused. Verify the Pi-hole port, HTTP/HTTPS mode, and server status."
            msg.contains("timeout", ignoreCase = true) ->
                "Connection timed out. Ensure your device can reach this Pi-hole network."
            msg.contains("SSLHandshakeException", ignoreCase = true) || msg.contains("CertPathValidatorException", ignoreCase = true) ->
                "TLS certificate verification failed. Check HTTPS setting or server certificate."
            msg.isNotBlank() -> msg
            else -> "Network request to Pi-hole failed."
        }
    }
}
