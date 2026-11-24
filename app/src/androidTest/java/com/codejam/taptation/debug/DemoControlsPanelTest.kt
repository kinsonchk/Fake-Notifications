package com.codejam.taptation.debug

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DemoControlsPanelTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val controls = RecordingDemoControls()

    @Test
    fun demoButtons_callTheDomainControlInterface() {
        composeRule.setContent {
            MaterialTheme { DemoControlsPanel(controls) }
        }

        composeRule.onNodeWithText("Set 0%").performClick()
        composeRule.onNodeWithText("Set 100%").performClick()
        composeRule.onNodeWithTag("health-input").performTextClearance()
        composeRule.onNodeWithTag("health-input").performTextInput("1")
        composeRule.onNodeWithText("Set health").performClick()
        composeRule.onNodeWithTag("damage-input").performTextClearance()
        composeRule.onNodeWithTag("damage-input").performTextInput("25")
        composeRule.onNodeWithText("Set damage").performClick()
        composeRule.onNodeWithText("Default damage").performClick()
        composeRule.onNodeWithTag("lockout-input").performTextClearance()
        composeRule.onNodeWithTag("lockout-input").performTextInput("60")
        composeRule.onNodeWithText("Set lockout").performClick()
        composeRule.onNodeWithText("Default lockout").performClick()
        composeRule.onNodeWithText("Force catch").performClick()
        composeRule.onNodeWithText("Fast-forward 1 clean hour").performClick()
        composeRule.onNodeWithText("Reset demo data").performClick()

        composeRule.onNodeWithText("Reset demo data?").assertIsDisplayed()
        composeRule.onNodeWithText("Reset").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(0, 100), controls.probabilities)
            assertEquals(1, controls.health)
            assertEquals(listOf<Int?>(25, null), controls.damages)
            assertEquals(listOf<Int?>(60, null), controls.lockouts)
            assertTrue(controls.forcedCatch)
            assertEquals(60L, controls.advancedMinutes)
            assertTrue(controls.reset)
        }
    }

    @Test
    fun missingDomainControls_areClearlyMarkedUnavailable() {
        composeRule.setContent {
            MaterialTheme { DemoControlsPanel(controls = null) }
        }

        composeRule.onNodeWithText("Not connected yet. These controls need the game repository.")
            .assertIsDisplayed()
    }
}

private class RecordingDemoControls : DemoControls {
    override val state = MutableStateFlow(DemoControlState(health = 10, catchProbabilityPercent = 5, catches = 0))
    val probabilities = mutableListOf<Int>()
    var health: Int? = null
    var forcedCatch = false
    var advancedMinutes: Long? = null
    var reset = false
    val damages = mutableListOf<Int?>()
    val lockouts = mutableListOf<Int?>()

    override fun setCatchProbability(percent: Int) { probabilities += percent }
    override fun setHealth(value: Int) { health = value }
    override fun forceCatch() { forcedCatch = true }
    override fun advanceCleanTime(minutes: Long) { advancedMinutes = minutes }
    override fun setRecoveryIntervalForDebug(minutes: Int?) { }
    override fun setCatchDamage(damage: Int?) { damages += damage }
    override fun setLockoutSeconds(seconds: Int?) { lockouts += seconds }
    override fun resetDemoData() { reset = true }
}

