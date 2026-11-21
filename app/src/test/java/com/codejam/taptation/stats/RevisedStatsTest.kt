package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class RevisedStatsTest {
    @Test fun ratiosCategoriesAndZeroCatchDaysUseExplicitDenominators() {
        val day = 86_400_000L
        val events = listOf(
            StatsEvent(1000, "x", StatsEventType.TAP, 0, Classification.Bait, true, "social"),
            StatsEvent(2000, "x", StatsEventType.TAP, 0, Classification.Bait, false, "social"),
            StatsEvent(3000, "x", StatsEventType.DISMISS, 0, Classification.Bait, false, "social"),
            StatsEvent(day + 1000, "m", StatsEventType.DISMISS, 0, Classification.Bait, false, "email"),
            StatsEvent(day + 2000, "m", StatsEventType.DISMISS, 0, Classification.Important, false, "email"),
        )
        val result = StatsCalculator.summarize(events, 0, 7 * day, false, ZoneId.of("UTC"))
        assertEquals(100.0, result.tapToDismissPercent!!, 0.001)
        assertEquals(50.0, result.caughtPerTapPercent!!, 0.001)
        assertEquals(1.0 / 7, result.averageCatchesPerDay, 0.001)
        assertEquals(7, result.dailyCatches.size)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 0), result.dailyCatches.map { it.count })
        assertEquals(CategoryStats("social", 2, 1, 1), result.categories.first { it.category == "social" })
        assertEquals(CategoryStats("email", 0, 1, 0), result.categories.first { it.category == "email" })
    }

    @Test fun zeroDenominatorsAreNotMisreportedAsZeroPercent() {
        val result = StatsCalculator.summarize(emptyList(), 0, 86_400_000, false, ZoneId.of("UTC"))
        assertNull(result.tapToDismissPercent)
        assertNull(result.caughtPerTapPercent)
        assertEquals(0.0, result.averageCatchesPerDay, 0.0)
        assertEquals(1, result.dailyCatches.size)
    }
}
