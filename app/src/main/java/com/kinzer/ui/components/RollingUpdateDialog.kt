package com.kinzer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinzer.data.RollingUpdateNodePhase
import com.kinzer.data.RollingUpdateNodeStatus
import com.kinzer.data.RollingUpdateSessionState
import com.kinzer.ui.theme.CyanPulse
import com.kinzer.ui.theme.JetBrainsMonoFontFamily
import com.kinzer.ui.theme.PiPulseThemeTokens

@Composable
fun RollingUpdateDialog(
    state: RollingUpdateSessionState,
    onCancelRun: () -> Unit,
    onDismiss: () -> Unit
) {
    val extended = PiPulseThemeTokens.extended
    val logListState = rememberLazyListState()

    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            logListState.animateScrollToItem(state.logs.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!state.isRunning) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !state.isRunning,
            dismissOnClickOutside = !state.isRunning
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Fleet Rolling Update",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                state.isRunning -> "Updating nodes sequentially with reboot recovery"
                                state.wasCancelled -> "Rolling update cancelled"
                                state.haltedOnFailure -> "Halted on error to preserve remaining DNS nodes"
                                else -> state.summaryMessage ?: "Rolling update complete"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                state.haltedOnFailure -> extended.blocked
                                state.isFinished && !state.wasCancelled -> extended.permitted
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (state.isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = CyanPulse
                        )
                    }
                }

                // Ordered Node Pipeline
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.nodes.forEachIndexed { index, node ->
                        RollingUpdateNodeRow(
                            orderNumber = index + 1,
                            node = node
                        )
                    }
                }

                // Live Terminal Console
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "SSH Console Output (${state.logs.size} lines)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        state = logListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 230.dp)
                            .testTag("rolling_update_console"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.logs) { line ->
                            val lineColor = when {
                                line.contains("ERROR:", ignoreCase = true) || line.startsWith("[err]") -> extended.blocked
                                line.startsWith("===") -> CyanPulse
                                line.contains("Complete", ignoreCase = true) ||
                                    line.contains("confirmed", ignoreCase = true) -> extended.permitted
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                            }
                            Text(
                                text = line,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily
                                ),
                                color = lineColor
                            )
                        }
                    }
                }

                // Footer Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.isRunning) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rolling_update_hide_button")
                        ) {
                            Text("Hide")
                        }
                        Button(
                            onClick = onCancelRun,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = extended.blocked
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("rolling_update_cancel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("rolling_update_done_button")
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RollingUpdateNodeRow(
    orderNumber: Int,
    node: RollingUpdateNodeStatus
) {
    val extended = PiPulseThemeTokens.extended
    val isActivePhase = node.phase in listOf(
        RollingUpdateNodePhase.CONNECTING,
        RollingUpdateNodePhase.RUNNING_SCRIPT,
        RollingUpdateNodePhase.CHECKING_REBOOT,
        RollingUpdateNodePhase.REBOOTING,
        RollingUpdateNodePhase.VERIFYING_DNS
    )
    val accent = when (node.phase) {
        RollingUpdateNodePhase.COMPLETED -> extended.permitted
        RollingUpdateNodePhase.FAILED -> extended.blocked
        RollingUpdateNodePhase.REBOOTING -> extended.warning
        RollingUpdateNodePhase.QUEUED, RollingUpdateNodePhase.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> CyanPulse
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (isActivePhase) 1.5.dp else 1.dp,
                color = if (isActivePhase) accent.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("rolling_update_node_${node.deviceId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isActivePhase -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
                node.phase == RollingUpdateNodePhase.COMPLETED -> Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Completed",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                node.phase == RollingUpdateNodePhase.FAILED -> Icon(
                    imageVector = Icons.Rounded.Error,
                    contentDescription = "Failed",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                node.phase == RollingUpdateNodePhase.SKIPPED -> Icon(
                    imageVector = Icons.Rounded.RemoveCircleOutline,
                    contentDescription = "Skipped",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = "Queued",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "#$orderNumber ${node.deviceName}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = node.host,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (node.detail.isNotBlank()) "${node.phase.label} • ${node.detail}" else node.phase.label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
