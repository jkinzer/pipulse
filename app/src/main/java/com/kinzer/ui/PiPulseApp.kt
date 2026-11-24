package com.kinzer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kinzer.data.DeviceCredentialVault
import com.kinzer.data.PiholeApiClient
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.SshFleetUpdater
import com.kinzer.data.UiState
import com.kinzer.data.local.LocalPiholeRepository
import com.kinzer.ui.components.AddDomainDialog
import com.kinzer.ui.components.DeviceEditorDialog
import com.kinzer.ui.components.RollingUpdateDialog
import com.kinzer.ui.components.SshConfigDialog
import com.kinzer.ui.screens.DashboardScreen
import com.kinzer.ui.screens.DevicesSettingsScreen
import com.kinzer.ui.screens.DomainListsScreen
import com.kinzer.ui.screens.QueryLogsScreen
import com.kinzer.ui.screens.StatsScreen
import com.kinzer.ui.viewmodel.PiPulseViewModel

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_LOGS = "logs"
const val ROUTE_LISTS = "lists"
const val ROUTE_STATS = "stats"
const val ROUTE_DEVICES = "devices"

private enum class PiPulseTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD(ROUTE_DASHBOARD, "Dashboard", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    LOGS(ROUTE_LOGS, "Logs", Icons.Rounded.Terminal, Icons.Outlined.Terminal),
    LISTS(ROUTE_LISTS, "Lists", Icons.Rounded.FormatListBulleted, Icons.Outlined.FormatListBulleted),
    STATS(ROUTE_STATS, "Stats", Icons.Rounded.BarChart, Icons.Outlined.BarChart),
    DEVICES(ROUTE_DEVICES, "Devices", Icons.Rounded.Router, Icons.Outlined.Router)
}

