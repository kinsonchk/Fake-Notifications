package com.codejam.taptation.stats

import com.codejam.taptation.logic.Classification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {
    private fun event(
        type: StatsEventType,
        age: Long?,
        classification: Classification = Classification.Bait,
        at: Long = 100,
        caught: Boolean = false,
    ) = StatsEvent(at, "pkg", type, age, classification, caught)

    @Test fun noEventsGivesNoRateInsteadOfDividingByZero() {
        val s = StatsCalculator.summarize(emptyList(), 0, 1000, includeImportant = false)
        assertEquals(0, s.taps)
        assertNull(s.tapRatePercent)
        assertNull(s.medianTapMillis)
        assertNull(s.averageAllMillis)
    }

    @Test fun tapRateIsTapsOverTapsPlusDismissals() {
        val events = listOf(event(StatsEventType.TAP, 1), event(StatsEventType.DISMISS, 1),
            event(StatsEventType.DISMISS, 1), event(StatsEventType.DISMISS, 1))
        assertEquals(25, StatsCalculator.summarize(events, 0, 1000, false).tapRatePercent)
        val onlyDismissals = events.filter { it.type == StatsEventType.DISMISS }
        assertEquals(0, StatsCalculator.summarize(onlyDismissals, 0, 1000, false).tapRatePercent)
    }

    @Test fun baitOnlyByDefaultImportantOnRequest() {
        val events = listOf(
            event(StatsEventType.TAP, 1, Classification.Bait, caught = true),
            event(StatsEventType.TAP, 1, Classification.Important),
            event(StatsEventType.DISMISS, 1, Classification.Unknown),
        )
        val bait = StatsCalculator.summarize(events, 0, 1000, includeImportant = false)
        assertEquals(1, bait.taps)
        assertEquals(0, bait.dismissals)
        assertEquals(1, bait.caught)
        val all = StatsCalculator.summarize(events, 0, 1000, includeImportant = true)
        assertEquals(2, all.taps)
        assertEquals(1, all.dismissals)
    }

    @Test fun mediansUseRealAgesAveragesCapAtOneHour() {
        val day = 24 * StatsCalculator.AGE_CAP_MILLIS
        val events = listOf(
            event(StatsEventType.DISMISS, 1_000),
            event(StatsEventType.DISMISS, 3_000),
            event(StatsEventType.DISMISS, day), // clear-all after a day away
        )
        val s = StatsCalculator.summarize(events, 0, 1000, false)
        assertEquals(3_000L, s.medianDismissMillis)
        assertEquals((1_000 + 3_000 + StatsCalculator.AGE_CAP_MILLIS) / 3, s.averageDismissMillis)
        assertNull(s.medianTapMillis)
    }

    @Test fun evenCountMedianAveragesMiddleValues() {
        assertEquals(15L, StatsCalculator.median(listOf(20, 10)))
    }

    @Test fun unknownOrNegativeAgesAreSkippedForTimesButStillCounted() {
        val events = listOf(event(StatsEventType.TAP, null), event(StatsEventType.TAP, -5), event(StatsEventType.TAP, 40))
        val s = StatsCalculator.summarize(events, 0, 1000, false)
        assertEquals(3, s.taps)
        assertEquals(40L, s.medianTapMillis)
    }

    @Test fun rangeIsStartInclusiveEndExclusive() {
        val events = listOf(event(StatsEventType.TAP, 1, at = 0), event(StatsEventType.TAP, 1, at = 1000))
        assertEquals(1, StatsCalculator.summarize(events, 0, 1000, false).taps)
    }
}
