package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification

enum class StatsEventType { TAP, DISMISS }

/**
 * One notification the user acted on: tapped (opened) or dismissed (swipe / clear all).
 * Holds no title or text. [ageMillis] is time from posting to the action; null when unknown.
 */
data class StatsEvent(
    val timestampMillis: Long,
    val packageName: String,
    val type: StatsEventType,
    val ageMillis: Long?,
    val classification: Classification,
    val caught: Boolean = false,
)

/**
 * Everything a stats screen shows for one period, already computed.
 *
 * @property tapRatePercent taps / (taps + dismissals) as 0-100; null when there were neither.
 * @property medianTapMillis medians use every known age; averages cap each age at
 *   [StatsCalculator.AGE_CAP_MILLIS] so "clear all" after a day away doesn't swamp them.
 * @property includeImportant false: bait only (default); true: bait, important and unsure.
 */
data class StatsSummary(
    val fromMillis: Long,
    val toMillis: Long,
    val includeImportant: Boolean,
    val taps: Int,
    val dismissals: Int,
    val caught: Int,
    val tapRatePercent: Int?,
    val medianTapMillis: Long?,
    val medianDismissMillis: Long?,
    val medianAllMillis: Long?,
    val averageTapMillis: Long?,
    val averageDismissMillis: Long?,
    val averageAllMillis: Long?,
)
