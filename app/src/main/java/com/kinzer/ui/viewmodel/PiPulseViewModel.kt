package com.kinzer.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kinzer.data.ALL_DEVICES_ID
import com.kinzer.data.AuditActionType
import com.kinzer.data.DeviceCredentialVault
import com.kinzer.data.DeviceHealthMetrics
import com.kinzer.data.DnsQueryLogEntry
import com.kinzer.data.DomainListRule
import com.kinzer.data.DomainListType
import com.kinzer.data.FleetTelemetryAggregator
import com.kinzer.data.LocalAppSettings
import com.kinzer.data.ManagementAuditLog
import com.kinzer.data.NodeTelemetrySummary
import com.kinzer.data.PiholeApiClient
import com.kinzer.data.PiholeApiVersion
import com.kinzer.data.PiholeConnectionResult
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.PiholeTelemetrySnapshot
import com.kinzer.data.QueryStatusCategory
import com.kinzer.data.UiState
import com.kinzer.data.local.LocalPiholeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LiveDeviceDataState(
    val isRefreshing: Boolean = false,
    val telemetry: PiholeTelemetrySnapshot? = null,
    val telemetryError: String? = null,
    val nodeSummaries: List<NodeTelemetrySummary> = emptyList(),
    val fleetReachableCount: Int = 0,
    val fleetBlockingActiveCount: Int = 0,
    val queryLogs: List<DnsQueryLogEntry> = emptyList(),
    val queryLogsLoading: Boolean = false,
    val queryLogsError: String? = null,
    val domainRules: List<DomainListRule> = emptyList(),
    val domainRulesLoading: Boolean = false,
    val domainRulesError: String? = null,
    val autoRefreshEnabled: Boolean = true,
    val blockingMutationInProgress: Boolean = false,
    val domainMutationInProgress: Boolean = false
)

