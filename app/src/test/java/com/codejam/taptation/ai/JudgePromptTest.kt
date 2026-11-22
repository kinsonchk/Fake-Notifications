package com.codejam.taptation.ai

import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Strictness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JudgePromptTest {
    @Test
    fun parseReadsTheFirstVerdictWord() {
        assertEquals(Verdict.Important, JudgePrompt.parse("IMPORTANT"))
        assertEquals(Verdict.Bait, JudgePrompt.parse(" bait."))
        assertEquals(Verdict.Unsure, JudgePrompt.parse("Unsure"))
        assertEquals(Verdict.Bait, JudgePrompt.parse("**BAIT** (not important)"))
    }

    @Test
    fun parseFallsBackToUnsure() {
        assertEquals(Verdict.Unsure, JudgePrompt.parse(""))
        assertEquals(Verdict.Unsure, JudgePrompt.parse("I cannot decide"))
    }

    @Test
    fun systemPromptCarriesCriteriaAndStrictness() {
        val config = GameConfig(
            essentialCriteria = "texts from my sister",
            nonEssentialCriteria = "game rewards",
            strictness = Strictness.Strict,
        )
        val system = JudgePrompt.system(config)
        assertTrue(system.contains("texts from my sister"))
        assertTrue(system.contains("game rewards"))
        assertTrue(system.contains("When torn between BAIT and UNSURE, answer BAIT."))
    }

    @Test
    fun blankCriteriaAreLeftOut() {
        assertFalse(JudgePrompt.system(GameConfig()).contains("The user says"))
    }

    @Test
    fun blankTitleAndTextHaveNoContent() {
        assertFalse(JudgePrompt.hasContent(NotificationContext("com.example", title = " ", text = null)))
        assertTrue(JudgePrompt.hasContent(NotificationContext("com.example", title = null, text = "Hi")))
    }

    @Test
    fun userPromptClipsLongFieldsAndSkipsMissingOnes() {
        val context = NotificationContext(packageName = "com.example", title = "Hi", text = "x".repeat(1000))
        val user = JudgePrompt.user(context)
        assertTrue(user.contains("app package: com.example"))
        assertTrue(user.contains("text: " + "x".repeat(JudgePrompt.MAX_FIELD_LENGTH) + "\n"))
        assertFalse(user.contains("channel:"))
        assertFalse(user.contains("buttons:"))
    }
}
