package com.kinzer.data

const val ALL_DEVICES_ID = "__ALL_DEVICES__"

enum class PiholeApiVersion(val wireValue: String, val displayLabel: String) {
    V6("V6", "Pi-hole v6 REST API"),
    V5("V5", "Pi-hole v5 Legacy API");

    companion object {
        fun fromWire(raw: String?): PiholeApiVersion =
            entries.firstOrNull { it.wireValue.equals(raw, ignoreCase = true) } ?: V6
    }
}

enum class AuditActionType(val wireValue: String, val label: String) {
    BLOCKING_ENABLED("BLOCKING_ENABLED", "Blocking Enabled"),
    BLOCKING_DISABLED("BLOCKING_DISABLED", "Blocking Paused"),
    WHITELIST_ADD("WHITELIST_ADD", "Whitelisted Domain"),
    WHITELIST_REMOVE("WHITELIST_REMOVE", "Removed from Whitelist"),
    BLACKLIST_ADD("BLACKLIST_ADD", "Blacklisted Domain"),
    BLACKLIST_REMOVE("BLACKLIST_REMOVE", "Removed from Blacklist"),
    DEVICE_ADDED("DEVICE_ADDED", "Added Pi-hole Device"),
    DEVICE_UPDATED("DEVICE_UPDATED", "Updated Pi-hole Device"),
    DEVICE_REMOVED("DEVICE_REMOVED", "Removed Pi-hole Device"),
    GRAVITY_UPDATE("GRAVITY_UPDATE", "Gravity Update Triggered");

    companion object {
        fun fromWire(raw: String?): AuditActionType =
            entries.firstOrNull { it.wireValue == raw } ?: DEVICE_UPDATED
    }
}

data class LocalAppSettings(
    val activeDeviceId: String = "",
    val refreshIntervalSeconds: Long = 5L
)

data class PiholeDeviceProfile(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val port: Long = 80L,
    val useHttps: Boolean = false,
    val apiVersion: String = PiholeApiVersion.V6.wireValue,
    val basePath: String = "",
    val locationTag: String = "Home LAN",
    val notes: String = "",
    val hasStoredCredential: Boolean = false,
    val lastConnectedAtMs: Long = 0L,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    val parsedApiVersion: PiholeApiVersion
        get() = PiholeApiVersion.fromWire(apiVersion)

    val endpointDisplay: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            val defaultPort = if (useHttps) 443L else 80L
            val portSuffix = if (port == defaultPort) "" else ":$port"
            val cleanBase = basePath.trim().trimEnd('/')
            val pathPart = if (cleanBase.isEmpty()) "" else if (cleanBase.startsWith("/")) cleanBase else "/$cleanBase"
            return "$scheme://${host.trim()}$portSuffix$pathPart"
        }
}

data class ManagementAuditLog(
    val id: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val actionType: String = AuditActionType.DEVICE_UPDATED.wireValue,
    val target: String = "",
    val createdAtMs: Long = 0L
) {
    val parsedAction: AuditActionType
        get() = AuditActionType.fromWire(actionType)
}

// --- LIVE PI-HOLE TELEMETRY MODELS ---

data class PiholeSummaryStats(
    val totalQueries: Long = 0L,
    val blockedQueries: Long = 0L,
    val percentBlocked: Double = 0.0,
    val domainsOnBlocklist: Long = 0L,
    val activeClients: Long = 0L,
    val uniqueDomains: Long = 0L,
    val queriesForwarded: Long = 0L,
    val queriesCached: Long = 0L,
    val blockingEnabled: Boolean = true,
    val disableRemainingSeconds: Long? = null
)

data class HistoryBucket(
    val timestampEpoch: Long,
    val totalQueries: Long,
    val blockedQueries: Long
)

enum class QueryStatusCategory(val label: String) {
    ALL("All"),
    BLOCKED("Blocked"),
    PERMITTED("Permitted"),
    CACHED("Cached")
}

data class DnsQueryLogEntry(
    val id: String,
    val timestampEpoch: Long,
    val queryType: String,
    val domain: String,
    val client: String,
    val statusLabel: String,
    val category: QueryStatusCategory,
    val responseTimeMs: Double?,
    val deviceId: String = "",
    val deviceName: String = ""
)

data class RankedMetricItem(
    val label: String,
    val count: Long,
    val sharePercent: Float,
    val secondaryLabel: String = ""
)

data class QueryTypeMetric(
    val type: String,
    val percentage: Float,
    val count: Long = 0L
)

enum class DomainListType(val apiPath: String, val v5Exact: String, val v5Regex: String, val label: String) {
    ALLOW("allow", "white", "regex_white", "Whitelist"),
    DENY("deny", "black", "regex_black", "Blacklist")
}

data class DomainListRule(
    val id: String,
    val domain: String,
    val listType: DomainListType,
    val regexRule: Boolean,
    val comment: String,
    val dateAddedEpoch: Long = 0L,
    val deviceId: String = "",
    val deviceName: String = ""
)

data class PiholeConnectionResult(
    val connected: Boolean,
    val message: String = ""
)

data class PiholeTelemetrySnapshot(
    val summary: PiholeSummaryStats,
    val history: List<HistoryBucket>,
    val topBlockedDomains: List<RankedMetricItem>,
    val topPermittedDomains: List<RankedMetricItem>,
    val topClients: List<RankedMetricItem>,
    val queryTypes: List<QueryTypeMetric>
)

data class DeviceHealthMetrics(
    val online: Boolean,
    val latencyMs: Long? = null,
    val blockingEnabled: Boolean? = null,
    val cpuPercent: Double? = null,
    val cpuLoad1m: Double? = null,
    val cpuCores: Int? = null,
    val memoryUsedPercent: Double? = null,
    val memoryUsedMb: Long? = null,
    val memoryTotalMb: Long? = null,
    val cpuTempCelsius: Double? = null,
    val tempHotLimitCelsius: Double? = null,
    val uptimeSeconds: Long? = null,
    val coreVersion: String? = null,
    val ftlVersion: String? = null,
    val updateAvailable: Boolean = false,
    val errorMessage: String? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
