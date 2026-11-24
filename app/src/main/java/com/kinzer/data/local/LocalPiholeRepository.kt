package com.kinzer.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kinzer.data.ALL_DEVICES_ID
import com.kinzer.data.AuditActionType
import com.kinzer.data.LocalAppSettings
import com.kinzer.data.ManagementAuditLog
import com.kinzer.data.PiholeApiVersion
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.SshFleetConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.localSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pipulse_local_settings"
)

class LocalPiholeRepository(
    private val appContext: Context,
    private val deviceDao: PiholeDeviceDao,
    private val auditLogDao: ManagementAuditLogDao
) {
    constructor(context: Context) : this(
        appContext = context.applicationContext,
        deviceDao = PiPulseDatabase.getInstance(context).deviceDao(),
        auditLogDao = PiPulseDatabase.getInstance(context).auditLogDao()
    )

    private val activeDeviceKey = stringPreferencesKey("selected_scope_id")
    private val refreshIntervalKey = longPreferencesKey("refresh_interval_seconds")
    private val sshUsernameKey = stringPreferencesKey("ssh_username")
    private val sshPortKey = intPreferencesKey("ssh_port")
    private val sshScriptPathKey = stringPreferencesKey("ssh_script_path")
    private val sshHasKeyKey = booleanPreferencesKey("ssh_has_private_key")
    private val sshKeyFileNameKey = stringPreferencesKey("ssh_key_file_name")
    private val sshHasPassphraseKey = booleanPreferencesKey("ssh_has_passphrase")
    private val sshRebootTimeoutKey = intPreferencesKey("ssh_reboot_timeout_seconds")
    private val sshKnownHostsPrefix = "ssh_host_fp_"

    fun observeSettings(): Flow<LocalAppSettings> =
        appContext.localSettingsDataStore.data.map { prefs ->
            LocalAppSettings(
                activeDeviceId = prefs[activeDeviceKey] ?: ALL_DEVICES_ID,
                refreshIntervalSeconds = (prefs[refreshIntervalKey] ?: 5L).coerceIn(2L, 60L)
            )
        }

    fun observeSshConfig(): Flow<SshFleetConfig> =
        appContext.localSettingsDataStore.data.map { prefs ->
            SshFleetConfig(
                username = prefs[sshUsernameKey]?.takeIf { it.isNotBlank() } ?: "jeff",
                port = (prefs[sshPortKey] ?: 22).coerceIn(1, 65535),
                scriptPath = prefs[sshScriptPathKey].orEmpty(),
                hasPrivateKey = prefs[sshHasKeyKey] ?: false,
                keyFileName = prefs[sshKeyFileNameKey].orEmpty(),
                hasPassphrase = prefs[sshHasPassphraseKey] ?: false,
                rebootTimeoutSeconds = (prefs[sshRebootTimeoutKey] ?: 240).coerceIn(60, 600)
            )
        }

    suspend fun saveSshConfig(config: SshFleetConfig): Result<Unit> = try {
        appContext.localSettingsDataStore.edit { prefs ->
            prefs[sshUsernameKey] = config.username.trim().ifBlank { "jeff" }.take(64)
            prefs[sshPortKey] = config.port.coerceIn(1, 65535)
            prefs[sshScriptPathKey] = config.scriptPath.trim().take(255)
            prefs[sshHasKeyKey] = config.hasPrivateKey
            prefs[sshKeyFileNameKey] = config.keyFileName.trim().take(100)
            prefs[sshHasPassphraseKey] = config.hasPassphrase
            prefs[sshRebootTimeoutKey] = config.rebootTimeoutSeconds.coerceIn(60, 600)
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getTrustedHostKey(hostPortKey: String): String? =
        appContext.localSettingsDataStore.data.first()[stringPreferencesKey("$sshKnownHostsPrefix$hostPortKey")]

    suspend fun saveTrustedHostKey(hostPortKey: String, keyFingerprint: String) {
        appContext.localSettingsDataStore.edit { prefs ->
            prefs[stringPreferencesKey("$sshKnownHostsPrefix$hostPortKey")] = keyFingerprint
        }
    }

    suspend fun clearTrustedHostKeys(): Result<Unit> = try {
        appContext.localSettingsDataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(sshKnownHostsPrefix) }
            toRemove.forEach { prefs.remove(it) }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateActiveDevice(deviceId: String): Result<Unit> = try {
        appContext.localSettingsDataStore.edit { prefs ->
            prefs[activeDeviceKey] = deviceId.trim()
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateRefreshInterval(intervalSeconds: Long): Result<Unit> = try {
        val bounded = intervalSeconds.coerceIn(2L, 60L)
        appContext.localSettingsDataStore.edit { prefs ->
            prefs[refreshIntervalKey] = bounded
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun observeDevices(): Flow<List<PiholeDeviceProfile>> =
        deviceDao.observeAllDevices().map { list -> list.map { it.toDomain() } }

    suspend fun addDevice(
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        locationTag: String,
        notes: String,
        hasStoredCredential: Boolean
    ): Result<String> = try {
        val cleanName = name.trim().take(60)
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .take(255)
        require(cleanName.isNotEmpty()) { "Device name is required." }
        require(cleanHost.isNotEmpty()) { "Host or IP address is required." }
        val boundedPort = port.coerceIn(1L, 65535L)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()

        deviceDao.insertDevice(
            PiholeDeviceEntity(
                id = id,
                name = cleanName,
                host = cleanHost,
                port = boundedPort,
                useHttps = useHttps,
                apiVersion = apiVersion.wireValue,
                basePath = basePath.trim().take(64),
                locationTag = locationTag.trim().take(40),
                notes = notes.trim().take(240),
                hasStoredCredential = hasStoredCredential,
                lastConnectedAtMs = 0L,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
        Result.success(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateDevice(
        deviceId: String,
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        locationTag: String,
        notes: String,
        hasStoredCredential: Boolean
    ): Result<Unit> = try {
        require(deviceId.isNotBlank()) { "Device ID is required." }
        val existing = deviceDao.getDeviceById(deviceId)
            ?: throw IllegalStateException("Pi-hole device not found.")
        val cleanName = name.trim().take(60)
        val cleanHost = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .take(255)
        require(cleanName.isNotEmpty()) { "Device name is required." }
        require(cleanHost.isNotEmpty()) { "Host or IP address is required." }
        val boundedPort = port.coerceIn(1L, 65535L)

        deviceDao.updateDevice(
            existing.copy(
                name = cleanName,
                host = cleanHost,
                port = boundedPort,
                useHttps = useHttps,
                apiVersion = apiVersion.wireValue,
                basePath = basePath.trim().take(64),
                locationTag = locationTag.trim().take(40),
                notes = notes.trim().take(240),
                hasStoredCredential = hasStoredCredential,
                updatedAtMs = System.currentTimeMillis()
            )
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateDeviceLastConnected(deviceId: String, epochMs: Long): Result<Unit> = try {
        if (deviceId.isNotBlank()) {
            deviceDao.updateLastConnected(deviceId, epochMs, System.currentTimeMillis())
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> = try {
        require(deviceId.isNotBlank()) { "Device ID is required." }
        deviceDao.deleteDeviceById(deviceId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun observeAuditLogs(): Flow<List<ManagementAuditLog>> =
        auditLogDao.observeRecentLogs().map { list -> list.map { it.toDomain() } }

    suspend fun recordAuditLog(
        deviceId: String,
        deviceName: String,
        actionType: AuditActionType,
        target: String
    ): Result<Unit> = try {
        auditLogDao.insertLog(
            ManagementAuditLogEntity(
                id = UUID.randomUUID().toString(),
                deviceId = deviceId.trim().take(128).ifBlank { "local" },
                deviceName = deviceName.trim().take(60).ifBlank { "Pi-hole Device" },
                actionType = actionType.wireValue,
                target = target.trim().take(255),
                createdAtMs = System.currentTimeMillis()
            )
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun clearAuditLogs(): Result<Unit> = try {
        auditLogDao.clearAllLogs()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun PiholeDeviceEntity.toDomain() = PiholeDeviceProfile(
        id = id,
        name = name,
        host = host,
        port = port,
        useHttps = useHttps,
        apiVersion = apiVersion,
        basePath = basePath,
        locationTag = locationTag,
        notes = notes,
        hasStoredCredential = hasStoredCredential,
        lastConnectedAtMs = lastConnectedAtMs,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )

    private fun ManagementAuditLogEntity.toDomain() = ManagementAuditLog(
        id = id,
        deviceId = deviceId,
        deviceName = deviceName,
        actionType = actionType,
        target = target,
        createdAtMs = createdAtMs
    )
}
