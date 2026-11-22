package com.codejam.taptation.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.Appearance
import com.codejam.taptation.logic.AppearanceRepository
import com.codejam.taptation.ui.theme.Cream
import com.codejam.taptation.ui.theme.Night
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppearanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun darkDefaultAndLightChoicePersistWithoutResettingNavigation() {
        val app=compose.activity.application as TaptationApplication
        compose.waitUntil(10_000){app.appearance.mode.value!=null}
        val previous=app.appearance.mode.value!!
        try {
            completeTutorial(app)
            app.appearance.setAppearance(Appearance.Dark)
            compose.waitUntil(5_000){app.appearance.mode.value==Appearance.Dark}
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("screen-title").fetchSemanticsNodes().isNotEmpty()}
            assertBackground(Night.toArgb())
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithTag("tab-content").performScrollToNode(hasContentDescription("Light theme"))
            compose.onNodeWithContentDescription("Light theme").assertIsDisplayed()
            compose.onNodeWithContentDescription("Dark theme").assertIsSelected()
            capture("appearance-dark")
            compose.onNodeWithContentDescription("Light theme").performClick()
            compose.waitUntil(5_000){app.appearance.mode.value==Appearance.Light}
            compose.onNodeWithContentDescription("Light theme").assertIsDisplayed().assertIsSelected()
            assertBackground(Cream.toArgb())
            capture("appearance-light")
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("tab-content").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(Appearance.Light,app.appearance.mode.value)
            assertBackground(Cream.toArgb())
        } finally {
            app.appearance.setAppearance(previous)
            compose.waitUntil(5_000){app.appearance.mode.value==previous}
        }
    }

    @Test fun missingPreferenceDefaultsToDarkAndLightSurvivesRepositoryReload()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="appearance-test-${System.nanoTime()}"
        var job=SupervisorJob()
        try {
            var repo=AppearanceRepository(context,CoroutineScope(job+Dispatchers.Default),name)
            withTimeout(5_000){while(repo.mode.value==null)delay(20)}
            assertEquals(Appearance.Dark,repo.mode.value)
            repo.setAppearance(Appearance.Light)
            withTimeout(5_000){while(repo.mode.value!=Appearance.Light)delay(20)}
            job.cancelAndJoin();job=SupervisorJob()
            repo=AppearanceRepository(context,CoroutineScope(job+Dispatchers.Default),name)
            withTimeout(5_000){while(repo.mode.value==null)delay(20)}
            assertEquals(Appearance.Light,repo.mode.value)
        } finally {job.cancelAndJoin();context.getSharedPreferences(name,0).edit().clear().commit()}
    }

    private fun assertBackground(argb:Int) {
        compose.waitForIdle()
        assertEquals(argb,compose.onNodeWithTag("tab-content").captureToImage().toPixelMap()[0,0].toArgb())
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(250)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-previews").apply{mkdirs()}
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
