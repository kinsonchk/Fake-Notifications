package com.codejam.taptation.ai

import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClefQuestionTest {
    @Test
    fun strictnessMovesTheBaitThreshold() {
        assertEquals(Verdict.Unsure, ClefQuestion.verdict(0.6, Strictness.Relaxed))
        assertEquals(Verdict.Unsure, ClefQuestion.verdict(0.6, Strictness.Balanced))
        assertEquals(Verdict.Bait, ClefQuestion.verdict(0.6, Strictness.Strict))
        assertEquals(Verdict.Bait, ClefQuestion.verdict(0.9, Strictness.Relaxed))
    }

    @Test
    fun lowBaitProbabilityIsImportant() {
        assertEquals(Verdict.Important, ClefQuestion.verdict(0.1, Strictness.Strict))
        assertEquals(Verdict.Important, ClefQuestion.verdict(0.4, Strictness.Balanced))
        assertEquals(Verdict.Unsure, ClefQuestion.verdict(0.41, Strictness.Balanced))
    }

    @Test
    fun userCriteriaExtendTheOptions() {
        val criteria = ClefQuestion.criteria(GameConfig(essentialCriteria = "my sister", nonEssentialCriteria = "game rewards"))
        assertTrue(criteria.getValue(ClefQuestion.IMPORTANT).endsWith("The user says these are essential: my sister"))
        assertTrue(criteria.getValue(ClefQuestion.BAIT).endsWith("The user says these are not essential: game rewards"))
        assertFalse(ClefQuestion.criteria(GameConfig()).values.any { it.contains("The user says") })
    }

    @Test
    fun stateSkipsMissingFields() {
        val state = ClefQuestion.state(NotificationContext("com.example", title = "Hi", text = null))
        assertEquals(setOf("app_package", "title", "text"), state.keys)
        assertEquals("", state["text"])
    }
}
