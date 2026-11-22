package com.codejam.taptation.ui

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
import com.codejam.taptation.R
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.meadow.MeadowCameo
import com.codejam.taptation.ui.common.CatchChoiceDialog
import com.codejam.taptation.ui.theme.TaptationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class FishingCatchUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val fixture=CatchDetails("com.instagram.android",90,80,10,"app_always_bait",null)
    private fun text(id:Int)=compose.activity.getString(id)

    @Test fun destinationsAndCorrectionWorkDuringTheAnimationWithoutApplyingHealth(){
        val app=compose.activity.application as TaptationApplication
        compose.waitUntil(10_000){app.game.state.value!=null}
        val before=app.game.state.value!!.health
        var home=0;var original=0;var correction=0;var later=0
        compose.activity.setContent{TaptationTheme{
            CatchChoiceDialog(fixture,"Instagram",onTaptation={home++},onOriginal={original++},onCorrect={correction++},
                onLater={later++},continueExtraDamage=5,cameo=MeadowCameo.Fish,sceneKey="animated-test")
        }}
        compose.onNodeWithText(text(R.string.caught_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.caught_our_app)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.caught_original_app_with_penalty,"Instagram",5)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.caught_correct)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.caught_later)).performScrollTo().performClick()
        assertEquals(listOf(1,1,1,1),listOf(home,original,correction,later));assertEquals(before,app.game.state.value!!.health)
    }

    @Test fun everyCameoRendersInBothThemesAndPostponedPromptsShowFinalHealth(){
        for(dark in listOf(false,true))for(scene in MeadowCameo.entries){
            compose.activity.setContent{TaptationTheme(darkTheme=dark){
                CatchChoiceDialog(fixture,"Instagram",onTaptation={},onOriginal={},continueExtraDamage=5,
                    animateDamage=false,cameo=scene,sceneKey="${scene.name}-$dark",onCorrect={})
            }}
            compose.onNodeWithTag("caught-scene-${scene.name}").assertIsDisplayed().performTouchInput{click()}
            compose.onNodeWithContentDescription("Health 80 out of 100, down 10").assertExists()
            compose.onNodeWithText(text(R.string.caught_our_app)).assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.caught_original_app_with_penalty,"Instagram",5)).assertIsDisplayed()
            capture("caught-${scene.name.lowercase()}-${if(dark)"dark" else "light"}")
        }
    }

    @Test fun isolatedDebugPreviewNeverChangesGameHealthOrUnlocks(){
        val app=compose.activity.application as TaptationApplication
        compose.waitUntil(10_000){app.game.state.value!=null&&app.meadow.state.value!=null}
        val health=app.game.state.value!!.health;val world=app.meadow.state.value!!.discoveries
        compose.activity.setContent{TaptationTheme{AppTabs(debug=true)}}
        compose.onNodeWithText("Debug").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Preview caught animations"))
        compose.onNodeWithText("Preview caught animations").performClick()
        compose.onNodeWithText("Squirrel").performClick()
        compose.onNodeWithText("Play catch preview").performClick()
        compose.onNodeWithTag("caught-scene-Squirrel").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.caught_our_app)).performClick()
        compose.onNodeWithText("Preview: Go to Taptation").assertExists()
        assertEquals(health,app.game.state.value!!.health);assertEquals(world,app.meadow.state.value!!.discoveries)
    }

    private fun capture(name:String){compose.waitForIdle();val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(250)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-previews").apply{mkdirs()}
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
