package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DomainListRule
import com.example.data.DomainListType
import com.example.data.PiholeDeviceProfile
import com.example.ui.components.CompactDeviceSwitcher
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PiPulseThemeTokens
import com.example.ui.viewmodel.LiveDeviceDataState

@Composable
fun DomainListsScreen(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    liveState: LiveDeviceDataState,
    selectedTab: DomainListType,
    searchQuery: String,
    kindFilter: String,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onSelectTab: (DomainListType) -> Unit,
    onSearchChange: (String) -> Unit,
    onKindFilterChange: (String) -> Unit,
    onRefreshClick: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onRemoveRule: (DomainListRule) -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val hasScope = isAllDevicesMode || activeDevice != null
    var pendingDeleteRule by remember { mutableStateOf<DomainListRule?>(null) }

    val allowCount = remember(liveState.domainRules) {
        liveState.domainRules.count { it.listType == DomainListType.ALLOW }
    }
    val denyCount = remember(liveState.domainRules) {
        liveState.domainRules.count { it.listType == DomainListType.DENY }
    }

    val filteredRules = remember(liveState.domainRules, selectedTab, searchQuery, kindFilter) {
        val needle = searchQuery.trim().lowercase()
        liveState.domainRules.filter { rule ->
            val tabMatch = rule.listType == selectedTab
            val kindMatch = when (kindFilter) {
                "EXACT" -> !rule.regexRule
                "REGEX" -> rule.regexRule
                else -> true
            }
            val textMatch = needle.isEmpty() ||
                rule.domain.lowercase().contains(needle) ||
                rule.comment.lowercase().contains(needle) ||
                rule.deviceName.lowercase().contains(needle)
            tabMatch && kindMatch && textMatch
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            val isOnline = if (isAllDevicesMode) {
                liveState.fleetReachableCount > 0
            } else {
                liveState.telemetry != null && liveState.telemetryError == null
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Domain Lists",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isAllDevicesMode -> "Combined across ${devices.size} Pi-holes"
                            activeDevice != null -> "Whitelist & blacklist rules"
                            else -> "Select a Pi-hole device"
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
                        tag = "lists_device_switcher"
                    )

                    IconButton(
                        onClick = onRefreshClick,
                        enabled = hasScope && !liveState.domainRulesLoading,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .testTag("domain_lists_refresh_button")
                    ) {
                        if (liveState.domainRulesLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CyanPulse
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh domain lists"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Segmented Whitelist / Blacklist selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DomainTabSegment(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("whitelist_tab_button"),
                    selected = selectedTab == DomainListType.ALLOW,
                    label = "Whitelist",
                    count = allowCount,
                    accent = extended.permitted,
                    container = extended.permittedContainer,
                    icon = Icons.Rounded.CheckCircleOutline,
                    onClick = { onSelectTab(DomainListType.ALLOW) }
                )
                DomainTabSegment(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("blacklist_tab_button"),
                    selected = selectedTab == DomainListType.DENY,
                    label = "Blacklist",
                    count = denyCount,
                    accent = extended.blocked,
                    container = extended.blockedContainer,
                    icon = Icons.Rounded.Block,
                    onClick = { onSelectTab(DomainListType.DENY) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search ${selectedTab.label.lowercase()} domains or comments...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("domain_lists_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All Rules", "EXACT" to "Exact", "REGEX" to "Regex").forEach { (key, label) ->
                    FilterChip(
                        selected = kindFilter == key,
                        onClick = { onKindFilterChange(key) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("domain_kind_chip_$key")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !hasScope -> {
                    EmptyDomainListState(
                        title = "No Active Pi-hole",
                        subtitle = "Configure a Pi-hole device to view, add, and remove whitelist or blacklist entries."
                    )
                }
                liveState.domainRulesLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = EmeraldSignal)
                    }
                }
                liveState.domainRulesError != null && liveState.domainRules.isEmpty() -> {
                    EmptyDomainListState(
                        title = "Unable to Load Domain Lists",
                        subtitle = liveState.domainRulesError
                    )
                }
                filteredRules.isEmpty() -> {
                    EmptyDomainListState(
                        title = "No ${selectedTab.label} Entries",
                        subtitle = if (searchQuery.isNotBlank()) {
                            "No domains matched \"$searchQuery\"."
                        } else if (isAllDevicesMode) {
                            "Tap Add Domain to create a ${selectedTab.label.lowercase()} rule across your Pi-holes."
                        } else {
                            "Tap Add Domain to create your first ${selectedTab.label.lowercase()} rule on ${activeDevice?.name}."
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredRules, key = { it.id }) { rule ->
                            DomainRuleCard(
                                rule = rule,
                                showDeviceBadge = isAllDevicesMode,
                                onDeleteClick = { pendingDeleteRule = rule }
                            )
                        }
                    }
                }
            }
        }

        if (hasScope) {
            ExtendedFloatingActionButton(
                onClick = onOpenAddDialog,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add Domain") },
                containerColor = if (selectedTab == DomainListType.ALLOW) extended.permitted else extended.blocked,
                contentColor = Color(0xFF041A15),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .testTag("add_domain_fab")
            )
        }
    }

    pendingDeleteRule?.let { rule ->
        val ownerName = rule.deviceName.ifBlank { activeDevice?.name ?: "Pi-hole" }
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            title = { Text("Remove from ${rule.listType.label}?") },
            text = {
                Text(
                    text = "Remove \"${rule.domain}\" from $ownerName?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveRule(rule)
                        pendingDeleteRule = null
                    },
                    modifier = Modifier.testTag("confirm_delete_domain_button")
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRule = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DomainTabSegment(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    count: Int,
    accent: Color,
    container: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) container else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DomainRuleCard(
    rule: DomainListRule,
    showDeviceBadge: Boolean,
    onDeleteClick: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val accent = if (rule.listType == DomainListType.ALLOW) extended.permitted else extended.blocked

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = rule.domain,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (rule.regexRule) extended.warningContainer else accent.copy(alpha = 0.14f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (rule.regexRule) "REGEX" else "EXACT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (rule.regexRule) extended.warning else accent
                    )
                }
                if (showDeviceBadge && rule.deviceName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanPulse.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = rule.deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 80.dp)
                        )
                    }
                }
            }
            if (rule.comment.isNotBlank()) {
                Text(
                    text = rule.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_domain_button_${rule.domain}")
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Remove ${rule.domain}",
                tint = extended.blocked
            )
        }
    }
}

@Composable
private fun EmptyDomainListState(title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatListBulleted,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
