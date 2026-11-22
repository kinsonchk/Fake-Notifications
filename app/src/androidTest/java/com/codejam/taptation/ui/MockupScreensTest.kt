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
import com.codejam.taptation.R
import com.codejam.taptation.service.TapListenerService
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises real tab navigation and settings drill-downs, and exports device renders for review. */
class MockupScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    /** The tabs sit behind the one-time tutorial and notification access (see TaptationApp). */
    @Before fun skipOnboarding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        completeTutorial(context)
        // Read the output so the grant has finished before the test renders TaptationApp.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "cmd notification allow_listener ${context.packageName}/${TapListenerService::class.java.name}"))
            .use { it.readBytes() }
    }

    /** The first test after a cold start renders while the game state is still loading. */
    private fun awaitDisplayed(text: String) {
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithText(text).assertIsDisplayed() }.isSuccess }
    }

    private fun text(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test fun lightScreensKeepSettingsAndDebugReachable() {
        compose.activity.setContent { TaptationTheme(darkTheme = false) { TaptationApp(debug = true) } }
        awaitDisplayed("Room to grow.")
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
        awaitDisplayed("Room to grow.")
        capture("home-dark")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithText("Your attention,\nin perspective.").assertIsDisplayed()
        capture("stats-dark")
        compose.activity.setContent { TaptationTheme(darkTheme = false) {
            CatchChoiceDialog(continueExtraDamage = 0, onTaptation = {}, onOriginal = {}, onLater = {})
        } }
        compose.onNodeWithText(text(R.string.caught_our_app)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.caught_original_app)).assertIsDisplayed()
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
