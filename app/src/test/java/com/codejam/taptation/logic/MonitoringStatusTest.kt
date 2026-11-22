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
    // Catch details are recorded on the PendingCatch at catch time (GameEngineTest covers the engine).
    @Test fun catchDetailsComeFromThePendingCatchItself() {
        val pending = PendingCatch("1", "b", 5, healthBefore = 90, healthAfter = 80, rule = "configured_bait", channel = "Likes")
        assertEquals(CatchDetails("b", 90, 80, 10, "configured_bait", "Likes"), CatchDetails.of(pending))
    }

    @Test fun catchesSavedWithoutDetailsGiveNothing() {
        assertNull(CatchDetails.of(PendingCatch("1", "a")))
    }
}
