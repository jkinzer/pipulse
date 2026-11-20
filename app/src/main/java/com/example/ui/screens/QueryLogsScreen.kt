package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DnsQueryLogEntry
import com.example.data.DomainListType
import com.example.data.PiholeDeviceProfile
import com.example.data.QueryStatusCategory
import com.example.ui.components.CompactDeviceSwitcher
import com.example.ui.components.formatEpochTime
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PiPulseThemeTokens
import com.example.ui.viewmodel.LiveDeviceDataState
import java.util.Locale

@Composable
fun QueryLogsScreen(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    liveState: LiveDeviceDataState,
    searchQuery: String,
    selectedCategory: QueryStatusCategory,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (QueryStatusCategory) -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onRefreshClick: () -> Unit,
    onQuickAddDomain: (domain: String, listType: DomainListType) -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val hasScope = isAllDevicesMode || activeDevice != null

    val filteredLogs = remember(liveState.queryLogs, searchQuery, selectedCategory) {
        val needle = searchQuery.trim().lowercase()
        liveState.queryLogs.filter { entry ->
            val catMatch = selectedCategory == QueryStatusCategory.ALL || entry.category == selectedCategory
            val textMatch = needle.isEmpty() ||
                entry.domain.lowercase().contains(needle) ||
                entry.client.lowercase().contains(needle) ||
                entry.queryType.lowercase().contains(needle) ||
                entry.deviceName.lowercase().contains(needle)
            catMatch && textMatch
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
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val switcherMaxWidth = if (maxWidth < 360.dp) 74.dp else 104.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Query Log",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasScope) {
                                "${filteredLogs.size} of ${liveState.queryLogs.size} queries"
                            } else {
                                "No active Pi-hole"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            maxLabelWidth = switcherMaxWidth,
                            tag = "logs_device_switcher"
                        )

                        IconButton(
                            onClick = onToggleAutoRefresh,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (liveState.autoRefreshEnabled) extended.permittedContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .testTag("logs_live_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (liveState.autoRefreshEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (liveState.autoRefreshEnabled) "Pause live polling" else "Resume live polling",
                                tint = if (liveState.autoRefreshEnabled) extended.permitted else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onRefreshClick,
                            enabled = hasScope && !liveState.isRefreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .testTag("logs_refresh_button")
                        ) {
                            if (liveState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = CyanPulse
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh query log"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter by domain, client IP, or record type...") },
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
                    .testTag("query_logs_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueryStatusCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategoryChange(cat) },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("query_filter_chip_${cat.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !hasScope -> {
                    EmptyLogsPlaceholder(
                        title = "No Active Pi-hole",
                        subtitle = "Add a Pi-hole server on the Devices tab to stream real-time DNS queries."
                    )
                }
                liveState.queryLogsLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanPulse)
                    }
                }
                liveState.queryLogsError != null && liveState.queryLogs.isEmpty() -> {
                    EmptyLogsPlaceholder(
                        title = "Query Log Unavailable",
                        subtitle = liveState.queryLogsError
                    )
                }
                filteredLogs.isEmpty() -> {
                    EmptyLogsPlaceholder(
                        title = "No Matching Queries",
                        subtitle = if (searchQuery.isNotBlank() || selectedCategory != QueryStatusCategory.ALL) {
                            "Try clearing your search or selecting All categories."
                        } else if (isAllDevicesMode) {
                            "No DNS queries returned yet across your Pi-hole nodes."
                        } else {
                            "No DNS queries returned yet by ${activeDevice?.name}."
                        }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { entry ->
                            QueryLogRowCard(
                                entry = entry,
                                showDeviceBadge = isAllDevicesMode,
                                mutationInProgress = liveState.domainMutationInProgress,
                                onWhitelist = { onQuickAddDomain(entry.domain, DomainListType.ALLOW) },
                                onBlacklist = { onQuickAddDomain(entry.domain, DomainListType.DENY) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryLogRowCard(
    entry: DnsQueryLogEntry,
    showDeviceBadge: Boolean,
    mutationInProgress: Boolean,
    onWhitelist: () -> Unit,
    onBlacklist: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val statusColor = when (entry.category) {
        QueryStatusCategory.BLOCKED -> extended.blocked
        QueryStatusCategory.CACHED -> extended.cached
        QueryStatusCategory.PERMITTED -> extended.permitted
        QueryStatusCategory.ALL -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = entry.domain,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showDeviceBadge && entry.deviceName.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyanPulse.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = entry.deviceName,
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPulse,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 84.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = entry.queryType,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.client} • ${formatEpochTime(entry.timestampEpoch)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val latencyStr = entry.responseTimeMs?.let {
                    String.format(Locale.US, " • %.1f ms", it)
                }.orEmpty()
                Text(
                    text = "${entry.statusLabel}$latencyStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onWhitelist,
                    enabled = !mutationInProgress && entry.domain.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircleOutline,
                        contentDescription = "Whitelist ${entry.domain}",
                        tint = EmeraldSignal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onBlacklist,
                    enabled = !mutationInProgress && entry.domain.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = "Blacklist ${entry.domain}",
                        tint = extended.blocked,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLogsPlaceholder(title: String, subtitle: String) {
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
                imageVector = Icons.Rounded.Terminal,
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
