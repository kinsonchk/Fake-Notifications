package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Pure summary math over stored events, so it is unit-tested without a database. */
object StatsCalculator {
    /** Ages above one hour count as one hour in averages. */
    const val AGE_CAP_MILLIS = 60 * 60 * 1000L

    fun summarize(
        events: List<StatsEvent>,
        fromMillis: Long,
        toMillis: Long,
        includeImportant: Boolean,
    ): StatsSummary {
        val counted = events.filter {
            it.timestampMillis in fromMillis until toMillis &&
                (includeImportant || it.classification == Classification.Bait)
        }
        val taps = counted.filter { it.type == StatsEventType.TAP }
        val dismissals = counted.filter { it.type == StatsEventType.DISMISS }
        val total = taps.size + dismissals.size
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
