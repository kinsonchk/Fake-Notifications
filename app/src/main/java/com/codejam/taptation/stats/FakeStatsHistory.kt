package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import kotlin.random.Random

/**
 * Debug-only fake history: a one-day hackathon build can't produce weeks of real data.
 * Deterministic for a given [random] seed.
 */
object FakeStatsHistory {
    private val packages = listOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.discord",
        "com.google.android.gm", "com.whatsapp", "com.google.android.youtube",
    )

    fun generate(nowMillis: Long, days: Int, random: Random = Random(42)): List<StatsEvent> =
        (0 until days).flatMap { day ->
            val dayStart = nowMillis - (day + 1) * StatsRepository.DAY_MILLIS
            List(random.nextInt(10, 31)) {
                val classification = when (random.nextInt(10)) {
                    in 0..6 -> Classification.Bait
                    in 7..8 -> Classification.Important
                    else -> Classification.Unknown
                }
                val tapped = random.nextInt(100) < 40
                // Mostly seconds to minutes, sometimes hours (time away from the phone).
                val age = when (random.nextInt(10)) {
                    in 0..5 -> random.nextLong(2_000, 120_000)
                    in 6..8 -> random.nextLong(120_000, 1_800_000)
                    else -> random.nextLong(1_800_000, 6 * 3_600_000L)
                }
                StatsEvent(
                    timestampMillis = dayStart + random.nextLong(StatsRepository.DAY_MILLIS),
                    packageName = packages.random(random),
                    type = if (tapped) StatsEventType.TAP else StatsEventType.DISMISS,
                    ageMillis = age,
                    classification = classification,
                    caught = tapped && classification == Classification.Bait && random.nextInt(1000) < 75,
                )
            }
        }.sortedBy { it.timestampMillis }
}
