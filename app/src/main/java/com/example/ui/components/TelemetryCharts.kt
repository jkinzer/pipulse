package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.HistoryBucket
import com.example.data.QueryTypeMetric
import com.example.data.RankedMetricItem
import com.example.ui.theme.AmberPause
import com.example.ui.theme.CoralBlocked
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldSignal
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PiPulseThemeTokens
import com.example.ui.theme.VioletMetric
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.2fM", value / 1_000_000.0)
    value >= 10_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> NumberFormat.getIntegerInstance(Locale.US).format(value)
}

fun formatEpochTime(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "--:--"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1000L))
}

fun formatEpochShort(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "--:--"
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochSeconds * 1000L))
}

@Composable
fun QueriesTimelineChart(
    buckets: List<HistoryBucket>,
    modifier: Modifier = Modifier
) {
    val extended = PiPulseThemeTokens.extended
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val totalColor = extended.cached
    val blockedColor = extended.blocked

    if (buckets.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Awaiting DNS time-series buckets from Pi-hole",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val recentBuckets = if (buckets.size > 48) buckets.takeLast(48) else buckets
    val maxTotal = recentBuckets.maxOf { it.totalQueries }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = totalColor, label = "Total DNS")
                LegendDot(color = blockedColor, label = "Blocked")
            }
            Text(
                text = "Peak ${formatCompactCount(maxTotal)}/bucket",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
        ) {
            val w = size.width
            val h = size.height
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)

            // Horizontal telemetry grid lines
            for (step in 0..3) {
                val y = padTop + usableH * (step / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val count = recentBuckets.size
            val stepX = if (count > 1) w / (count - 1).toFloat() else w

            val totalPoints = recentBuckets.mapIndexed { idx, bucket ->
                val x = if (count > 1) idx * stepX else w / 2f
                val ratio = (bucket.totalQueries.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
                Offset(x, padTop + usableH * (1f - ratio))
            }

            val blockedPoints = recentBuckets.mapIndexed { idx, bucket ->
                val x = if (count > 1) idx * stepX else w / 2f
                val ratio = (bucket.blockedQueries.toFloat() / maxTotal.toFloat()).coerceIn(0f, 1f)
                Offset(x, padTop + usableH * (1f - ratio))
            }

            fun buildAreaPath(points: List<Offset>): Pair<Path, Path> {
                val strokePath = Path()
                val fillPath = Path()
                if (points.isEmpty()) return strokePath to fillPath
                strokePath.moveTo(points.first().x, points.first().y)
                fillPath.moveTo(points.first().x, padTop + usableH)
                fillPath.lineTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val midX = (prev.x + curr.x) / 2f
                    strokePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
                fillPath.lineTo(points.last().x, padTop + usableH)
                fillPath.close()
                return strokePath to fillPath
            }

            val (totalStroke, totalFill) = buildAreaPath(totalPoints)
            drawPath(
                path = totalFill,
                brush = Brush.verticalGradient(
                    colors = listOf(totalColor.copy(alpha = 0.28f), totalColor.copy(alpha = 0.02f)),
                    startY = padTop,
                    endY = padTop + usableH
                )
            )
            drawPath(
                path = totalStroke,
                color = totalColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val (blockedStroke, blockedFill) = buildAreaPath(blockedPoints)
            drawPath(
                path = blockedFill,
                brush = Brush.verticalGradient(
                    colors = listOf(blockedColor.copy(alpha = 0.32f), blockedColor.copy(alpha = 0.03f)),
                    startY = padTop,
                    endY = padTop + usableH
                )
            )
            drawPath(
                path = blockedStroke,
                color = blockedColor,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Latest point glow indicator
            totalPoints.lastOrNull()?.let { pt ->
                drawCircle(color = totalColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = pt)
                drawCircle(color = totalColor, radius = 3.dp.toPx(), center = pt)
            }
            blockedPoints.lastOrNull()?.let { pt ->
                drawCircle(color = blockedColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = pt)
                drawCircle(color = blockedColor, radius = 3.dp.toPx(), center = pt)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatEpochShort(recentBuckets.first().timestampEpoch),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatEpochShort(recentBuckets[recentBuckets.size / 2].timestampEpoch),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatEpochShort(recentBuckets.last().timestampEpoch),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QueryTypesDonutChart(
    queryTypes: List<QueryTypeMetric>,
    modifier: Modifier = Modifier
) {
    val palette = listOf(
        EmeraldSignal,
        CyanPulse,
        VioletMetric,
        AmberPause,
        CoralBlocked,
        Color(0xFF38BDF8),
        Color(0xFFA3E635)
    )

    if (queryTypes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No DNS record type breakdown available yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val topTypes = queryTypes.take(6)
    val totalPct = topTypes.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(1f)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 18.dp.toPx()
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                val topLeft = Offset(strokeW / 2f, strokeW / 2f)

                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW)
                )

                var startAngle = -90f
                topTypes.forEachIndexed { index, item ->
                    val sweep = (item.percentage / totalPct) * 360f
                    if (sweep > 0.5f) {
                        drawArc(
                            color = palette[index % palette.size],
                            startAngle = startAngle,
                            sweepAngle = (sweep - 1.5f).coerceAtLeast(1f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = topTypes.first().type,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(Locale.US, "%.1f%%", topTypes.first().percentage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            topTypes.forEachIndexed { index, metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(palette[index % palette.size])
                        )
                        Text(
                            text = metric.type,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f%%", metric.percentage),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RankedHorizontalBarList(
    items: List<RankedMetricItem>,
    barColor: Color,
    emptyText: String,
    actionIconIsAllow: Boolean? = null,
    onQuickAction: ((String) -> Unit)? = null
) {
    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.secondaryLabel.isNotBlank()) {
                            Text(
                                text = item.secondaryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formatCompactCount(item.count),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = barColor
                    )

                    if (actionIconIsAllow != null && onQuickAction != null) {
                        IconButton(
                            onClick = { onQuickAction(item.label) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("quick_domain_action_${if (actionIconIsAllow) "allow" else "block"}_$index")
                        ) {
                            Icon(
                                imageVector = if (actionIconIsAllow) Icons.Rounded.AddCircleOutline else Icons.Rounded.Block,
                                contentDescription = if (actionIconIsAllow) {
                                    "Whitelist ${item.label}"
                                } else {
                                    "Blacklist ${item.label}"
                                },
                                tint = if (actionIconIsAllow) EmeraldSignal else CoralBlocked,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.sharePercent.coerceIn(0.03f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(barColor.copy(alpha = 0.75f), barColor)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
