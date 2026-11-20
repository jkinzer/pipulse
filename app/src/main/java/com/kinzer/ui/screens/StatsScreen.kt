package com.kinzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kinzer.data.DomainListType
import com.kinzer.data.PiholeDeviceProfile
import com.kinzer.ui.components.CompactDeviceSwitcher
import com.kinzer.ui.components.QueriesTimelineChart
import com.kinzer.ui.components.QueryTypesDonutChart
import com.kinzer.ui.components.RankedHorizontalBarList
import com.kinzer.ui.theme.CyanPulse
import com.kinzer.ui.theme.PiPulseThemeTokens
import com.kinzer.ui.viewmodel.LiveDeviceDataState

@Composable
fun StatsScreen(
    devices: List<PiholeDeviceProfile>,
    activeDevice: PiholeDeviceProfile?,
    isAllDevicesMode: Boolean,
    liveState: LiveDeviceDataState,
    onSelectAllDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAddDeviceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onQuickAddDomain: (domain: String, listType: DomainListType) -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val telemetry = liveState.telemetry
    val hasScope = isAllDevicesMode || activeDevice != null

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
                            text = "Traffic Stats",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isAllDevicesMode -> "Combined across ${liveState.fleetReachableCount}/${devices.size} nodes"
                                activeDevice != null -> "Blocked domains, clients & DNS"
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
                            tag = "stats_device_switcher"
                        )

                        IconButton(
                            onClick = onRefreshClick,
                            enabled = hasScope && !liveState.isRefreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .testTag("stats_refresh_button")
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
                                    contentDescription = "Refresh analytics"
                                )
                            }
                        }
                    }
                }
            }

            if (!hasScope) {
                item {
                    StatsEmptyCard(
                        title = "No Active Pi-hole",
                        subtitle = "Add a Pi-hole node in Devices to visualize blocked domains, client activity, and DNS record distributions."
                    )
                }
            } else if (telemetry == null && liveState.telemetryError == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanPulse)
                    }
                }
            } else if (telemetry == null) {
                item {
                    StatsEmptyCard(
                        title = "Analytics Unavailable",
                        subtitle = liveState.telemetryError ?: if (isAllDevicesMode) {
                            "Unable to fetch statistics from your Pi-hole nodes."
                        } else {
                            "Unable to fetch statistics from ${activeDevice?.name}."
                        }
                    )
                }
            } else {
                item {
                    AnalyticsSectionCard(
                        title = "Query Types Distribution",
                        subtitle = "Breakdown of A, AAAA, HTTPS, PTR, and other DNS records"
                    ) {
                        QueryTypesDonutChart(queryTypes = telemetry.queryTypes)
                    }
                }

                item {
                    AnalyticsSectionCard(
                        title = "Top Blocked Domains",
                        subtitle = "Highest-frequency ad & tracker domains • Tap + to whitelist"
                    ) {
                        RankedHorizontalBarList(
                            items = telemetry.topBlockedDomains,
                            barColor = extended.blocked,
                            emptyText = "No blocked domains recorded in the current window.",
                            actionIconIsAllow = true,
                            onQuickAction = { domain -> onQuickAddDomain(domain, DomainListType.ALLOW) }
                        )
                    }
                }

                item {
                    AnalyticsSectionCard(
                        title = "Top Network Clients",
                        subtitle = "Devices generating the most DNS requests"
                    ) {
                        RankedHorizontalBarList(
                            items = telemetry.topClients,
                            barColor = extended.cached,
                            emptyText = "No client telemetry available."
                        )
                    }
                }

                item {
                    AnalyticsSectionCard(
                        title = "Top Permitted Domains",
                        subtitle = "Most frequently resolved domains • Tap block icon to blacklist"
                    ) {
                        RankedHorizontalBarList(
                            items = telemetry.topPermittedDomains,
                            barColor = extended.permitted,
                            emptyText = "No permitted domains recorded in the current window.",
                            actionIconIsAllow = false,
                            onQuickAction = { domain -> onQuickAddDomain(domain, DomainListType.DENY) }
                        )
                    }
                }

                item {
                    AnalyticsSectionCard(
                        title = "DNS Volume Over Time",
                        subtitle = "Total vs. blocked query trend"
                    ) {
                        QueriesTimelineChart(buckets = telemetry.history)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun AnalyticsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun StatsEmptyCard(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.BarChart,
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
