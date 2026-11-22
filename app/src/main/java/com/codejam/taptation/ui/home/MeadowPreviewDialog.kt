package com.codejam.taptation.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codejam.taptation.logic.meadow.*
import com.codejam.taptation.ui.theme.TaptationTheme
import kotlin.math.roundToInt

/** Debug-only toy gallery. No repository, clock advancement, health writes or persisted unlocks. */
@Composable
fun MeadowPreviewDialog(onClose:()->Unit) {
    var health by remember{mutableFloatStateOf(100f)}
    var minutes by remember{mutableFloatStateOf(360f)}
    val initialDark = MaterialTheme.colorScheme.background.luminance() < .5f
    var dark by remember{mutableStateOf(initialDark)}
    var classic by remember{mutableStateOf(false)}
    var clean by remember{mutableFloatStateOf(480f)}
    var discoveries by remember{mutableStateOf(MeadowDiscovery.entries.toSet())}
    val world=MeadowState(healthyMillis=minutes.roundToInt()*MeadowState.MINUTE,
        checkpointFull=health.roundToInt()==100,checkpointMonitoring=true,discoveries=discoveries,
        cleanMillis=clean.roundToInt()*MeadowState.MINUTE)
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val view=LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window,view).apply {
                    isAppearanceLightStatusBars=!dark
                    isAppearanceLightNavigationBars=!dark
                }
            }
        }
        TaptationTheme(darkTheme=dark) { Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().testTag("meadow-preview").windowInsetsPadding(WindowInsets.safeDrawing),
                contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Meadow preview",modifier=Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);TextButton(onClick=onClose){Text("Close")}}}
                item{Text("Preview only. Your game health and earned discoveries do not change.",style=MaterialTheme.typography.bodySmall)}
                item{Text("Preview health: ${health.roundToInt()}");Slider(value=health,onValueChange={health=it},valueRange=0f..100f,modifier=Modifier.testTag("preview-health"))}
                item{Text("Preview growth: ${minutes.roundToInt()} min");Slider(value=minutes,onValueChange={minutes=it},valueRange=0f..360f,steps=11,modifier=Modifier.testTag("preview-growth"))}
                item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Dark preview");Switch(checked=dark,onCheckedChange={dark=it})}}
                item{Text("Preview clean time: ${clean.roundToInt()} min");Slider(value=clean,onValueChange={clean=it},valueRange=0f..540f,modifier=Modifier.testTag("preview-clean"))}
                item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Classic scenery");Switch(checked=classic,onCheckedChange={classic=it})}}
                item{LivingMeadow(health.roundToInt(),world,if(classic)MeadowScenery.Classic else MeadowScenery.Valley){discoveries=discoveries+it}}
                item{MeadowValleyCard(world)}
                item{MeadowGrowthCard(world)}
            }
        } }
    }
}
