package com.kinzer.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kinzer.R
import com.kinzer.data.NodeTelemetrySummary
import com.kinzer.data.PiholeApiVersion
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.PiholeSummaryStats
import com.kinzer.ui.components.CompactDeviceSwitcher
import com.kinzer.ui.components.QueriesTimelineChart
import com.kinzer.ui.components.formatCompactCount
import com.kinzer.ui.theme.CyanPulse
import com.kinzer.ui.theme.EmeraldSignal
import com.kinzer.ui.theme.JetBrainsMonoFontFamily
import com.kinzer.ui.theme.PiPulseThemeTokens
import com.kinzer.ui.viewmodel.LiveDeviceDataState
import java.util.Locale

@Composable
fun DashboardScreen(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    liveState: LiveDeviceDataState,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSetBlocking: (enabled: Boolean, timerSeconds: Int?) -> Unit,
    onTriggerGravity: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToLists: () -> Unit
) {
    val hasScope = isAllDevicesMode || activeDevice != null
    val isOnline = if (isAllDevicesMode) {
        liveState.fleetReachableCount > 0
    } else {
        liveState.telemetry != null && liveState.telemetryError == null
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DashboardTopBar(
                    devices = devices,
                    activeDevice = activeDevice,
                    isAllDevicesMode = isAllDevicesMode,
                    reachableCount = liveState.fleetReachableCount,
                    isOnline = isOnline,
                    isRefreshing = liveState.isRefreshing,
                    onSelectAllDevices = onSelectAllDevices,
                    onSelectDevice = onSelectDevice,
                    onAddDeviceClick = onAddDeviceClick,
                    onRefreshClick = onRefreshClick
                )
            }

            if (!hasScope) {
                item {
                    NoDeviceOnboardingCard(onAddDeviceClick = onAddDeviceClick)
                }
            } else {
                if (liveState.telemetryError != null) {
                    item {
                        UnreachableDeviceCard(
                            title = if (isAllDevicesMode) {
                                "Unable to reach Pi-hole fleet"
                            } else {
                                "Unable to reach ${activeDevice?.name ?: "Pi-hole"}"
                            },
                            endpointLabel = if (isAllDevicesMode) {
                                "0 of ${devices.size} nodes reachable"
                            } else {
                                activeDevice?.endpointDisplay.orEmpty()
                            },
                            errorMessage = liveState.telemetryError,
                            onRetry = onRefreshClick,
                            onAddAnother = onAddDeviceClick
                        )
                    }
                }

                val summary = liveState.telemetry?.summary
                if (summary != null) {
                    item {
                        ProtectionHeroCard(
                            device = activeDevice,
                            isAllDevicesMode = isAllDevicesMode,
                            reachableCount = liveState.fleetReachableCount,
                            totalNodeCount = devices.size,
                            blockingActiveCount = liveState.fleetBlockingActiveCount,
                            summary = summary,
                            isMutating = liveState.blockingMutationInProgress,
                            onSetBlocking = onSetBlocking
                        )
                    }

                    if (isAllDevicesMode && liveState.nodeSummaries.isNotEmpty()) {
                        item {
                            FleetNodeStatusStrip(
                                nodes = liveState.nodeSummaries,
                                onSelectDevice = onSelectDevice
                            )
                        }
                    }

                    item {
                        KpiBentoGrid(
                            summary = summary,
                            isAllDevicesMode = isAllDevicesMode
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isAllDevicesMode) "Combined Queries Over Time" else "Queries Over Time",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            QueriesTimelineChart(buckets = liveState.telemetry.history)
                        }
                    }

                    item {
                        val supportsGravity = if (isAllDevicesMode) {
                            devices.any { it.parsedApiVersion == PiholeApiVersion.V6 }
                        } else {
                            activeDevice?.parsedApiVersion == PiholeApiVersion.V6
                        }
                        QuickActionsRow(
                            supportsGravity = supportsGravity,
                            onNavigateToLogs = onNavigateToLogs,
                            onNavigateToLists = onNavigateToLists,
                            onTriggerGravity = onTriggerGravity
                        )
                    }
                } else if (liveState.telemetryError == null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = EmeraldSignal)
                                Text(
                                    text = if (isAllDevicesMode) {
                                        "Connecting to ${devices.size} Pi-hole nodes..."
                                    } else {
                                        "Connecting to ${activeDevice?.name} (${activeDevice?.host})..."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun DashboardTopBar(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    reachableCount: Int,
    isOnline: Boolean,
    isRefreshing: Boolean,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when {
                    isAllDevicesMode -> "$reachableCount/${devices.size} nodes online • Combined"
                    activeDevice != null -> "${activeDevice.host}:${activeDevice.port} • ${activeDevice.apiVersion}"
                    else -> "Local Pi-hole control"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactDeviceSwitcher(
                devices = devices,
                activeDevice = activeDevice,
                isAllDevicesSelected = isAllDevicesMode,
                isOnline = isOnline,
                onSelectAllDevices = onSelectAllDevices,
                onSelectDevice = onSelectDevice,
                onAddDeviceClick = onAddDeviceClick,
                tag = "device_switcher_pill"
            )

            IconButton(
                onClick = onRefreshClick,
                enabled = (isAllDevicesMode || activeDevice != null) && !isRefreshing,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .testTag("dashboard_refresh_button")
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = CyanPulse
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh telemetry",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ProtectionHeroCard(
    device: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    reachableCount: Int,
    totalNodeCount: Int,
    blockingActiveCount: Int,
    summary: PiholeSummaryStats,
    isMutating: Boolean,
    onSetBlocking: (enabled: Boolean, timerSeconds: Int?) -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val active = summary.blockingEnabled
    val anyActive = active || (isAllDevicesMode && blockingActiveCount > 0)
    val accent = if (active) extended.permitted else extended.warning
    val bgBrush = Brush.linearGradient(
        colors = if (active) {
            listOf(extended.permittedContainer.copy(alpha = 0.85f), MaterialTheme.colorScheme.surface)
        } else {
            listOf(extended.warningContainer.copy(alpha = 0.85f), MaterialTheme.colorScheme.surface)
        }
    )

    val titleText = when {
        !isAllDevicesMode -> if (active) "DNS Protection Active" else "Blocking Paused"
        active -> "All Nodes Protected"
        blockingActiveCount == 0 -> "All Nodes Paused"
        else -> "Partial Protection ($blockingActiveCount/$reachableCount)"
    }

    val subtitleText = when {
        isAllDevicesMode && active ->
            "$reachableCount of $totalNodeCount online • ${String.format(Locale.US, "%.1f%%", summary.percentBlocked)} queries filtered"
        isAllDevicesMode && summary.disableRemainingSeconds != null ->
            "$blockingActiveCount/$reachableCount active • Resumes in ${summary.disableRemainingSeconds}s"
        isAllDevicesMode ->
            "$blockingActiveCount of $reachableCount reachable nodes blocking"
        active ->
            "${device?.locationTag?.ifBlank { "Pi-hole" } ?: "Pi-hole"} • ${String.format(Locale.US, "%.1f%%", summary.percentBlocked)} queries filtered"
        summary.disableRemainingSeconds != null ->
            "Auto-resumes in ${summary.disableRemainingSeconds}s"
        else ->
            "Paused indefinitely on ${device?.name ?: "Pi-hole"}"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(bgBrush)
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = { onSetBlocking(!active, if (active) 300 else null) },
                enabled = !isMutating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF041A15)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("toggle_blocking_button")
            ) {
                if (isMutating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF041A15)
                    )
                } else {
                    Icon(
                        imageVector = if (active) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when {
                            isAllDevicesMode && active -> "Pause All"
                            isAllDevicesMode -> "Enable All"
                            active -> "Pause 5m"
                            else -> "Enable"
                        }
                    )
                }
            }
        }

        if (anyActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Pause 30s" to 30,
                    "Pause 5m" to 300,
                    "Pause 15m" to 900,
                    "Pause 1h" to 3600,
                    "Indefinite" to null
                ).forEach { (label, secs) ->
                    AssistChip(
                        onClick = { onSetBlocking(false, secs) },
                        enabled = !isMutating,
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.testTag("pause_chip_${secs ?: "indef"}")
                    )
                }
            }
        }
    }
}