class PiPulseViewModel(
    private val localRepository: LocalPiholeRepository,
    private val apiClient: PiholeApiClient,
    private val credentialVault: DeviceCredentialVault
) : ViewModel() {

    private var pollingJob: Job? = null
    private var healthPollingJob: Job? = null

    val settingsState: StateFlow<LocalAppSettings> =
        localRepository.observeSettings()
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing local settings", error)
                emit(LocalAppSettings(activeDeviceId = ALL_DEVICES_ID))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LocalAppSettings(activeDeviceId = ALL_DEVICES_ID)
            )

    val devicesState: StateFlow<UiState<List<PiholeDeviceProfile>>> =
        localRepository.observeDevices()
            .map<List<PiholeDeviceProfile>, UiState<List<PiholeDeviceProfile>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing local devices", error)
                emit(UiState.Error(error.message ?: "Failed to load saved Pi-hole devices"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val auditLogsState: StateFlow<UiState<List<ManagementAuditLog>>> =
        localRepository.observeAuditLogs()
            .map<List<ManagementAuditLog>, UiState<List<ManagementAuditLog>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Error observing local audit logs", error)
                emit(UiState.Error(error.message ?: "Failed to load activity log"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _selectedScopeOverrideId = MutableStateFlow<String?>(null)

    /**
     * True when 2+ Pi-hole nodes exist and the active scope is "All Devices" (the default).
     */
    val isAllDevicesMode: StateFlow<Boolean> =
        combine(devicesState, settingsState, _selectedScopeOverrideId) { devicesUi, settings, overrideId ->
            val devices = (devicesUi as? UiState.Success)?.data.orEmpty()
            if (devices.size < 2) return@combine false
            val preferredId = overrideId ?: settings.activeDeviceId
            preferredId.isBlank() ||
                preferredId == ALL_DEVICES_ID ||
                devices.none { it.id == preferredId }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false
        )

    /**
     * The currently focused single Pi-hole device, or null when no devices exist or
     * when "All Devices" fleet mode is active.
     */
    val activeDevice: StateFlow<PiholeDeviceProfile?> =
        combine(devicesState, settingsState, _selectedScopeOverrideId, isAllDevicesMode) { devicesUi, settings, overrideId, allMode ->
            val devices = (devicesUi as? UiState.Success)?.data.orEmpty()
            if (devices.isEmpty() || allMode) return@combine null
            val preferredId = overrideId ?: settings.activeDeviceId
            devices.firstOrNull { it.id == preferredId } ?: devices.first()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = null
        )

    private val _liveState = MutableStateFlow(LiveDeviceDataState())
    val liveState: StateFlow<LiveDeviceDataState> = _liveState.asStateFlow()

    private val _querySearch = MutableStateFlow("")
    val querySearch: StateFlow<String> = _querySearch.asStateFlow()

    private val _queryCategory = MutableStateFlow(QueryStatusCategory.ALL)
    val queryCategory: StateFlow<QueryStatusCategory> = _queryCategory.asStateFlow()

    private val _domainSearch = MutableStateFlow("")
    val domainSearch: StateFlow<String> = _domainSearch.asStateFlow()

    private val _selectedDomainTab = MutableStateFlow(DomainListType.ALLOW)
    val selectedDomainTab: StateFlow<DomainListType> = _selectedDomainTab.asStateFlow()

    private val _domainKindFilter = MutableStateFlow("ALL")
    val domainKindFilter: StateFlow<String> = _domainKindFilter.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _connectionTestLoading = MutableStateFlow(false)
    val connectionTestLoading: StateFlow<Boolean> = _connectionTestLoading.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<PiholeConnectionResult?>(null)
    val connectionTestResult: StateFlow<PiholeConnectionResult?> = _connectionTestResult.asStateFlow()

    private val _deviceHealthMap = MutableStateFlow<Map<String, DeviceHealthMetrics>>(emptyMap())
    val deviceHealthMap: StateFlow<Map<String, DeviceHealthMetrics>> = _deviceHealthMap.asStateFlow()

    private val _healthRefreshing = MutableStateFlow(false)
    val healthRefreshing: StateFlow<Boolean> = _healthRefreshing.asStateFlow()

    init {
        observeSelectionForPolling()
        observeDevicesForHealthPolling()
    }

    private fun currentDevices(): List<PiholeDeviceProfile> =
        (devicesState.value as? UiState.Success)?.data.orEmpty()

    private fun observeSelectionForPolling() {
        viewModelScope.launch {
            var lastPollingKey: String? = null
            combine(
                devicesState,
                activeDevice,
                isAllDevicesMode,
                settingsState
            ) { devicesUi, singleDevice, allMode, settings ->
                val devices = (devicesUi as? UiState.Success)?.data.orEmpty()
                val intervalSec = settings.refreshIntervalSeconds.coerceIn(2L, 60L)
                PollingTarget(
                    devices = devices,
                    singleDevice = singleDevice,
                    allDevicesMode = allMode,
                    intervalSeconds = intervalSec
                )
            }.collect { target ->
                val newKey = when {
                    target.devices.isEmpty() -> "EMPTY"
                    target.allDevicesMode -> "ALL:${target.intervalSeconds}:" +
                        target.devices.joinToString("|") {
                            "${it.id}:${it.host}:${it.port}:${it.useHttps}:${it.apiVersion}:${it.basePath}"
                        }
                    target.singleDevice != null -> {
                        val d = target.singleDevice
                        "SINGLE:${d.id}:${d.host}:${d.port}:${d.useHttps}:${d.apiVersion}:${d.basePath}:${target.intervalSeconds}"
                    }
                    else -> "EMPTY"
                }

                if (newKey == lastPollingKey) return@collect
                lastPollingKey = newKey

                pollingJob?.cancel()
                if (target.devices.isEmpty() || (!target.allDevicesMode && target.singleDevice == null)) {
                    _liveState.update {
                        it.copy(
                            telemetry = null,
                            telemetryError = null,
                            nodeSummaries = emptyList(),
                            fleetReachableCount = 0,
                            fleetBlockingActiveCount = 0,
                            queryLogs = emptyList(),
                            queryLogsError = null,
                            domainRules = emptyList(),
                            domainRulesError = null,
                            isRefreshing = false
                        )
                    }
                } else {
                    _liveState.update {
                        it.copy(
                            telemetry = null,
                            telemetryError = null,
                            nodeSummaries = emptyList(),
                            fleetReachableCount = 0,
                            fleetBlockingActiveCount = 0,
                            queryLogs = emptyList(),
                            queryLogsError = null,
                            domainRules = emptyList(),
                            domainRulesError = null
                        )
                    }
                    if (target.allDevicesMode) {
                        startFleetPollingLoop(target.devices, target.intervalSeconds)
                    } else {
                        startSingleDevicePollingLoop(target.singleDevice!!, target.intervalSeconds)
                    }
                }
            }
        }
    }

    private fun startSingleDevicePollingLoop(device: PiholeDeviceProfile, intervalSeconds: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            refreshAllForDevice(device, includeDomains = true, recordLastConnected = true)
            while (isActive) {
                delay(intervalSeconds * 1000L)
                if (_liveState.value.autoRefreshEnabled) {
                    refreshAllForDevice(device, includeDomains = false, recordLastConnected = false)
                }
            }
        }
    }

    private fun startFleetPollingLoop(devices: List<PiholeDeviceProfile>, intervalSeconds: Long) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            refreshAllForFleet(devices, includeDomains = true, recordLastConnected = true)
            while (isActive) {
                delay(intervalSeconds * 1000L)
                if (_liveState.value.autoRefreshEnabled) {
                    refreshAllForFleet(devices, includeDomains = false, recordLastConnected = false)
                }
            }
        }
    }

    private fun observeDevicesForHealthPolling() {
        viewModelScope.launch {
            var lastDevicesKey: String? = null
            devicesState.collect { devicesUi ->
                val devices = (devicesUi as? UiState.Success)?.data.orEmpty()
                val newKey = devices.joinToString("|") {
                    "${it.id}:${it.host}:${it.port}:${it.useHttps}:${it.apiVersion}:${it.basePath}"
                }
                if (newKey == lastDevicesKey) return@collect
                lastDevicesKey = newKey

                healthPollingJob?.cancel()
                if (devices.isEmpty()) {
                    _deviceHealthMap.value = emptyMap()
                    _healthRefreshing.value = false
                } else {
                    healthPollingJob = viewModelScope.launch {
                        fetchHealthForDevices(devices)
                        while (isActive) {
                            delay(HEALTH_POLL_INTERVAL_MILLIS)
                            if (_liveState.value.autoRefreshEnabled) {
                                fetchHealthForDevices(devices)
                            }
                        }
                    }
                }
            }
        }
    }

    fun refreshDeviceHealth() {
        val devices = currentDevices()
        if (devices.isEmpty()) return
        viewModelScope.launch {
            fetchHealthForDevices(devices)
        }
    }

    private suspend fun fetchHealthForDevices(devices: List<PiholeDeviceProfile>) {
        _healthRefreshing.value = true
        val results = coroutineScope {
            devices.map { device ->
                async {
                    val secret = credentialVault.getSecret(device.id)
                    device.id to apiClient.fetchDeviceHealth(device, secret)
                }
            }.awaitAll()
        }
        _deviceHealthMap.value = results.toMap()
        _healthRefreshing.value = false
    }

    fun refreshNow(includeDomains: Boolean = true) {
        val devices = currentDevices()
        if (devices.isEmpty()) return
        viewModelScope.launch {
            if (isAllDevicesMode.value) {
                refreshAllForFleet(devices, includeDomains = includeDomains, recordLastConnected = true)
            } else {
                val device = activeDevice.value ?: return@launch
                refreshAllForDevice(device, includeDomains = includeDomains, recordLastConnected = true)
            }
        }
    }

    fun refreshDomainRulesOnly() {
        val devices = currentDevices()
        if (devices.isEmpty()) return
        viewModelScope.launch {
            _liveState.update { it.copy(domainRulesLoading = true, domainRulesError = null) }
            if (isAllDevicesMode.value) {
                val results = coroutineScope {
                    devices.map { device ->
                        async {
                            val secret = credentialVault.getSecret(device.id)
                            device to apiClient.fetchDomainRules(device, secret)
                        }
                    }.awaitAll()
                }
                val (mergedRules, errorMsg) = FleetTelemetryAggregator.mergeDomainRules(results)
                _liveState.update {
                    it.copy(
                        domainRules = mergedRules,
                        domainRulesLoading = false,
                        domainRulesError = errorMsg
                    )
                }
            } else {
                val device = activeDevice.value ?: return@launch
                val secret = credentialVault.getSecret(device.id)
                apiClient.fetchDomainRules(device, secret)
                    .onSuccess { rules ->
                        val attributed = rules.map { it.copy(deviceId = device.id, deviceName = device.name) }
                        _liveState.update {
                            it.copy(
                                domainRules = attributed,
                                domainRulesLoading = false,
                                domainRulesError = null
                            )
                        }
                    }
                    .onFailure { err ->
                        _liveState.update {
                            it.copy(
                                domainRulesLoading = false,
                                domainRulesError = err.message ?: "Failed to load whitelist and blacklist."
                            )
                        }
                    }
            }
        }
    }

    private suspend fun refreshAllForDevice(
        device: PiholeDeviceProfile,
        includeDomains: Boolean,
        recordLastConnected: Boolean
    ) {
        _liveState.update {
            it.copy(
                isRefreshing = true,
                queryLogsLoading = it.queryLogs.isEmpty(),
                domainRulesLoading = includeDomains && it.domainRules.isEmpty()
            )
        }
        val secret = credentialVault.getSecret(device.id)

        apiClient.fetchTelemetrySnapshot(device, secret)
            .onSuccess { snapshot ->
                _liveState.update {
                    it.copy(
                        telemetry = snapshot,
                        telemetryError = null,
                        nodeSummaries = listOf(
                            NodeTelemetrySummary(
                                device = device,
                                online = true,
                                blockingEnabled = snapshot.summary.blockingEnabled,
                                totalQueries = snapshot.summary.totalQueries,
                                percentBlocked = snapshot.summary.percentBlocked
                            )
                        ),
                        fleetReachableCount = 1,
                        fleetBlockingActiveCount = if (snapshot.summary.blockingEnabled) 1 else 0,
                        isRefreshing = false
                    )
                }
                if (recordLastConnected) {
                    localRepository.updateDeviceLastConnected(device.id, System.currentTimeMillis())
                }
            }
            .onFailure { err ->
                _liveState.update {
                    it.copy(
                        telemetryError = err.message ?: "Unable to connect to ${device.name}",
                        nodeSummaries = listOf(
                            NodeTelemetrySummary(
                                device = device,
                                online = false,
                                errorMessage = err.message ?: "Unreachable"
                            )
                        ),
                        fleetReachableCount = 0,
                        fleetBlockingActiveCount = 0,
                        isRefreshing = false
                    )
                }
            }

        apiClient.fetchQueryLogs(device, secret, limit = 150)
            .onSuccess { entries ->
                val attributed = entries.map { it.copy(deviceId = device.id, deviceName = device.name) }
                _liveState.update {
                    it.copy(
                        queryLogs = attributed,
                        queryLogsLoading = false,
                        queryLogsError = null
                    )
                }
            }
            .onFailure { err ->
                _liveState.update {
                    it.copy(
                        queryLogsLoading = false,
                        queryLogsError = err.message ?: "Unable to fetch query logs."
                    )
                }
            }

        if (includeDomains) {
            apiClient.fetchDomainRules(device, secret)
                .onSuccess { rules ->
                    val attributed = rules.map { it.copy(deviceId = device.id, deviceName = device.name) }
                    _liveState.update {
                        it.copy(
                            domainRules = attributed,
                            domainRulesLoading = false,
                            domainRulesError = null
                        )
                    }
                }
                .onFailure { err ->
                    _liveState.update {
                        it.copy(
                            domainRulesLoading = false,
                            domainRulesError = err.message ?: "Unable to fetch whitelist/blacklist rules."
                        )
                    }
                }
        }
    }

    private suspend fun refreshAllForFleet(
        devices: List<PiholeDeviceProfile>,
        includeDomains: Boolean,
        recordLastConnected: Boolean
    ) {
        _liveState.update {
            it.copy(
                isRefreshing = true,
                queryLogsLoading = it.queryLogs.isEmpty(),
                domainRulesLoading = includeDomains && it.domainRules.isEmpty()
            )
        }

        val secretsById = coroutineScope {
            devices.map { d -> async { d.id to credentialVault.getSecret(d.id) } }
                .awaitAll()
                .toMap()
        }

        val telemetryResults = coroutineScope {
            devices.map { device ->
                async {
                    val secret = secretsById[device.id].orEmpty()
                    device to apiClient.fetchTelemetrySnapshot(device, secret)
                }
            }.awaitAll()
        }

        val aggregated = FleetTelemetryAggregator.aggregateTelemetry(telemetryResults)
        _liveState.update {
            it.copy(
                telemetry = aggregated.snapshot ?: it.telemetry.takeIf { aggregated.reachableCount > 0 },
                telemetryError = aggregated.errorMessage,
                nodeSummaries = aggregated.nodeSummaries,
                fleetReachableCount = aggregated.reachableCount,
                fleetBlockingActiveCount = aggregated.blockingActiveCount,
                isRefreshing = false
            )
        }

        if (recordLastConnected) {
            val now = System.currentTimeMillis()
            telemetryResults.forEach { (device, result) ->
                if (result.isSuccess) {
                    localRepository.updateDeviceLastConnected(device.id, now)
                }
            }
        }

        val perNodeLogLimit = (200 / devices.size.coerceAtLeast(1)).coerceIn(60, 120)
        val logResults = coroutineScope {
            devices.map { device ->
                async {
                    val secret = secretsById[device.id].orEmpty()
                    device to apiClient.fetchQueryLogs(device, secret, limit = perNodeLogLimit)
                }
            }.awaitAll()
        }
        val (mergedLogs, logsError) = FleetTelemetryAggregator.mergeQueryLogs(logResults, limit = 200)
        _liveState.update {
            it.copy(
                queryLogs = mergedLogs,
                queryLogsLoading = false,
                queryLogsError = logsError
            )
        }

        if (includeDomains) {
            val ruleResults = coroutineScope {
                devices.map { device ->
                    async {
                        val secret = secretsById[device.id].orEmpty()
                        device to apiClient.fetchDomainRules(device, secret)
                    }
                }.awaitAll()
            }
            val (mergedRules, rulesError) = FleetTelemetryAggregator.mergeDomainRules(ruleResults)
            _liveState.update {
                it.copy(
                    domainRules = mergedRules,
                    domainRulesLoading = false,
                    domainRulesError = rulesError
                )
            }
        }
    }

    fun selectActiveDevice(deviceId: String) {
        _selectedScopeOverrideId.value = deviceId
        viewModelScope.launch {
            localRepository.updateActiveDevice(deviceId)
                .onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save active device selection."
                }
        }
    }

    fun selectAllDevices() {
        selectActiveDevice(ALL_DEVICES_ID)
    }

    fun toggleAutoRefresh() {
        _liveState.update { it.copy(autoRefreshEnabled = !it.autoRefreshEnabled) }
    }

    fun updateRefreshInterval(seconds: Long) {
        viewModelScope.launch {
            localRepository.updateRefreshInterval(seconds)
                .onSuccess {
                    _feedbackMessage.value = "Live polling interval set to ${seconds}s"
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Failed to update polling interval."
                }
        }
    }

    fun setBlocking(enabled: Boolean, timerSeconds: Int? = null) {
        val devices = currentDevices()
        if (devices.isEmpty()) return

        viewModelScope.launch {
            _liveState.update { it.copy(blockingMutationInProgress = true) }
            if (isAllDevicesMode.value) {
                val results = coroutineScope {
                    devices.map { device ->
                        async {
                            val secret = credentialVault.getSecret(device.id)
                            device to apiClient.setBlocking(device, secret, enabled, timerSeconds)
                        }
                    }.awaitAll()
                }
                val succeeded = results.count { it.second.isSuccess }
                if (succeeded > 0) {
                    val verb = if (enabled) {
                        "DNS blocking enabled"
                    } else if (timerSeconds != null && timerSeconds > 0) {
                        "DNS blocking paused (${formatDurationLabel(timerSeconds)})"
                    } else {
                        "DNS blocking paused indefinitely"
                    }
                    _feedbackMessage.value = "$verb on $succeeded/${devices.size} Pi-holes"
                    localRepository.recordAuditLog(
                        deviceId = ALL_DEVICES_ID,
                        deviceName = "All Devices ($succeeded/${devices.size})",
                        actionType = if (enabled) AuditActionType.BLOCKING_ENABLED else AuditActionType.BLOCKING_DISABLED,
                        target = if (enabled) "Enabled" else if (timerSeconds != null) formatDurationLabel(timerSeconds) else "Indefinite"
                    )
                    refreshAllForFleet(devices, includeDomains = false, recordLastConnected = false)
                } else {
                    val errMsg = results.firstNotNullOfOrNull { it.second.exceptionOrNull()?.message }
                    _feedbackMessage.value = errMsg ?: "Failed to update blocking on any Pi-hole."
                }
            } else {
                val device = activeDevice.value ?: run {
                    _liveState.update { it.copy(blockingMutationInProgress = false) }
                    return@launch
                }
                val secret = credentialVault.getSecret(device.id)
                apiClient.setBlocking(device, secret, enabled, timerSeconds)
                    .onSuccess {
                        val label = if (enabled) {
                            "DNS blocking enabled on ${device.name}"
                        } else if (timerSeconds != null && timerSeconds > 0) {
                            "DNS blocking paused for ${formatDurationLabel(timerSeconds)}"
                        } else {
                            "DNS blocking paused indefinitely"
                        }
                        _feedbackMessage.value = label
                        localRepository.recordAuditLog(
                            deviceId = device.id,
                            deviceName = device.name,
                            actionType = if (enabled) AuditActionType.BLOCKING_ENABLED else AuditActionType.BLOCKING_DISABLED,
                            target = if (enabled) "Enabled" else if (timerSeconds != null) formatDurationLabel(timerSeconds) else "Indefinite"
                        )
                        refreshAllForDevice(device, includeDomains = false, recordLastConnected = false)
                    }
                    .onFailure { err ->
                        _feedbackMessage.value = err.message ?: "Failed to update blocking state."
                    }
            }
            _liveState.update { it.copy(blockingMutationInProgress = false) }
        }
    }

    fun addDomainToList(
        domain: String,
        listType: DomainListType,
        regexRule: Boolean,
        comment: String,
        applyToAllDevices: Boolean = isAllDevicesMode.value,
        onComplete: () -> Unit = {}
    ) {
        val devices = currentDevices()
        if (devices.isEmpty()) {
            _feedbackMessage.value = "Select or add a Pi-hole device first."
            return
        }
        val cleanDomain = domain.trim()
        if (cleanDomain.isEmpty()) {
            _feedbackMessage.value = "Enter a valid domain or regex pattern."
            return
        }

        val targetDevices = if (applyToAllDevices && devices.size > 1) {
            devices
        } else {
            listOfNotNull(activeDevice.value ?: devices.firstOrNull())
        }
        if (targetDevices.isEmpty()) return

        viewModelScope.launch {
            _liveState.update { it.copy(domainMutationInProgress = true) }
            val actionWord = if (listType == DomainListType.ALLOW) "whitelisted" else "blacklisted"

            if (targetDevices.size > 1) {
                val results = coroutineScope {
                    targetDevices.map { device ->
                        async {
                            val secret = credentialVault.getSecret(device.id)
                            device to apiClient.addDomainRule(device, secret, cleanDomain, listType, regexRule, comment)
                        }
                    }.awaitAll()
                }
                val succeeded = results.count { it.second.isSuccess }
                if (succeeded > 0) {
                    _feedbackMessage.value = "$cleanDomain $actionWord on $succeeded/${targetDevices.size} Pi-holes"
                    localRepository.recordAuditLog(
                        deviceId = ALL_DEVICES_ID,
                        deviceName = "All Devices ($succeeded/${targetDevices.size})",
                        actionType = if (listType == DomainListType.ALLOW) AuditActionType.WHITELIST_ADD else AuditActionType.BLACKLIST_ADD,
                        target = cleanDomain
                    )
                    refreshDomainRulesOnly()
                    onComplete()
                } else {
                    val errMsg = results.firstNotNullOfOrNull { it.second.exceptionOrNull()?.message }
                    _feedbackMessage.value = errMsg ?: "Failed to add domain rule."
                }
            } else {
                val device = targetDevices.first()
                val secret = credentialVault.getSecret(device.id)
                apiClient.addDomainRule(device, secret, cleanDomain, listType, regexRule, comment)
                    .onSuccess {
                        _feedbackMessage.value = "$cleanDomain $actionWord on ${device.name}"
                        localRepository.recordAuditLog(
                            deviceId = device.id,
                            deviceName = device.name,
                            actionType = if (listType == DomainListType.ALLOW) AuditActionType.WHITELIST_ADD else AuditActionType.BLACKLIST_ADD,
                            target = cleanDomain
                        )
                        refreshDomainRulesOnly()
                        onComplete()
                    }
                    .onFailure { err ->
                        _feedbackMessage.value = err.message ?: "Failed to add domain rule."
                    }
            }
            _liveState.update { it.copy(domainMutationInProgress = false) }
        }
    }

    fun removeDomainFromList(rule: DomainListRule) {
        val devices = currentDevices()
        val device = if (rule.deviceId.isNotBlank()) {
            devices.firstOrNull { it.id == rule.deviceId }
        } else {
            activeDevice.value
        } ?: return

        viewModelScope.launch {
            _liveState.update { it.copy(domainMutationInProgress = true) }
            val secret = credentialVault.getSecret(device.id)
            apiClient.removeDomainRule(device, secret, rule)
                .onSuccess {
                    _feedbackMessage.value = "Removed ${rule.domain} from ${device.name} ${rule.listType.label}"
                    localRepository.recordAuditLog(
                        deviceId = device.id,
                        deviceName = device.name,
                        actionType = if (rule.listType == DomainListType.ALLOW) AuditActionType.WHITELIST_REMOVE else AuditActionType.BLACKLIST_REMOVE,
                        target = rule.domain
                    )
                    refreshDomainRulesOnly()
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Failed to remove domain rule."
                }
            _liveState.update { it.copy(domainMutationInProgress = false) }
        }
    }

    fun triggerGravityUpdate() {
        val devices = currentDevices()
        if (devices.isEmpty()) return

        viewModelScope.launch {
            if (isAllDevicesMode.value) {
                val v6Devices = devices.filter { it.parsedApiVersion == PiholeApiVersion.V6 }
                if (v6Devices.isEmpty()) {
                    _feedbackMessage.value = "Gravity trigger requires at least one Pi-hole v6 device."
                    return@launch
                }
                val results = coroutineScope {
                    v6Devices.map { device ->
                        async {
                            val secret = credentialVault.getSecret(device.id)
                            device to apiClient.triggerGravityUpdate(device, secret)
                        }
                    }.awaitAll()
                }
                val succeeded = results.count { it.second.isSuccess }
                if (succeeded > 0) {
                    _feedbackMessage.value = "Gravity update started on $succeeded/${v6Devices.size} Pi-hole v6 nodes"
                    localRepository.recordAuditLog(
                        deviceId = ALL_DEVICES_ID,
                        deviceName = "All Devices ($succeeded/${v6Devices.size})",
                        actionType = AuditActionType.GRAVITY_UPDATE,
                        target = "Fleet Gravity refresh"
                    )
                } else {
                    val errMsg = results.firstNotNullOfOrNull { it.second.exceptionOrNull()?.message }
                    _feedbackMessage.value = errMsg ?: "Gravity update failed."
                }
            } else {
                val device = activeDevice.value ?: return@launch
                val secret = credentialVault.getSecret(device.id)
                apiClient.triggerGravityUpdate(device, secret)
                    .onSuccess {
                        _feedbackMessage.value = "Gravity adlist update started on ${device.name}"
                        localRepository.recordAuditLog(
                            deviceId = device.id,
                            deviceName = device.name,
                            actionType = AuditActionType.GRAVITY_UPDATE,
                            target = "Manual Gravity refresh"
                        )
                    }
                    .onFailure { err ->
                        _feedbackMessage.value = err.message ?: "Gravity update failed."
                    }
            }
        }
    }

    fun testDeviceConnection(
        editingDeviceId: String?,
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        enteredSecret: String
    ) {
        viewModelScope.launch {
            _connectionTestLoading.value = true
            _connectionTestResult.value = null
            val effectiveSecret = if (enteredSecret.isNotBlank()) {
                enteredSecret
            } else if (!editingDeviceId.isNullOrBlank()) {
                credentialVault.getSecret(editingDeviceId)
            } else {
                ""
            }
            val probeProfile = PiholeDeviceProfile(
                id = editingDeviceId.orEmpty(),
                name = name.ifBlank { "Probe" },
                host = host.trim(),
                port = port.coerceIn(1L, 65535L),
                useHttps = useHttps,
                apiVersion = apiVersion.wireValue,
                basePath = basePath.trim()
            )
            _connectionTestResult.value = apiClient.testConnection(probeProfile, effectiveSecret)
            _connectionTestLoading.value = false
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    fun saveDevice(
        editingDevice: PiholeDeviceProfile?,
        name: String,
        host: String,
        port: Long,
        useHttps: Boolean,
        apiVersion: PiholeApiVersion,
        basePath: String,
        locationTag: String,
        notes: String,
        secretInput: String,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            if (editingDevice == null) {
                val hasSecret = secretInput.isNotBlank()
                localRepository.addDevice(
                    name = name,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    apiVersion = apiVersion,
                    basePath = basePath,
                    locationTag = locationTag,
                    notes = notes,
                    hasStoredCredential = hasSecret
                ).onSuccess { newId ->
                    if (hasSecret) {
                        credentialVault.saveSecret(newId, secretInput)
                    }
                    // Keep All Devices as default whenever 2+ nodes are configured
                    selectAllDevices()
                    localRepository.recordAuditLog(
                        deviceId = newId,
                        deviceName = name.trim().take(60),
                        actionType = AuditActionType.DEVICE_ADDED,
                        target = host.trim().take(255)
                    )
                    _feedbackMessage.value = "Saved Pi-hole device \"${name.trim()}\""
                    onSaved()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Failed to save device."
                }
            } else {
                val hasSecret = secretInput.isNotBlank() || editingDevice.hasStoredCredential
                localRepository.updateDevice(
                    deviceId = editingDevice.id,
                    name = name,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    apiVersion = apiVersion,
                    basePath = basePath,
                    locationTag = locationTag,
                    notes = notes,
                    hasStoredCredential = hasSecret
                ).onSuccess {
                    if (secretInput.isNotBlank()) {
                        credentialVault.saveSecret(editingDevice.id, secretInput)
                    }
                    apiClient.clearSession(editingDevice.id)
                    localRepository.recordAuditLog(
                        deviceId = editingDevice.id,
                        deviceName = name.trim().take(60),
                        actionType = AuditActionType.DEVICE_UPDATED,
                        target = host.trim().take(255)
                    )
                    _feedbackMessage.value = "Updated \"${name.trim()}\""
                    onSaved()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Failed to update device."
                }
            }
        }
    }

    fun deleteDevice(device: PiholeDeviceProfile) {
        viewModelScope.launch {
            localRepository.deleteDevice(device.id)
                .onSuccess {
                    credentialVault.removeSecret(device.id)
                    apiClient.clearSession(device.id)
                    localRepository.recordAuditLog(
                        deviceId = device.id,
                        deviceName = device.name,
                        actionType = AuditActionType.DEVICE_REMOVED,
                        target = device.host
                    )
                    _feedbackMessage.value = "Removed \"${device.name}\""
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Failed to remove device."
                }
        }
    }

    fun clearAuditLogs() {
        viewModelScope.launch {
            localRepository.clearAuditLogs()
                .onSuccess { _feedbackMessage.value = "Cleared local activity log" }
                .onFailure { err -> _feedbackMessage.value = err.message ?: "Could not clear activity log." }
        }
    }

    fun updateQuerySearch(query: String) {
        _querySearch.value = query
    }

    fun updateQueryCategory(category: QueryStatusCategory) {
        _queryCategory.value = category
    }

    fun updateDomainSearch(query: String) {
        _domainSearch.value = query
    }

    fun selectDomainTab(type: DomainListType) {
        _selectedDomainTab.value = type
    }

    fun updateDomainKindFilter(kind: String) {
        _domainKindFilter.value = kind
    }

    fun consumeFeedback() {
        _feedbackMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        healthPollingJob?.cancel()
    }

    private fun formatDurationLabel(seconds: Int): String = when {
        seconds < 60 -> "${seconds}s"
        seconds % 3600 == 0 -> "${seconds / 3600}h"
        else -> "${seconds / 60}m"
    }

    private data class PollingTarget(
        val devices: List<PiholeDeviceProfile>,
        val singleDevice: PiholeDeviceProfile?,
        val allDevicesMode: Boolean,
        val intervalSeconds: Long
    )

    private companion object {
        const val TAG = "PiPulseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val HEALTH_POLL_INTERVAL_MILLIS = 15_000L
    }
}