@Composable
fun PiPulseApp() {
    val viewModel: PiPulseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val localRepository = LocalPiholeRepository(app)
                val apiClient = PiholeApiClient()
                PiPulseViewModel(
                    localRepository = localRepository,
                    apiClient = apiClient,
                    credentialVault = DeviceCredentialVault(app),
                    sshUpdater = SshFleetUpdater(localRepository, apiClient)
                )
            }
        }
    )

    val devicesUi by viewModel.devicesState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val auditLogsUi by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val isAllDevicesMode by viewModel.isAllDevicesMode.collectAsStateWithLifecycle()
    val activeDevice by viewModel.activeDevice.collectAsStateWithLifecycle()
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()

    val querySearch by viewModel.querySearch.collectAsStateWithLifecycle()
    val queryCategory by viewModel.queryCategory.collectAsStateWithLifecycle()
    val domainSearch by viewModel.domainSearch.collectAsStateWithLifecycle()
    val selectedDomainTab by viewModel.selectedDomainTab.collectAsStateWithLifecycle()
    val domainKindFilter by viewModel.domainKindFilter.collectAsStateWithLifecycle()

    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val testLoading by viewModel.connectionTestLoading.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTestResult.collectAsStateWithLifecycle()
    val deviceHealthMap by viewModel.deviceHealthMap.collectAsStateWithLifecycle()
    val healthRefreshing by viewModel.healthRefreshing.collectAsStateWithLifecycle()
    val sshConfig by viewModel.sshConfigState.collectAsStateWithLifecycle()
    val sshTestLoading by viewModel.sshTestLoading.collectAsStateWithLifecycle()
    val sshTestResult by viewModel.sshTestResult.collectAsStateWithLifecycle()
    val rollingUpdateState by viewModel.rollingUpdateState.collectAsStateWithLifecycle()

    val devices = (devicesUi as? UiState.Success)?.data.orEmpty()
    val auditLogs = (auditLogsUi as? UiState.Success)?.data.orEmpty()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeFeedback()
    }

    var showDeviceEditor by remember { mutableStateOf(false) }
    var editingDevice by remember { mutableStateOf<PiholeDeviceProfile?>(null) }
    var showAddDomainDialog by remember { mutableStateOf(false) }
    var showSshConfigDialog by remember { mutableStateOf(false) }
    var showRollingUpdateDialog by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        PiPulseTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        PiPulseTab.entries.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    val openAddDeviceDialog: () -> Unit = {
                        editingDevice = null
                        viewModel.clearConnectionTestResult()
                        showDeviceEditor = true
                    }
                    val isDeviceOnline = if (isAllDevicesMode) {
                        liveState.fleetReachableCount > 0
                    } else {
                        liveState.telemetry != null && liveState.telemetryError == null
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DASHBOARD
                    ) {
                        composable(ROUTE_DASHBOARD) {
                            DashboardScreen(
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesMode = isAllDevicesMode,
                                liveState = liveState,
                                onSelectAllDevices = viewModel::selectAllDevices,
                                onSelectDevice = viewModel::selectActiveDevice,
                                onAddDeviceClick = openAddDeviceDialog,
                                onRefreshClick = { viewModel.refreshNow(includeDomains = true) },
                                onSetBlocking = viewModel::setBlocking,
                                onTriggerGravity = viewModel::triggerGravityUpdate,
                                onNavigateToLogs = { navigateToTab(ROUTE_LOGS) },
                                onNavigateToLists = { navigateToTab(ROUTE_LISTS) }
                            )
                        }

                        composable(ROUTE_LOGS) {
                            QueryLogsScreen(
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesMode = isAllDevicesMode,
                                liveState = liveState,
                                searchQuery = querySearch,
                                selectedCategory = queryCategory,
                                onSelectAllDevices = viewModel::selectAllDevices,
                                onSelectDevice = viewModel::selectActiveDevice,
                                onAddDeviceClick = openAddDeviceDialog,
                                onSearchChange = viewModel::updateQuerySearch,
                                onCategoryChange = viewModel::updateQueryCategory,
                                onToggleAutoRefresh = viewModel::toggleAutoRefresh,
                                onRefreshClick = { viewModel.refreshNow(includeDomains = false) },
                                onQuickAddDomain = { domain, listType ->
                                    viewModel.addDomainToList(
                                        domain = domain,
                                        listType = listType,
                                        regexRule = false,
                                        comment = "Added from live query log",
                                        applyToAllDevices = isAllDevicesMode
                                    )
                                }
                            )
                        }

                        composable(ROUTE_LISTS) {
                            DomainListsScreen(
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesMode = isAllDevicesMode,
                                liveState = liveState,
                                selectedTab = selectedDomainTab,
                                searchQuery = domainSearch,
                                kindFilter = domainKindFilter,
                                onSelectAllDevices = viewModel::selectAllDevices,
                                onSelectDevice = viewModel::selectActiveDevice,
                                onAddDeviceClick = openAddDeviceDialog,
                                onSelectTab = viewModel::selectDomainTab,
                                onSearchChange = viewModel::updateDomainSearch,
                                onKindFilterChange = viewModel::updateDomainKindFilter,
                                onRefreshClick = viewModel::refreshDomainRulesOnly,
                                onOpenAddDialog = { showAddDomainDialog = true },
                                onRemoveRule = viewModel::removeDomainFromList
                            )
                        }

                        composable(ROUTE_STATS) {
                            StatsScreen(
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesMode = isAllDevicesMode,
                                liveState = liveState,
                                onSelectAllDevices = viewModel::selectAllDevices,
                                onSelectDevice = viewModel::selectActiveDevice,
                                onAddDeviceClick = openAddDeviceDialog,
                                onRefreshClick = { viewModel.refreshNow(includeDomains = false) },
                                onQuickAddDomain = { domain, listType ->
                                    viewModel.addDomainToList(
                                        domain = domain,
                                        listType = listType,
                                        regexRule = false,
                                        comment = "Added from traffic analytics",
                                        applyToAllDevices = isAllDevicesMode
                                    )
                                }
                            )
                        }

                        composable(ROUTE_DEVICES) {
                            DevicesSettingsScreen(
                                refreshIntervalSeconds = settings.refreshIntervalSeconds,
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesSelected = isAllDevicesMode,
                                isOnline = isDeviceOnline,
                                deviceHealthMap = deviceHealthMap,
                                healthRefreshing = healthRefreshing,
                                sshConfig = sshConfig,
                                rollingUpdateState = rollingUpdateState,
                                auditLogs = auditLogs,
                                onSelectAllDevices = viewModel::selectAllDevices,
                                onSelectDevice = viewModel::selectActiveDevice,
                                onRefreshHealth = viewModel::refreshDeviceHealth,
                                onOpenSshConfig = {
                                    viewModel.clearSshTestResult()
                                    showSshConfigDialog = true
                                },
                                onStartRollingUpdate = {
                                    showRollingUpdateDialog = true
                                    viewModel.startFleetRollingUpdate()
                                },
                                onViewRollingUpdate = {
                                    showRollingUpdateDialog = true
                                },
                                onAddDeviceClick = openAddDeviceDialog,
                                onEditDeviceClick = { device ->
                                    editingDevice = device
                                    viewModel.clearConnectionTestResult()
                                    showDeviceEditor = true
                                },
                                onDeleteDevice = viewModel::deleteDevice,
                                onUpdateRefreshInterval = viewModel::updateRefreshInterval,
                                onClearAuditLogs = viewModel::clearAuditLogs
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeviceEditor) {
        DeviceEditorDialog(
            editingDevice = editingDevice,
            testLoading = testLoading,
            testResult = testResult,
            onDismiss = {
                showDeviceEditor = false
                editingDevice = null
                viewModel.clearConnectionTestResult()
            },
            onTestConnection = viewModel::testDeviceConnection,
            onSave = { name, host, port, useHttps, apiVersion, basePath, locationTag, notes, secret ->
                viewModel.saveDevice(
                    editingDevice = editingDevice,
                    name = name,
                    host = host,
                    port = port,
                    useHttps = useHttps,
                    apiVersion = apiVersion,
                    basePath = basePath,
                    locationTag = locationTag,
                    notes = notes,
                    secretInput = secret,
                    onSaved = {
                        showDeviceEditor = false
                        editingDevice = null
                        viewModel.clearConnectionTestResult()
                    }
                )
            }
        )
    }

    if (showAddDomainDialog) {
        AddDomainDialog(
            initialListType = selectedDomainTab,
            deviceCount = devices.size,
            activeDeviceName = activeDevice?.name,
            defaultApplyToAll = isAllDevicesMode,
            isSubmitting = liveState.domainMutationInProgress,
            onDismiss = { showAddDomainDialog = false },
            onSubmit = { domain, listType, regexRule, comment, applyToAll ->
                viewModel.addDomainToList(
                    domain = domain,
                    listType = listType,
                    regexRule = regexRule,
                    comment = comment,
                    applyToAllDevices = applyToAll,
                    onComplete = { showAddDomainDialog = false }
                )
            }
        )
    }

    if (showSshConfigDialog) {
        SshConfigDialog(
            initialConfig = sshConfig,
            sampleTestDevice = activeDevice ?: devices.firstOrNull(),
            testLoading = sshTestLoading,
            testResult = sshTestResult,
            onValidateKey = viewModel::validateSshKey,
            onTestSsh = viewModel::testSshConnection,
            onClearTrustedHosts = viewModel::clearTrustedSshHostKeys,
            onRemoveStoredKey = viewModel::removeStoredSshKey,
            onDismiss = {
                showSshConfigDialog = false
                viewModel.clearSshTestResult()
            },
            onSave = { user, port, script, newKey, fileName, pass, timeout ->
                viewModel.saveSshConfig(
                    username = user,
                    port = port,
                    scriptPath = script,
                    newKeyPem = newKey,
                    keyFileName = fileName,
                    passphraseInput = pass,
                    rebootTimeoutSeconds = timeout,
                    onSaved = {
                        showSshConfigDialog = false
                        viewModel.clearSshTestResult()
                    }
                )
            }
        )
    }

    if (showRollingUpdateDialog) {
        RollingUpdateDialog(
            state = rollingUpdateState,
            onCancelRun = viewModel::cancelFleetRollingUpdate,
            onDismiss = { showRollingUpdateDialog = false }
        )
    }
}
