package com.example.data

import kotlin.math.roundToLong

data class NodeTelemetrySummary(
    val device: PiholeDeviceProfile,
    val online: Boolean,
    val blockingEnabled: Boolean = false,
    val totalQueries: Long = 0L,
    val blockedQueries: Long = 0L,
    val percentBlocked: Double = 0.0,
    val errorMessage: String? = null
)

data class AggregatedFleetTelemetry(
    val snapshot: PiholeTelemetrySnapshot?,
    val nodeSummaries: List<NodeTelemetrySummary>,
    val reachableCount: Int,
    val blockingActiveCount: Int,
    val errorMessage: String?
)

/**
 * Pure aggregation utilities for merging live telemetry, DNS query logs, and domain rules
 * across multiple Pi-hole instances in "All Devices" fleet mode.
 */
object FleetTelemetryAggregator {

    fun aggregateTelemetry(
        results: List<Pair<PiholeDeviceProfile, Result<PiholeTelemetrySnapshot>>>
    ): AggregatedFleetTelemetry {
        val nodeSummaries = results.map { (device, result) ->
            result.fold(
                onSuccess = { snap ->
                    NodeTelemetrySummary(
                        device = device,
                        online = true,
                        blockingEnabled = snap.summary.blockingEnabled,
                        totalQueries = snap.summary.totalQueries,
                        blockedQueries = snap.summary.blockedQueries,
                        percentBlocked = snap.summary.percentBlocked
                    )
                },
                onFailure = { err ->
                    NodeTelemetrySummary(
                        device = device,
                        online = false,
                        errorMessage = err.message ?: "Unreachable"
                    )
                }
            )
        }

        val successfulSnapshots = results.mapNotNull { (_, res) -> res.getOrNull() }
        if (successfulSnapshots.isEmpty()) {
            val firstError = nodeSummaries.firstOrNull { !it.online }?.errorMessage
                ?: "Unable to reach any configured Pi-hole device."
            return AggregatedFleetTelemetry(
                snapshot = null,
                nodeSummaries = nodeSummaries,
                reachableCount = 0,
                blockingActiveCount = 0,
                errorMessage = firstError
            )
        }

        val totalQueries = successfulSnapshots.sumOf { it.summary.totalQueries }
        val blockedQueries = successfulSnapshots.sumOf { it.summary.blockedQueries }
        val percentBlocked = if (totalQueries > 0L) {
            (blockedQueries.toDouble() * 100.0) / totalQueries.toDouble()
        } else {
            0.0
        }
        val queriesCached = successfulSnapshots.sumOf { it.summary.queriesCached }
        val queriesForwarded = successfulSnapshots.sumOf { it.summary.queriesForwarded }

        // Redundant Pi-holes typically share adlists & clients; use max/deduplicated estimates
        // to avoid inflating counts by 2x.
        val domainsOnBlocklist = successfulSnapshots.maxOf { it.summary.domainsOnBlocklist }
        val uniqueDomains = successfulSnapshots.maxOf { it.summary.uniqueDomains }

        val mergedTopClients = mergeRankedItems(
            lists = successfulSnapshots.map { it.topClients },
            limit = 10
        )
        val maxReportedClients = successfulSnapshots.maxOf { it.summary.activeClients }
        val activeClients = maxOf(maxReportedClients, mergedTopClients.size.toLong())

        val blockingActiveCount = successfulSnapshots.count { it.summary.blockingEnabled }
        val allBlockingEnabled = blockingActiveCount == successfulSnapshots.size
        val minDisableTimer = successfulSnapshots
            .mapNotNull { it.summary.disableRemainingSeconds }
            .minOrNull()

        val statusText = when {
            allBlockingEnabled -> "enabled"
            blockingActiveCount == 0 -> "disabled"
            else -> "partial"
        }

        val mergedSummary = PiholeSummaryStats(
            totalQueries = totalQueries,
            blockedQueries = blockedQueries,
            percentBlocked = percentBlocked,
            domainsOnBlocklist = domainsOnBlocklist,
            activeClients = activeClients,
            uniqueDomains = uniqueDomains,
            queriesForwarded = queriesForwarded,
            queriesCached = queriesCached,
            blockingEnabled = allBlockingEnabled,
            disableRemainingSeconds = if (allBlockingEnabled) null else minDisableTimer,
            gravityLastUpdatedEpoch = successfulSnapshots.mapNotNull { it.summary.gravityLastUpdatedEpoch }.maxOrNull(),
            statusText = statusText
        )

        val mergedHistory = mergeHistoryBuckets(successfulSnapshots.map { it.history })
        val mergedTopBlocked = mergeRankedItems(successfulSnapshots.map { it.topBlockedDomains }, limit = 10)
        val mergedTopPermitted = mergeRankedItems(successfulSnapshots.map { it.topPermittedDomains }, limit = 10)
        val mergedQueryTypes = mergeQueryTypes(successfulSnapshots)

        return AggregatedFleetTelemetry(
            snapshot = PiholeTelemetrySnapshot(
                summary = mergedSummary,
                history = mergedHistory,
                topBlockedDomains = mergedTopBlocked,
                topPermittedDomains = mergedTopPermitted,
                topClients = mergedTopClients,
                queryTypes = mergedQueryTypes,
                fetchedAtMs = System.currentTimeMillis()
            ),
            nodeSummaries = nodeSummaries,
            reachableCount = successfulSnapshots.size,
            blockingActiveCount = blockingActiveCount,
            errorMessage = null
        )
    }