@Composable
private fun FleetNodeStatusStrip(
    nodes: List<NodeTelemetrySummary>,
    onSelectDevice: (String) -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Pi-hole Fleet • Tap a node to inspect",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            nodes.forEach { node ->
                val dotColor = when {
                    !node.online -> extended.blocked
                    node.blockingEnabled -> extended.permitted
                    else -> extended.warning
                }
                val statusSub = when {
                    !node.online -> "Offline"
                    !node.blockingEnabled -> "${formatCompactCount(node.totalQueries)} • Paused"
                    else -> "${formatCompactCount(node.totalQueries)} • ${String.format(Locale.US, "%.1f%%", node.percentBlocked)}"
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, dotColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable { onSelectDevice(node.device.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("fleet_node_chip_${node.device.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Column {
                        Text(
                            text = node.device.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = statusSub,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (node.online) MaterialTheme.colorScheme.onSurfaceVariant else extended.blocked,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiBentoGrid(
    summary: PiholeSummaryStats,
    isAllDevicesMode: Boolean
) {
    val extended = PiPulseThemeTokens.extended
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Dns,
                accent = extended.cached,
                title = "Total Queries",
                value = formatCompactCount(summary.totalQueries),
                subtitle = "${formatCompactCount(summary.queriesCached)} cached"
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Block,
                accent = extended.blocked,
                title = "Blocked",
                value = formatCompactCount(summary.blockedQueries),
                subtitle = String.format(Locale.US, "%.1f%% blocked", summary.percentBlocked)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Shield,
                accent = extended.permitted,
                title = "Adlist Domains",
                value = formatCompactCount(summary.domainsOnBlocklist),
                subtitle = if (isAllDevicesMode) {
                    "Max across nodes"
                } else {
                    "${formatCompactCount(summary.uniqueDomains)} unique seen"
                }
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Devices,
                accent = extended.chartAccent,
                title = "Active Clients",
                value = formatCompactCount(summary.activeClients),
                subtitle = if (isAllDevicesMode) {
                    "${formatCompactCount(summary.queriesForwarded)} fwd • peak clients"
                } else {
                    "${formatCompactCount(summary.queriesForwarded)} forwarded"
                }
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    modifier: Modifier,
    icon: ImageVector,
    accent: Color,
    title: String,
    value: String,
    subtitle: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun QuickActionsRow(
    supportsGravity: Boolean,
    onNavigateToLogs: () -> Unit,
    onNavigateToLists: () -> Unit,
    onTriggerGravity: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onNavigateToLists,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("dashboard_manage_lists_button")
        ) {
            Icon(Icons.Rounded.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Domain Lists", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onNavigateToLogs,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("dashboard_live_logs_button")
        ) {
            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Live Logs", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (supportsGravity) {
            IconButton(
                onClick = onTriggerGravity,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .testTag("dashboard_gravity_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Sync,
                    contentDescription = "Trigger Gravity Update",
                    tint = EmeraldSignal
                )
            }
        }
    }
}

@Composable
private fun NoDeviceOnboardingCard(onAddDeviceClick: () -> Unit) {
    val extended = PiPulseThemeTokens.extended
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_network_shield_hero),
                contentDescription = "PiPulse local DNS network shield illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(extended.permittedContainer.copy(alpha = 0.92f))
                    .border(1.dp, EmeraldSignal.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = EmeraldSignal,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "100% LOCAL • ON-DEVICE VAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = extended.onPermittedContainer
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connect Your First Pi-hole",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add one or more Pi-hole v6 or v5 servers by LAN IP, local hostname, or WireGuard/Tailscale VPN address. No cloud account needed—all settings stay privately on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAddDeviceClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_add_pihole_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Pi-hole Device")
            }
        }
    }
}

@Composable
private fun UnreachableDeviceCard(
    title: String,
    endpointLabel: String,
    errorMessage: String,
    onRetry: () -> Unit,
    onAddAnother: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(extended.blockedContainer.copy(alpha = 0.55f))
            .border(1.dp, extended.blocked.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = "Connection error",
                tint = extended.blocked
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (endpointLabel.isNotBlank()) {
                    Text(
                        text = endpointLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = extended.onBlockedContainer
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("retry_connection_button")
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
            }
            OutlinedButton(
                onClick = onAddAnother,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Add Another")
            }
        }
    }
}
