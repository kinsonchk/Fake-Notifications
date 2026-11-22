package com.codejam.taptation.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.theme.TaptationTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Exercises real tab navigation and settings drill-downs, and exports device renders for review. */
class MockupScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightScreensKeepSettingsAndDebugReachable() {
        compose.activity.setContent { TaptationTheme(darkTheme = false) { TaptationApp(debug = true) } }
        compose.onNodeWithText("Room to grow.").assertIsDisplayed()
        capture("home-light")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Your attention,\nin perspective.").assertIsDisplayed()
        capture("stats-light")
        compose.onNodeWithText("Last 30 days").performClick()
        compose.onNodeWithText("All detected").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Hard").assertIsDisplayed()
        capture("settings-light")
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Always-safe apps"))
        compose.onNodeWithText("Always-safe apps").performClick()
        compose.onNodeWithText("Always-safe apps.").assertIsDisplayed()
        capture("safe-apps-light")
        compose.onNodeWithText("←  Settings").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Notification rules"))
        compose.onNodeWithText("Notification rules").performClick()
        compose.onNodeWithText("Notification rules.").assertIsDisplayed()
        compose.onNodeWithText("Add a rule").performClick()
        compose.onNodeWithText("Choose an app.").assertIsDisplayed()
        capture("rule-choose-app-light")
        compose.onNodeWithText("←  Cancel").performClick()
        compose.onNodeWithText("←  Settings").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("People who always get through"))
        compose.onNodeWithText("People who always get through").performClick()
        compose.onNodeWithText("Always get\nthrough.").assertIsDisplayed()
        compose.onNodeWithText("←  Settings").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Catch reminders"))
        compose.onNodeWithText("Catch reminders").performClick()
        compose.onNodeWithText("Catch reminders.").assertIsDisplayed()
        capture("reminders-light")
        compose.onNodeWithText("←  Settings").performClick()
        compose.onNodeWithText("Debug").performClick()
        compose.onNodeWithText("System").assertIsDisplayed()
        capture("debug-light")
    }

    @Test fun darkScreensAndCaughtChoicesRender() {
        compose.activity.setContent { TaptationTheme(darkTheme = true) { TaptationApp(debug = true) } }
        compose.onNodeWithText("Room to grow.").assertIsDisplayed()
        capture("home-dark")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Your attention,\nin perspective.").assertIsDisplayed()
        capture("stats-dark")
        compose.activity.setContent { TaptationTheme(darkTheme = false) {
            CatchChoiceDialog(CatchDetails(null, 90, 80, 10, "bait_channel", null), null, onTaptation = {}, onOriginal = {})
        } }
        compose.onNodeWithText("Go to Taptation").assertIsDisplayed()
        compose.onNodeWithText("Continue to the app").assertIsDisplayed()
        capture("caught-light")
    }

    private fun capture(name: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Loading", substring = true)).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
