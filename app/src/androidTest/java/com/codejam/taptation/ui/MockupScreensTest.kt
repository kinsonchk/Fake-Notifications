package com.codejam.taptation.ui

import android.graphics.Bitmap
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.home.GrassMeadow
import com.codejam.taptation.ui.theme.TaptationTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test

/** Real navigation, app-rule persistence and Android screenshots; no sample app list. */
class MockupScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun lightScreensKeepRulesAndDebugReachable() {
        val app = compose.activity.application as TaptationApplication
        compose.waitUntil(10_000) { app.game.state.value != null }
        val previous = app.game.state.value!!.config
        val installed = compose.activity.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }.filter { it.packageName != compose.activity.packageName }
            .distinctBy { it.packageName }.sortedBy { it.loadLabel(compose.activity.packageManager).toString().lowercase() }
        val target = installed.first()
        val name = target.loadLabel(compose.activity.packageManager).toString()
        try {
            compose.activity.setContent { TaptationTheme(darkTheme = false) { TaptationApp(debug = true) } }
            compose.onNodeWithTag("screen-title").assertTextEquals("Home")
            capture("home-light")
            compose.onNodeWithText("Stats").performClick()
            compose.onNodeWithTag("screen-title").assertTextEquals("Stats")
            capture("stats-light")
            compose.onNodeWithText("Last 30 days").performClick()
            compose.onNodeWithText("All detected").performClick()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Easy").assertIsDisplayed()
            compose.onNodeWithText("Hard").assertIsDisplayed()
            capture("settings-light")
            compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Notification rules"))
            compose.onNodeWithText("Notification rules").performClick()
            compose.onNodeWithTag("screen-title").assertTextEquals("Notification rules")
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("$name notification rule").fetchSemanticsNodes().isNotEmpty() }
            capture("app-rules-light")
            compose.onNodeWithContentDescription("$name notification rule").performClick()
            compose.onAllNodesWithText("Always important").onLast().performClick()
            compose.waitUntil(5_000) { app.game.state.value!!.config.ruleFor(target.packageName) == AppRule.AlwaysImportant }
            assertEquals(AppRule.AlwaysImportant, app.game.state.value!!.config.ruleFor(target.packageName))
            compose.onNodeWithTag("tab-content").performScrollToNode(hasText("How should we judge the rest?"))
            compose.onNodeWithText("What makes a message essential for you?").assertExists()
            capture("rule-preferences-light")
            compose.onNodeWithTag("tab-content").performScrollToNode(hasText("How strict should the AI be?"))
            capture("rule-strictness-light")
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Debug").performClick()
            compose.onNodeWithText("System").assertIsDisplayed()
            capture("debug-light")
        } finally { app.game.updateConfig { previous }.get(5, TimeUnit.SECONDS) }
    }

    @Test fun darkScreensAndCaughtChoicesRender() {
        compose.activity.setContent { TaptationTheme(darkTheme = true) { TaptationApp(debug = true) } }
        compose.onNodeWithTag("screen-title").assertTextEquals("Home")
        capture("home-dark")
        compose.onNodeWithText("Stats").performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Stats")
        capture("stats-dark")
        var postponed = false
        compose.activity.setContent { TaptationTheme(darkTheme = false) {
            CatchChoiceDialog(CatchDetails("com.instagram.android", 90, 80, 10, "app_always_bait", null), "Instagram",
                onTaptation = {}, onOriginal = {}, onCorrect = {}, continueExtraDamage = 5, onLater = { postponed = true })
        } }
        compose.onNodeWithText("Go to Taptation").assertIsDisplayed()
        compose.onNodeWithText("Continue to Instagram · −5 health").assertIsDisplayed()
        capture("caught-light")
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertEquals(true, postponed) }
    }

    @Test fun meadowRepresentsHealthWithoutChangingIt() {
        for (health in listOf(0, 50, 100)) {
            compose.activity.setContent { TaptationTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(24.dp)) { GrassMeadow(health) }
                }
            } }
            compose.onNodeWithContentDescription("Meadow health $health out of 100").assertExists()
            compose.onNodeWithContentDescription("Meadow health $health out of 100").performTouchInput { swipeLeft() }
            compose.onNodeWithContentDescription("Meadow health $health out of 100").assertExists()
            capture("grass-$health")
        }
    }

    private fun capture(name: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Loading", substring = true)).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "ui-previews").apply { mkdirs() }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
