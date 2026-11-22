package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.*
import org.junit.Assert.*
import org.junit.Test

class DamagePresentationTest {
    @Test fun flashSettlesAtNormalColorWithoutRestoringHealth() {
        assertEquals(0f, damageFlashMix(0f), .001f)
        assertEquals(1f, damageFlashMix(.28f), .001f)
        assertEquals(0f, damageFlashMix(1f), .001f)
        assertEquals(1f, damageDropProgress(1f), .001f)
        assertEquals(0f, damageDropProgress(-1f), .001f)
    }

    @Test fun manualCatchUsesItsOwnHealthRecordInsteadOfAnOldNotification() {
        val state = GameState(health = 70, taps = listOf(
            TapRecord(10, "old.app", Classification.Bait, TapOutcome.Caught, 100, 90, 10, "caught")),
            healthHistory = listOf(HealthRecord(20, 80, 70, "catch")))
        val details = catchDetailsForUi(state, PendingCatch("20-2", null))!!
        assertEquals(80, details.healthBefore)
        assertEquals(70, details.healthAfter)
        assertNull(details.packageName)
        assertNull(catchDetailsForUi(state, PendingCatch("30-3", null)))
        assertEquals(70, state.health)
    }
}
