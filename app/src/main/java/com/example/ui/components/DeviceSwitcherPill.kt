package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.PiholeDeviceProfile
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.PiPulseThemeTokens

/**
 * Compact 48dp-tall device switcher chip designed to sit right next to top-bar action buttons
 * (such as Refresh) consistently across Dashboard, Logs, Lists, Stats, and Devices screens.
 * Supports "All Devices" combined fleet mode whenever 2 or more Pi-hole nodes are configured.
 */
@Composable
fun CompactDeviceSwitcher(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesSelected: Boolean,
    isOnline: Boolean,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLabelWidth: Dp = 108.dp,
    tag: String = "compact_device_switcher"
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val extended = PiPulseThemeTokens.extended
    val pulseAlpha = rememberStatusPulseAlpha(isOnline)

    val hasAnySelection = isAllDevicesSelected || activeDevice != null
    val borderColor = if (hasAnySelection && isOnline) {
        extended.permitted.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    val displayLabel = when {
        devices.isEmpty() -> "Add Node"
        isAllDevicesSelected -> "All (${devices.size})"
        activeDevice != null -> activeDevice.name
        else -> "Add Node"
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .clickable(role = Role.DropdownList) {
                    if (devices.isEmpty()) {
                        onAddDeviceClick()
                    } else {
                        menuExpanded = true
                    }
                }
                .padding(start = 10.dp, end = 6.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(if (isOnline) pulseAlpha else 1f)
                    .clip(CircleShape)
                    .background(
                        when {
                            !hasAnySelection -> MaterialTheme.colorScheme.outline
                            isOnline -> extended.permitted
                            else -> extended.blocked
                        }
                    )
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = maxLabelWidth)
            )
            Icon(
                imageVector = if (devices.isEmpty()) Icons.Rounded.Add else Icons.Rounded.ExpandMore,
                contentDescription = if (devices.isEmpty()) "Add Pi-hole device" else "Switch Pi-hole device",
                tint = if (devices.isEmpty()) CyanPulse else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        DeviceSwitcherDropdownMenu(
            expanded = menuExpanded,
            devices = devices,
            activeDevice = activeDevice,
            isAllDevicesSelected = isAllDevicesSelected,
            onDismiss = { menuExpanded = false },
            onSelectAllDevices = {
                menuExpanded = false
                onSelectAllDevices()
            },
            onSelectDevice = { id ->
                menuExpanded = false
                onSelectDevice(id)
            },
            onAddDeviceClick = {
                menuExpanded = false
                onAddDeviceClick()
            }
        )
    }
}

@Composable
private fun rememberStatusPulseAlpha(isOnline: Boolean): Float {
    if (!isOnline) return 1f
    val infiniteTransition = rememberInfiniteTransition(label = "deviceStatusPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "deviceStatusPulseAlpha"
    )
    return alpha
}

@Composable
private fun DeviceSwitcherDropdownMenu(
    expanded: Boolean,
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesSelected: Boolean,
    onDismiss: () -> Unit,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        if (devices.size >= 2) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = "All Devices",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isAllDevicesSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Combined stats • ${devices.size} Pi-holes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Hub,
                        contentDescription = null,
                        tint = if (isAllDevicesSelected) EmeraldSignal else CyanPulse
                    )
                },
                trailingIcon = if (isAllDevicesSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "All devices selected",
                            tint = EmeraldSignal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                onClick = onSelectAllDevices,
                modifier = Modifier.testTag("switch_device_item_all")
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        devices.forEach { device ->
            val isSelected = !isAllDevicesSelected && device.id == activeDevice?.id
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${device.endpointDisplay} (${device.apiVersion})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Router,
                        contentDescription = null,
                        tint = if (isSelected) EmeraldSignal else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Active device",
                            tint = EmeraldSignal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                onClick = { onSelectDevice(device.id) },
                modifier = Modifier.testTag("switch_device_item_${device.id}")
            )
        }
        if (devices.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        DropdownMenuItem(
            text = { Text("Add New Pi-hole Device") },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, tint = CyanPulse) },
            onClick = onAddDeviceClick,
            modifier = Modifier.testTag("switch_device_add_item")
        )
    }
}
