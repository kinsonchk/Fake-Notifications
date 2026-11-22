package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Pure summary math over stored events, so it is unit-tested without a database. */
object StatsCalculator {
    /** Ages above one hour count as one hour in averages. */
    const val AGE_CAP_MILLIS = 60 * 60 * 1000L

    fun summarize(
        events: List<StatsEvent>,
        fromMillis: Long,
        toMillis: Long,
        includeImportant: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StatsSummary {
        val counted = events.filter {
            it.timestampMillis in fromMillis until toMillis &&
                (includeImportant || it.classification == Classification.Bait)
        }
        val taps = counted.filter { it.type == StatsEventType.TAP }
        val dismissals = counted.filter { it.type == StatsEventType.DISMISS }
        val total = taps.size + dismissals.size
        require(toMillis > fromMillis)
        val firstDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val lastDay = Instant.ofEpochMilli(toMillis - 1).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(firstDay, lastDay) + 1
        val caughtByDay = taps.filter { it.caught }.groupingBy {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
        }.eachCount()
        return StatsSummary(
            fromMillis = fromMillis,
            toMillis = toMillis,
            includeImportant = includeImportant,
            taps = taps.size,
            dismissals = dismissals.size,
            caught = taps.count { it.caught },
            tapRatePercent = if (total == 0) null else (taps.size * 100.0 / total).roundToInt(),
            medianTapMillis = median(ages(taps)),
            medianDismissMillis = median(ages(dismissals)),
            medianAllMillis = median(ages(counted)),
            averageTapMillis = cappedAverage(ages(taps)),
            averageDismissMillis = cappedAverage(ages(dismissals)),
            averageAllMillis = cappedAverage(ages(counted)),
            tapToDismissPercent = if (dismissals.isEmpty()) null else taps.size * 100.0 / dismissals.size,
            caughtPerTapPercent = if (taps.isEmpty()) null else taps.count { it.caught } * 100.0 / taps.size,
            averageCatchesPerDay = taps.count { it.caught }.toDouble() / days,
            categories = counted.groupBy { it.category?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf(String::isNotBlank) ?: "unknown" }
                .toSortedMap().map { (category, events) -> CategoryStats(category,
                    events.count { it.type == StatsEventType.TAP },
                    events.count { it.type == StatsEventType.DISMISS }, events.count { it.caught }) },
            dailyCatches = List(days.toInt()) { index -> firstDay.plusDays(index.toLong()).let {
                DailyCatches(it.toString(), caughtByDay[it] ?: 0)
            } },
        )
    }

    private fun ages(events: List<StatsEvent>): List<Long> = events.mapNotNull { it.ageMillis?.takeIf { age -> age >= 0 } }

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun cappedAverage(values: List<Long>): Long? =
        values.takeIf { it.isNotEmpty() }?.map { it.coerceAtMost(AGE_CAP_MILLIS) }?.average()?.roundToLong()
}
