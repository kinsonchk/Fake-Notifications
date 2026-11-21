package com.codejam.taptation.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
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
        compose.onNodeWithText("Include important / unsure").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Hard").assertIsDisplayed()
        capture("settings-light")
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Notification rules"))
        compose.onNodeWithText("Notification rules").performClick()
        compose.onNodeWithText("Your apps").assertIsDisplayed()
        capture("notification-rules-light")
        compose.onNodeWithText("←  Back to settings").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Caught feedback"))
        compose.onNodeWithText("Caught feedback").performClick()
        compose.onNodeWithText("←  Back to settings").performClick()
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
            CatchChoiceDialog(onTaptation = {}, onOriginal = {})
        } }
        compose.onNodeWithText("Go to Taptation").assertIsDisplayed()
        compose.onNodeWithText("Go to original app").assertIsDisplayed()
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
