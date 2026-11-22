package com.codejam.taptation.ui

import androidx.compose.material3.MaterialTheme
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.common.RadioOption
import com.codejam.taptation.ui.common.SegmentedChoice
import com.codejam.taptation.ui.common.TermListCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The usability redesign's building blocks, tested without needing system permissions. */
class RedesignUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun caughtPopupStatesWhyAndCostAndOffersCorrection() {
        var corrected = 0
        var original = 0
        compose.setContent { MaterialTheme {
            CatchChoiceDialog(CatchDetails("com.insta", 90, 80, 10, "bait_channel", "Suggested for you"), "Instagram",
                onTaptation = {}, onOriginal = { original++ }, onCorrect = { corrected++ })
        } }
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(com.codejam.taptation.R.string.caught_title)).assertIsDisplayed()
        compose.onNodeWithText("Instagram · Suggested for you is set as distracting").assertIsDisplayed()
        compose.onNodeWithContentDescription("Health 80 out of 100, down 10").assertIsDisplayed()
        compose.onNodeWithText("This should be important").performClick()
        compose.onNodeWithText("Continue to Instagram").performClick()
        compose.runOnIdle { assertEquals(1, corrected); assertEquals(1, original) }
    }

    @Test fun caughtPopupWithoutDetailsHidesCorrectionAndKeepsTheTwoDestinations() {
        compose.setContent { MaterialTheme { CatchChoiceDialog(null, null, onTaptation = {}, onOriginal = {}) } }
        compose.onNodeWithText("Go to Taptation").assertIsDisplayed()
        compose.onNodeWithText("Continue to the original app").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTextCount("This should be important") == 0)
    }

    @Test fun removingAPersonNeedsTheRemoveButtonAndCanBeUndone() {
        var names = setOf("Mom", "Alex Chen")
        compose.setContent { MaterialTheme {
            var values by remember { mutableStateOf(names) }
            TermListCard("People who should always get through", "Enter their name.", "Matching names are treated as important.",
                "Name", "No one added yet.", values) { values = it; names = it }
        } }
        compose.onNodeWithText("Mom").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Mom").performClick()
        compose.onNodeWithText("Removed “Mom”").assertIsDisplayed()
        compose.runOnIdle { assertEquals(setOf("Alex Chen"), names) }
        compose.onNodeWithText("Undo").performClick()
        compose.runOnIdle { assertEquals(setOf("Mom", "Alex Chen"), names) }
    }

    @Test fun segmentedChoiceReportsTheTappedOption() {
        var picked = -1
        compose.setContent { MaterialTheme { SegmentedChoice(listOf("Distracting", "All detected"), 0, { picked = it }) } }
        compose.onNodeWithText("All detected").performClick()
        compose.runOnIdle { assertEquals(1, picked) }
    }

    @Test fun radioOptionIsSelectableByItsWholeCard() {
        var clicks = 0
        compose.setContent { MaterialTheme { RadioOption(false, "Always safe", "Never triggers a catch.") { clicks++ } } }
        compose.onNodeWithText("Never triggers a catch.").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
