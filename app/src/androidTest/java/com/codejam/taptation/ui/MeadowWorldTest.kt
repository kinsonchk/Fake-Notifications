package com.codejam.taptation.ui

import androidx.compose.ui.test.onNodeWithContentDescription
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.codejam.taptation.MainActivity
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.meadow.*
import com.codejam.taptation.ui.home.*
import com.codejam.taptation.ui.theme.TaptationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MeadowWorldTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private var world by mutableStateOf(MeadowState(healthyMillis=360*MeadowState.MINUTE,checkpointFull=true,checkpointMonitoring=true))

    private fun show(health:Int=100,dark:Boolean=false){compose.activity.setContent{
        TaptationTheme(darkTheme=dark){Surface(color=MaterialTheme.colorScheme.background){
            LazyColumn(Modifier.fillMaxSize().testTag("world-scroll"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                item{LivingMeadow(health,world){world=world.copy(discoveries=world.discoveries+it)}}
                item{MeadowGrowthCard(world)}
            }
        }}
    };compose.runOnIdle {
        androidx.core.view.WindowCompat.getInsetsController(compose.activity.window,compose.activity.window.decorView).apply {
            isAppearanceLightStatusBars=!dark
            isAppearanceLightNavigationBars=!dark
        }
    }}

    @Test fun brushingAndCircleGesturesRevealDiscoveriesWithoutChangingHealth(){
        val app=compose.activity.application as TaptationApplication
        compose.waitUntil(10_000){app.game.state.value!=null}
        val health=app.game.state.value!!.health
        show()
        compose.onNodeWithContentDescription("Meadow health 100 out of 100", substring = true).performTouchInput{
            down(Offset(width*.1f,height*.82f));moveTo(Offset(width*.24f,height*.82f),250);up()
        }
        compose.waitUntil(5_000){MeadowDiscovery.Hedgehog in world.discoveries}
        compose.onNodeWithContentDescription("Meadow health 100 out of 100", substring = true).performTouchInput{
            val cx=width*.5f;val cy=height*.48f;val radius=width/7f
            down(Offset(cx+radius,cy))
            for(i in 1..48){val a=i*2*PI/48;moveTo(Offset(cx+cos(a).toFloat()*radius,cy+sin(a).toFloat()*radius),16)}
            up()
        }
        compose.onNodeWithText("A little whirlwind. The butterfly is riding it.").assertExists()
        assertEquals(health,app.game.state.value!!.health)
        capture("meadow-gesture")
    }

    @Test fun undergroundAndCollectionWorkWithAccessibleAlternatives(){
        show()
        compose.onNodeWithText("Explore").performClick()
        compose.onNodeWithText("Explore").performClick()
        compose.onNodeWithContentDescription("Discoveries 8 of 8").performClick()
        compose.onNodeWithText("Your discoveries stay, even after a catch.").assertIsDisplayed()
        compose.onNodeWithTag("meadow-collection").performScrollToNode(hasText("Young oak"))
        compose.onNodeWithText("Young oak").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Below ground").performClick()
        compose.onNodeWithContentDescription("Underground:", substring = true).assertExists()
        compose.onNodeWithContentDescription("Underground:", substring = true).performTouchInput{click(Offset(width*.15f,height*.7f))}
        compose.onNodeWithText("The mushrooms answer with a soft glow.").assertExists()
        capture("meadow-underground")
    }

    @Test fun earnedLandmarksRenderAtLowHealthInBothThemes(){
        world=world.copy(discoveries=MeadowDiscovery.entries.toSet())
        for(dark in listOf(false,true))for(health in listOf(0,45,100)){
            show(health,dark)
            compose.onNodeWithContentDescription("Meadow health $health out of 100", substring = true).assertExists()
            capture("meadow-${if(dark)"dark" else "light"}-$health")
        }
    }

    @Test fun debugPreviewDoesNotWriteRealHealthOrDiscoveries(){
        val app=compose.activity.application as TaptationApplication
        compose.waitUntil(10_000){app.game.state.value!=null&&app.meadow.state.value!=null}
        val health=app.game.state.value!!.health;val discoveries=app.meadow.state.value!!.discoveries
        compose.activity.setContent{TaptationTheme{AppTabs(debug=true)}}
        compose.onNodeWithText("Debug").performClick()
        compose.onNodeWithTag("tab-content").performScrollToNode(hasText("Open meadow preview"))
        compose.onNodeWithText("Open meadow preview").performClick()
        compose.onNodeWithTag("preview-health").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress){it(0f)}
        compose.onNodeWithTag("meadow-preview").performScrollToNode(hasContentDescription("Meadow health 0 out of 100", substring = true))
        compose.onNodeWithContentDescription("Meadow health 0 out of 100", substring = true).assertExists()
        assertEquals(health,app.game.state.value!!.health);assertEquals(discoveries,app.meadow.state.value!!.discoveries)
    }

    private fun capture(name:String){
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync();android.os.SystemClock.sleep(350)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"ui-previews").apply{mkdirs()}
        val image=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
    }
}
