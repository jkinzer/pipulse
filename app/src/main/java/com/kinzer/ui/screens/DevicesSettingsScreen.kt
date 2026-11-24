package com.kinzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kinzer.data.DeviceHealthMetrics
import com.kinzer.data.ManagementAuditLog
import com.kinzer.data.PiholeApiVersion
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.data.RollingUpdateSessionState
import com.kinzer.data.SshFleetConfig
import com.kinzer.ui.components.CompactDeviceSwitcher
import com.kinzer.ui.theme.CyanPulse
import com.kinzer.ui.theme.EmeraldSignal
import com.kinzer.ui.theme.JetBrainsMonoFontFamily
import com.kinzer.ui.theme.PiPulseThemeTokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DevicesSettingsScreen(
    refreshIntervalSeconds: Long,
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesSelected: Boolean,
    isOnline: Boolean,
    deviceHealthMap: Map<String, DeviceHealthMetrics>,
    healthRefreshing: Boolean,
    sshConfig: SshFleetConfig,
    rollingUpdateState: RollingUpdateSessionState,
    auditLogs: List<ManagementAuditLog>,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onRefreshHealth: () -> Unit,
    onOpenSshConfig: () -> Unit,
    onStartRollingUpdate: () -> Unit,
    onViewRollingUpdate: () -> Unit,
    onAddDeviceClick: () -> Unit,
    onEditDeviceClick: (PiholeDeviceProfile) -> Unit,
    onDeleteDevice: (PiholeDeviceProfile) -> Unit,
    onUpdateRefreshInterval: (Long) -> Unit,
    onClearAuditLogs: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    var devicePendingDelete by remember { mutableStateOf<PiholeDeviceProfile?>(null) }
    var showConfirmRollingUpdate by remember { mutableStateOf(false) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Devices",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${devices.size} local profile(s)",
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
                        if (devices.isNotEmpty()) {
                            CompactDeviceSwitcher(
                                devices = devices,
                                activeDevice = activeDevice,
                                isAllDevicesSelected = isAllDevicesSelected,
                                isOnline = isOnline,
                                onSelectAllDevices = onSelectAllDevices,
                                onSelectDevice = onSelectDevice,
                                onAddDeviceClick = onAddDeviceClick,
                                maxLabelWidth = 96.dp,
                                tag = "devices_device_switcher"
                            )
                        }
                        Button(
                            onClick = onAddDeviceClick,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("devices_add_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add")
                        }
                    }
                }
            }

            if (devices.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Router,
                            contentDescription = null,
                            tint = EmeraldSignal,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = "No Pi-hole Devices Configured",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Add multiple Pi-hole instances (Home LAN, Secondary DNS, or WireGuard VPN) to monitor and control them locally from one app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item {
                    val onlineCount = devices.count { deviceHealthMap[it.id]?.online == true }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (deviceHealthMap.isEmpty()) {
                                "Checking node health & sensors..."
                            } else {
                                "Node Health • $onlineCount/${devices.size} online"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onRefreshHealth,
                            enabled = !healthRefreshing,
                            modifier = Modifier.testTag("refresh_device_health_button")
                        ) {
                            if (healthRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanPulse
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Probing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Refresh Health")
                            }
                        }
                    }
                }

                items(devices, key = { it.id }) { device ->
                    val isActive = isAllDevicesSelected || device.id == activeDevice?.id
                    DeviceProfileCard(
                        device = device,
                        health = deviceHealthMap[device.id],
                        isActive = isActive,
                        onSelect = { onSelectDevice(device.id) },
                        onEdit = { onEditDeviceClick(device) },
                        onDelete = { devicePendingDelete = device }
                    )
                }
            }

            // SSH Rolling Update & Server Maintenance Card
            item {
                SshRollingUpdateCard(
                    devices = devices,
                    sshConfig = sshConfig,
                    rollingUpdateState = rollingUpdateState,
                    onConfigureSsh = onOpenSshConfig,
                    onRequestRollingUpdate = {
                        if (rollingUpdateState.isRunning) {
                            onViewRollingUpdate()
                        } else {
                            showConfirmRollingUpdate = true
                        }
                    },
                    onViewLastOutput = onViewRollingUpdate
                )
            }

            // Polling Cadence Settings
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
                        text = "Live Telemetry Polling Cadence",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Controls dashboard and real-time query log refresh rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3L, 5L, 10L, 30L).forEach { seconds ->
                            FilterChip(
                                selected = refreshIntervalSeconds == seconds,
                                onClick = { onUpdateRefreshInterval(seconds) },
                                label = { Text("${seconds}s") },
                                modifier = Modifier.testTag("poll_interval_${seconds}s")
                            )
                        }
                    }
                }
            }

            // Local Management Activity Log
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = CyanPulse,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Local Activity Log",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (auditLogs.isNotEmpty()) {
                            TextButton(
                                onClick = onClearAuditLogs,
                                modifier = Modifier.testTag("clear_audit_logs_button")
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    if (auditLogs.isEmpty()) {
                        Text(
                            text = "Blocking toggles, whitelist/blacklist edits, and device changes will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            auditLogs.take(12).forEach { log ->
                                AuditLogItemRow(log = log)
                            }
                        }
                    }
                }
            }

            // On-Device Privacy & Security Status Card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(extended.permittedContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = extended.permitted
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local-Only Mode Active",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Device profiles are saved in local SQLite (Room) and Pi-hole passwords/tokens are encrypted in Android Keystore (AES-256-GCM). No cloud sign-in required.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    devicePendingDelete?.let { dev ->
        AlertDialog(
            onDismissRequest = { devicePendingDelete = null },
            title = { Text("Remove Pi-hole Device?") },
            text = {
                Text("Remove \"${dev.name}\" (${dev.host}) and delete its locally encrypted API token?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDevice(dev)
                        devicePendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_device_button")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { devicePendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showConfirmRollingUpdate) {
        val orderedNames = devices.mapIndexed { idx, d -> "${idx + 1}. ${d.name} (${d.host})" }.joinToString("\n")
        AlertDialog(
            onDismissRequest = { showConfirmRollingUpdate = false },
            title = { Text("Run Rolling Update on ${devices.size} Server(s)?") },
            text = {
                Text(
                    text = "Each server will be updated in sequence:\n\n$orderedNames\n\n" +
                        "Script: ${sshConfig.scriptPath}\n" +
                        "User: ${sshConfig.username} (port ${sshConfig.port})\n\n" +
                        "If needrestart or /var/run/reboot-required signals a reboot, PiPulse will run sudo reboot, disconnect, and wait until the server finishes rebooting and Pi-hole DNS responds before starting the next server."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRollingUpdate = false
                        onStartRollingUpdate()
                    },
                    modifier = Modifier.testTag("confirm_rolling_update_button")
                ) {
                    Text("Update Now", color = EmeraldSignal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRollingUpdate = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SshRollingUpdateCard(
    devices: List<PiholeDeviceProfile>,
    sshConfig: SshFleetConfig,
    rollingUpdateState: RollingUpdateSessionState,
    onConfigureSsh: () -> Unit,
    onRequestRollingUpdate: () -> Unit,
    onViewLastOutput: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val canLaunchUpdate = devices.isNotEmpty() && sshConfig.isReadyForUpdate

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (rollingUpdateState.isRunning) 1.5.dp else 1.dp,
                color = if (rollingUpdateState.isRunning) CyanPulse else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyanPulse.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        tint = CyanPulse,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "SSH Rolling Update",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sequential script run, needrestart check & safe reboot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Configuration status pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgePill(
                text = "${sshConfig.username}:${sshConfig.port}",
                accent = CyanPulse
            )
            if (sshConfig.hasPrivateKey) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(extended.permittedContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = extended.permitted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = sshConfig.keyFileName.ifBlank { "id_rsa" },
                        style = MaterialTheme.typography.labelSmall,
                        color = extended.permitted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                BadgePill(
                    text = "SSH Key Not Set",
                    accent = extended.warning
                )
            }
        }

        // Configured script preview
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = sshConfig.scriptPath.ifBlank { "No update script configured yet" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = if (sshConfig.scriptPath.isNotBlank()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rollingUpdateState.summaryMessage != null && !rollingUpdateState.isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rollingUpdateState.summaryMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rollingUpdateState.haltedOnFailure) extended.blocked else extended.permitted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onViewLastOutput,
                    modifier = Modifier.testTag("ssh_view_last_log_button")
                ) {
                    Text("View Log")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onConfigureSsh,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("ssh_configure_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("SSH Config", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Button(
                onClick = onRequestRollingUpdate,
                enabled = rollingUpdateState.isRunning || canLaunchUpdate,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(48.dp)
                    .testTag("ssh_update_all_button")
            ) {
                if (rollingUpdateState.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Progress", maxLines = 1)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (devices.size > 1) "Update All (${devices.size})" else "Update Server",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceProfileCard(
    device: PiholeDeviceProfile,
    health: DeviceHealthMetrics?,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val borderColor = if (isActive) extended.permitted else MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isActive) extended.permittedContainer.copy(alpha = 0.24f)
                else MaterialTheme.colorScheme.surface
            )
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .padding(14.dp)
            .testTag("device_card_${device.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Router,
                    contentDescription = if (isActive) "Active device" else "Inactive device",
                    tint = if (isActive) extended.permitted else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.endpointDisplay,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_device_button_${device.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${device.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_device_button_${device.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${device.name}",
                        tint = extended.blocked
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BadgePill(text = device.apiVersion, accent = CyanPulse)
            if (device.locationTag.isNotBlank()) {
                BadgePill(text = device.locationTag, accent = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (device.hasStoredCredential) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(extended.permittedContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        tint = extended.permitted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = extended.permitted
                    )
                }
            }
            if (health != null && health.online && health.latencyMs != null) {
                BadgePill(
                    text = "${health.latencyMs} ms",
                    accent = when {
                        health.latencyMs < 150L -> extended.permitted
                        health.latencyMs < 500L -> extended.warning
                        else -> extended.blocked
                    }
                )
            }
            if (health?.updateAvailable == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(extended.warningContainer)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdateAlt,
                        contentDescription = null,
                        tint = extended.warning,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Update",
                        style = MaterialTheme.typography.labelSmall,
                        color = extended.warning
                    )
                }
            }
        }

        if (health != null) {
            DeviceHealthSensorsPanel(
                device = device,
                health = health
            )
        }
    }
}

@Composable
private fun DeviceHealthSensorsPanel(
    device: PiholeDeviceProfile,
    health: DeviceHealthMetrics
) {
    val extended = PiPulseThemeTokens.extended

    if (!health.online) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(extended.blockedContainer.copy(alpha = 0.45f))
                .border(1.dp, extended.blocked.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = "Offline",
                tint = extended.blocked,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = health.errorMessage?.ifBlank { "Node unreachable" } ?: "Node unreachable",
                style = MaterialTheme.typography.labelSmall,
                color = extended.onBlockedContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val hasHardwareMetrics = health.cpuPercent != null ||
        health.memoryUsedPercent != null ||
        health.cpuTempCelsius != null ||
        health.uptimeSeconds != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (hasHardwareMetrics) {
            // Row 1: CPU & RAM gauges
            if (health.cpuPercent != null || health.memoryUsedPercent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    health.cpuPercent?.let { cpuPct ->
                        val cpuColor = when {
                            cpuPct >= 85.0 -> extended.blocked
                            cpuPct >= 65.0 -> extended.warning
                            else -> extended.cached
                        }
                        val loadDetail = health.cpuLoad1m?.let {
                            String.format(Locale.US, "Load %.2f", it)
                        } ?: health.cpuCores?.let { "$it cores" }.orEmpty()
                        SensorBarTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Speed,
                            label = "CPU",
                            valueText = String.format(Locale.US, "%.0f%%", cpuPct),
                            detailText = loadDetail,
                            progress = (cpuPct / 100.0).toFloat().coerceIn(0f, 1f),
                            accent = cpuColor
                        )
                    }
                    health.memoryUsedPercent?.let { memPct ->
                        val memColor = when {
                            memPct >= 88.0 -> extended.blocked
                            memPct >= 72.0 -> extended.warning
                            else -> extended.permitted
                        }
                        val memDetail = if (health.memoryUsedMb != null && health.memoryTotalMb != null) {
                            "${health.memoryUsedMb}/${health.memoryTotalMb} MB"
                        } else {
                            ""
                        }
                        SensorBarTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Memory,
                            label = "RAM",
                            valueText = String.format(Locale.US, "%.0f%%", memPct),
                            detailText = memDetail,
                            progress = (memPct / 100.0).toFloat().coerceIn(0f, 1f),
                            accent = memColor
                        )
                    }
                }
            }

            // Row 2: Temperature & Uptime / Version
            if (health.cpuTempCelsius != null || health.uptimeSeconds != null || health.coreVersion != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    health.cpuTempCelsius?.let { tempC ->
                        val hotLimit = health.tempHotLimitCelsius ?: 70.0
                        val warnLimit = (hotLimit - 10.0).coerceAtLeast(55.0)
                        val tempColor = when {
                            tempC >= hotLimit -> extended.blocked
                            tempC >= warnLimit -> extended.warning
                            else -> extended.permitted
                        }
                        SensorBarTile(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.DeviceThermostat,
                            label = "CPU Temp",
                            valueText = String.format(Locale.US, "%.1f°C", tempC),
                            detailText = String.format(Locale.US, "Limit %.0f°C", hotLimit),
                            progress = (tempC / hotLimit.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f),
                            accent = tempColor
                        )
                    }
                    if (health.uptimeSeconds != null || health.coreVersion != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Uptime",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = health.uptimeSeconds?.let { formatUptime(it) } ?: "Online",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val versionStr = buildString {
                                health.coreVersion?.let { append("Core $it") }
                                if (health.ftlVersion != null) {
                                    if (isNotEmpty()) append(" • ")
                                    append("FTL ${health.ftlVersion}")
                                }
                            }
                            if (versionStr.isNotBlank()) {
                                Text(
                                    text = versionStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Pi-hole v5 or container without OS metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (health.blockingEnabled != false) extended.permitted else extended.warning)
                    )
                    Text(
                        text = if (health.blockingEnabled != false) "Online • Blocking Active" else "Online • Blocking Paused",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (device.parsedApiVersion == PiholeApiVersion.V5) {
                    Text(
                        text = "OS sensors need v6",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorBarTile(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    valueText: String,
    detailText: String,
    progress: Float,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = accent,
                maxLines = 1
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
        )
        if (detailText.isNotBlank()) {
            Text(
                text = detailText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val mins = (seconds % 3_600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${mins}m"
        else -> "${mins.coerceAtLeast(1L)}m"
    }
}

@Composable
private fun BadgePill(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun AuditLogItemRow(log: ManagementAuditLog) {
    val timeStr = remember(log.createdAtMs) {
        val ts = if (log.createdAtMs > 0L) log.createdAtMs else System.currentTimeMillis()
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.parsedAction.label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${log.deviceName}${if (log.target.isNotBlank()) " • ${log.target}" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
