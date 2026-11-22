package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTextTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun categoryCodesBecomeReadableNames() {
        assertEquals("Social", StatsText.categoryName("social"))
        assertEquals("Promotions", StatsText.categoryName("promo"))
        assertEquals("Messages", StatsText.categoryName("msg"))
        assertEquals("Messages", StatsText.categoryName(" MESSAGE "))
        assertEquals("Calls", StatsText.categoryName("missed_call"))
        assertEquals("No category", StatsText.categoryName(null))
        assertEquals("No category", StatsText.categoryName("unknown"))
        assertEquals("Custom", StatsText.categoryName("custom"))
    }

    @Test fun ratioKeepsTheTapsOverDismissalsMathButReadsAsWords() {
        assertEquals("2 opened for every 1 swiped away", StatsText.openedPerSwiped(4, 2))
        assertEquals("2.5 opened for every 1 swiped away", StatsText.openedPerSwiped(5, 2))
        assertEquals("0 opened for every 1 swiped away", StatsText.openedPerSwiped(0, 3))
        assertNull(StatsText.openedPerSwiped(5, 0))
        assertEquals("2 : 1", StatsText.ratioHeadline(4, 2))
        assertEquals("0.5 : 1", StatsText.ratioHeadline(1, 2))
        assertEquals("—", StatsText.ratioHeadline(3, 0))
    }

    private fun summary(vararg events: StatsEvent) = StatsCalculator.summarize(events.toList(), 0, 3 * day, true, ZoneOffset.UTC)
    private fun tap(at: Long, caught: Boolean = false) = StatsEvent(at, "p", StatsEventType.TAP, 1, Classification.Bait, caught)
    private fun swipe(at: Long) = StatsEvent(at, "p", StatsEventType.DISMISS, 1, Classification.Bait)

    @Test fun emptyPeriodIsNotTheSameAsAQuietOne() {
        assertEquals(StatsText.Emptiness.NoActivity, StatsText.emptiness(summary()))
        assertEquals(StatsText.Emptiness.NoCatches, StatsText.emptiness(summary(tap(10), swipe(20))))
        assertEquals(StatsText.Emptiness.NoCatches, StatsText.emptiness(summary(swipe(20))))
        assertEquals(StatsText.Emptiness.HasCatches, StatsText.emptiness(summary(tap(10, caught = true))))
    }

    @Test fun dailyBarsPreserveZeroCatchDays() {
        val s = summary(tap(10, caught = true), tap(20), tap(day + 5), swipe(day + 6))
        assertEquals(listOf(1, 0, 0), s.dailyCatches.map { it.count })
        assertEquals(listOf("1970-01-01", "1970-01-02", "1970-01-03"), s.dailyCatches.map { it.date })
    }
}
