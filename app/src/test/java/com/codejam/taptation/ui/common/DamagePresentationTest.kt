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

    /** The details come from the catch itself (recorded at catch time), never from an older tap. */
    @Test fun manualCatchShowsItsOwnCostNotAnOldNotification() {
        val details = CatchDetails.of(PendingCatch("20-2", null, healthBefore = 80, healthAfter = 70))!!
        assertEquals(80, details.healthBefore)
        assertEquals(70, details.healthAfter)
        assertEquals(10, details.damage)
        assertNull(details.packageName)
        // Catches saved before the details were recorded show the generic message.
        assertNull(CatchDetails.of(PendingCatch("30-3", null)))
    }
}
