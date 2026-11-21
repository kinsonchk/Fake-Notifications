package com.codejam.taptation.service

import com.codejam.taptation.logic.NotificationContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DebugSimulationTest {
    private val base = NotificationContext(
        packageName = "com.codejam.taptation",
        title = "Hi",
        channelName = "Debug test",
        isConversation = false,
    )

    @Test fun extrasRoundTrip() {
        val simulation = DebugSimulation("com.instagram.android", "Likes", true, 4)
        assertEquals(simulation, DebugSimulation.from(simulation.toExtras()::get))
    }

    @Test fun noOrBlankExtrasMeansNoSimulation() {
        assertNull(DebugSimulation.from { null })
        assertNull(DebugSimulation.from { "  " })
    }

    @Test fun invalidValuesAreIgnored() {
        val extras = mapOf(
            DebugSimulation.EXTRA_PACKAGE to "com.example",
            DebugSimulation.EXTRA_CONVERSATION to "maybe",
            DebugSimulation.EXTRA_APP_CATEGORY to "x",
        )
        assertEquals(DebugSimulation(packageName = "com.example"), DebugSimulation.from(extras::get))
    }

    @Test fun overridesReplaceOnlyWhatIsSet() {
        val applied = DebugSimulation(packageName = "com.discord", isConversation = true).applyTo(base)
        assertEquals("com.discord", applied.packageName)
        assertEquals(true, applied.isConversation)
        assertEquals("Debug test", applied.channelName)
        assertEquals("Hi", applied.title)
    }
}