    fun mergeQueryLogs(
        results: List<Pair<PiholeDeviceProfile, Result<List<DnsQueryLogEntry>>>>,
        limit: Int = 200
    ): Pair<List<DnsQueryLogEntry>, String?> {
        val merged = mutableListOf<DnsQueryLogEntry>()
        var anySuccess = false
        var firstError: String? = null

        results.forEach { (device, result) ->
            result.fold(
                onSuccess = { entries ->
                    anySuccess = true
                    entries.forEach { entry ->
                        merged += entry.copy(
                            id = "${device.id}:${entry.id}",
                            deviceId = device.id,
                            deviceName = device.name
                        )
                    }
                },
                onFailure = { err ->
                    if (firstError == null) {
                        firstError = err.message ?: "Unable to fetch logs from ${device.name}"
                    }
                }
            )
        }

        val sorted = merged
            .sortedByDescending { it.timestampEpoch }
            .take(limit)

        return sorted to if (anySuccess) null else firstError
    }

    fun mergeDomainRules(
        results: List<Pair<PiholeDeviceProfile, Result<List<DomainListRule>>>>
    ): Pair<List<DomainListRule>, String?> {
        val merged = mutableListOf<DomainListRule>()
        var anySuccess = false
        var firstError: String? = null

        results.forEach { (device, result) ->
            result.fold(
                onSuccess = { rules ->
                    anySuccess = true
                    rules.forEach { rule ->
                        merged += rule.copy(
                            id = "${device.id}:${rule.id}",
                            deviceId = device.id,
                            deviceName = device.name
                        )
                    }
                },
                onFailure = { err ->
                    if (firstError == null) {
                        firstError = err.message ?: "Unable to fetch domain rules from ${device.name}"
                    }
                }
            )
        }

        val sorted = merged.sortedWith(
            compareByDescending<DomainListRule> { it.dateAddedEpoch }
                .thenBy { it.domain.lowercase() }
                .thenBy { it.deviceName.lowercase() }
        )

        return sorted to if (anySuccess) null else firstError
    }

    private fun mergeHistoryBuckets(allBuckets: List<List<HistoryBucket>>): List<HistoryBucket> {
        val bucketMap = linkedMapOf<Long, Pair<Long, Long>>()
        // Align timestamps to 10-minute slots (600s) so slightly offset Pi-hole clocks combine cleanly
        allBuckets.forEach { list ->
            list.forEach { bucket ->
                val slot = (bucket.timestampEpoch / 600L) * 600L
                val existing = bucketMap[slot]
                if (existing == null) {
                    bucketMap[slot] = bucket.totalQueries to bucket.blockedQueries
                } else {
                    bucketMap[slot] = (existing.first + bucket.totalQueries) to (existing.second + bucket.blockedQueries)
                }
            }
        }
        return bucketMap.entries
            .sortedBy { it.key }
            .map { (ts, pair) ->
                HistoryBucket(
                    timestampEpoch = ts,
                    totalQueries = pair.first,
                    blockedQueries = pair.second
                )
            }
            .takeLast(144)
    }

    private fun mergeRankedItems(
        lists: List<List<RankedMetricItem>>,
        limit: Int
    ): List<RankedMetricItem> {
        data class Acc(var label: String, var count: Long, var secondary: String)

        val accMap = linkedMapOf<String, Acc>()
        lists.forEach { items ->
            items.forEach { item ->
                val key = item.label.trim().lowercase()
                if (key.isEmpty()) return@forEach
                val existing = accMap[key]
                if (existing == null) {
                    accMap[key] = Acc(item.label.trim(), item.count, item.secondaryLabel)
                } else {
                    existing.count += item.count
                    if (existing.secondary.isBlank() && item.secondaryLabel.isNotBlank()) {
                        existing.secondary = item.secondaryLabel
                    }
                }
            }
        }

        val top = accMap.values
            .sortedByDescending { it.count }
            .take(limit)
        val total = top.sumOf { it.count }.coerceAtLeast(1L)
        return top.map { acc ->
            RankedMetricItem(
                label = acc.label,
                count = acc.count,
                sharePercent = (acc.count.toFloat() / total.toFloat()) * 100f,
                secondaryLabel = acc.secondary
            )
        }
    }

    private fun mergeQueryTypes(snapshots: List<PiholeTelemetrySnapshot>): List<QueryTypeMetric> {
        val weightedCounts = linkedMapOf<String, Double>()
        snapshots.forEach { snap ->
            val nodeTotal = snap.summary.totalQueries.coerceAtLeast(1L).toDouble()
            snap.queryTypes.forEach { metric ->
                val key = metric.type.trim().uppercase()
                if (key.isEmpty()) return@forEach
                val estimatedCount = if (metric.count > 0L) {
                    metric.count.toDouble()
                } else {
                    (metric.percentage.toDouble() / 100.0) * nodeTotal
                }
                weightedCounts[key] = (weightedCounts[key] ?: 0.0) + estimatedCount
            }
        }

        val grandTotal = weightedCounts.values.sum().coerceAtLeast(1.0)
        return weightedCounts.entries
            .sortedByDescending { it.value }
            .map { (type, count) ->
                QueryTypeMetric(
                    type = type,
                    percentage = ((count / grandTotal) * 100.0).toFloat(),
                    count = count.roundToLong()
                )
            }
    }
}
