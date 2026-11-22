package com.codejam.taptation.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringStatusTest {
    private val now = 1_000_000L

    @Test fun missingAccessIsSetupNeededEvenWhenPaused() {
        assertEquals(MonitoringStatus.SetupNeeded, MonitoringStatus.from(false, false, GameConfig(), now))
        assertEquals(MonitoringStatus.SetupNeeded, MonitoringStatus.from(false, true, GameConfig(active = false), now))
    }

    @Test fun pausedShowsWhenItEnds() {
        val timed = GameConfig(active = false, disabledUntilMillis = now + 60_000)
        assertEquals(MonitoringStatus.Paused(now + 60_000), MonitoringStatus.from(true, true, timed, now))
        assertEquals(MonitoringStatus.Paused(null), MonitoringStatus.from(true, true, GameConfig(active = false), now))
    }

    @Test fun pausedTimerThatEndedMeansMonitoring() {
        val expired = GameConfig(active = false, disabledUntilMillis = now - 1)
        assertEquals(MonitoringStatus.Monitoring(Difficulty.Easy), MonitoringStatus.from(true, true, expired, now))
    }

    @Test fun grantedButNotConnectedIsInterrupted() {
        assertEquals(MonitoringStatus.ConnectionInterrupted, MonitoringStatus.from(true, false, GameConfig(), now))
        assertTrue(!MonitoringStatus.ConnectionInterrupted.isWorking)
    }

    @Test fun onlyAllGreenMeansMonitoringWithTheDifficulty() {
        val status = MonitoringStatus.from(true, true, GameConfig(difficulty = Difficulty.Hard), now)
        assertEquals(MonitoringStatus.Monitoring(Difficulty.Hard), status)
        assertTrue(status.isWorking)
    }
}

class CatchDetailsTest {
    private fun tap(pkg: String, outcome: TapOutcome, before: Int, after: Int, rule: String = "bait_channel") =
        TapRecord(1, pkg, Classification.Bait, outcome, before, after, before - after, "caught", rule = rule)

    @Test fun usesNewestCaughtTapFromThePendingApp() {
        val state = GameState(health = 80, taps = listOf(
            tap("a", TapOutcome.Caught, 100, 90), tap("b", TapOutcome.Caught, 90, 80, "configured_bait"),
            tap("b", TapOutcome.NoPenalty, 80, 80, "bait_category")))
        val details = CatchDetails.from(state, PendingCatch("1", "b"), "Likes")!!
        assertEquals(CatchDetails("b", 90, 80, 10, "configured_bait", "Likes"), details)
    }

    @Test fun fallsBackToNewestCatchOfAnyApp() {
        val state = GameState(taps = listOf(tap("a", TapOutcome.Caught, 100, 90)))
        assertEquals("zzz", CatchDetails.from(state, PendingCatch("1", "zzz"))!!.packageName)
        assertEquals(10, CatchDetails.from(state, PendingCatch("1", "zzz"))!!.damage)
    }

    @Test fun manualCatchUsesHealthHistory() {
        val state = GameState(healthHistory = listOf(HealthRecord(5, 100, 90, "manual")))
        val details = CatchDetails.from(state, PendingCatch("1", null))!!
        assertEquals(90, details.healthAfter)
        assertEquals(10, details.damage)
        assertNull(details.packageName)
    }

    @Test fun noCatchRecordGivesNothing() {
        assertNull(CatchDetails.from(GameState(), PendingCatch("1", "a")))
    }
}
