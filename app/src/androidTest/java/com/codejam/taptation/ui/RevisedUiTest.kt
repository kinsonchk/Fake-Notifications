package com.codejam.taptation.ui

import android.os.SystemClock
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codejam.taptation.logic.DisableGate
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.settings.DisableConfirmation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RevisedUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun caughtChoiceHasBothDistinctDestinations() {
        var ours = 0
        var original = 0
        compose.setContent { MaterialTheme {
            CatchChoiceDialog(onTaptation = { ours++ }, onOriginal = { original++ })
        } }
        compose.onNodeWithText("Go to Taptation").assertIsDisplayed().performClick()
        compose.onNodeWithText("Go to original app").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, ours); assertEquals(1, original) }
    }

    @Test fun disableRequiresWaitButKeepActiveIsAvailable() {
        var cancelled = false
        compose.setContent { MaterialTheme {
            DisableConfirmation(DisableGate.Challenge("test", SystemClock.elapsedRealtime() + 60_000),
                busy = false, onCancel = { cancelled = true }, onConfirm = { error("Wait was bypassed") })
        } }
        compose.onNodeWithText("Confirm disable").assertIsNotEnabled()
        compose.onNodeWithText("Keep active").performClick()
        compose.runOnIdle { assertEquals(true, cancelled) }
    }

    @Test fun completedWaitAllowsExplicitDurationConfirmation() {
        var duration: Int? = -1
        compose.setContent { MaterialTheme {
            DisableConfirmation(DisableGate.Challenge("ready", 0), busy = false,
                onCancel = {}, onConfirm = { duration = it })
        } }
        compose.onNodeWithText("1 hour").performClick()
        compose.onNodeWithText("Confirm disable").performClick()
        compose.runOnIdle { assertEquals(60, duration) }
    }
}
