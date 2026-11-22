package com.codejam.taptation.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.meadow.*
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.rememberMotionEnabled
import com.codejam.taptation.ui.theme.*

/** Public Home has no time-skip or unlock switches. The debug preview is isolated from this store. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LivingMeadow(health: Int, world: MeadowState, onDiscover: (MeadowDiscovery) -> Unit) {
    val effects = remember { MeadowEffects() }
    var underground by rememberSaveable { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(320f) }
    // A newly earned acorn gets one visit; returning later can replay it without awarding anything.
    var seenAcorn by rememberSaveable { mutableIntStateOf(world.acornStage) }
    LaunchedEffect(world.acornStage) {
        if (world.acornStage > seenAcorn) { effects.squirrel(); seenAcorn = world.acornStage }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            SideEffect { width = maxWidth.value }
            MeadowCanvas(health = health, world = world, effects = effects,
                onDiscover = onDiscover, onOpenUnderground = { underground = true })
        }
        TextButton(onClick = { underground = !underground }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (underground) "Back to the surface ↑" else "Below ground ↓")
        }
        AnimatedVisibility(underground) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UndergroundMeadow(world, effects)
                Text("Tap a mushroom, the beetle, or the busy ant.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MeadowAction("Whirlwind") { effects.whirlwind(Offset(width * .5f, 110f)) }
            MeadowAction("Rain", effects::rain)
            MeadowAction("Explore") {
                if (underground) {
                    effects.glowUntil = effects.time + 5; effects.beetleUntil = effects.time + 4
                    effects.antParcel = (effects.antParcel + 1) % 3; effects.say("The little world under your meadow wakes up.")
                } else {
                    val next = MeadowDiscovery.entries.firstOrNull { it !in world.discoveries }
                    if (next != null) { onDiscover(next); effects.say(if(next == MeadowDiscovery.Hedgehog) "You found a sleeping hedgehog." else "A tiny pond was hiding in the grass.") }
                    else { underground = true; effects.say("There is more beneath the grass.") }
                }
            }
            if (world.acornStage > 0) MeadowAction("Call squirrel", effects::squirrel)
            TextButton(onClick = { collection = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Discoveries ${entries(world).count { it.found }} / 8") }
        }
        Text(effects.note, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (collection) ModalBottomSheet(onDismissRequest = { collection = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text("Discoveries", style = MaterialTheme.typography.headlineSmall)
            Text("Your discoveries stay, even after a catch.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(vertical = 12.dp).testTag("meadow-collection")) {
                items(entries(world), key = { it.name }) { entry ->
                    ListItem(headlineContent = { Text(entry.name) }, supportingContent = {
                        Text(if(entry.found)entry.description else entry.unlock)
                    }, trailingContent = { Text(if(entry.found)"Found" else "Locked", style = MaterialTheme.typography.labelMedium) })
                }
            }
        }
    }
}

@Composable
private fun MeadowAction(text:String,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),
        contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.primary)) { Text(text) }
}

@Composable
internal fun MeadowGrowthCard(world: MeadowState) {
    val title = when(world.nextMilestone) {
        MeadowMilestone.Flowers -> "First flowers"
        MeadowMilestone.Wildflowers -> "More wildflowers"
        MeadowMilestone.Visitors -> "A squirrel plants an acorn"
        MeadowMilestone.Nest -> "A nest takes shape"
        MeadowMilestone.Eggs -> "A new beginning"
        MeadowMilestone.Oak -> "A sapling becomes an oak"
        null -> "A meadow full of life"
    }
    SectionCard(title) {
        world.remainingMillis?.let { remaining ->
            Text("${(remaining + MeadowState.MINUTE - 1) / MeadowState.MINUTE} min at full health",
                style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(progress = { world.milestoneProgress }, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
        Text(when {
            !world.checkpointMonitoring -> "Growth waits while monitoring is off. Your discoveries stay."
            !world.checkpointFull -> "Reach 100 health to keep growing. Earlier rewards stay with you."
            world.nextMilestone == null -> "Keep exploring and playing. Your meadow is yours to keep."
            else -> "Full health keeps your meadow growing, even while you are away."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(world.nestStage>0) Text(when(world.nestStage){1->"The robin brought a twig.";2->"A little nest appeared.";else->"Two tiny eggs. A new beginning."},style=MaterialTheme.typography.bodySmall)
        if(world.acornStage>0) Text(when(world.acornStage){1->"The squirrel planted an acorn.";2->"The acorn became a sapling.";else->"A young oak. The meadow remembers its visitors."},style=MaterialTheme.typography.bodySmall)
    }
}

private data class MeadowEntry(val name:String,val found:Boolean,val description:String,val unlock:String)
private fun entries(world:MeadowState)=listOf(
    MeadowEntry("Butterfly",world.butterfly,"Draw wind and watch it ride the breeze.","First flowers: 30 min at full health."),
    MeadowEntry("Robin",world.robin,"A little visitor who makes a home here.","Wildflowers: 90 min at full health."),
    MeadowEntry("Fireflies",world.fireflies,"Tiny lights appear with the app’s dark appearance.","Visitors: 180 min at full health."),
    MeadowEntry("Hedgehog",MeadowDiscovery.Hedgehog in world.discoveries,"Still tucked in. Still asleep.","Brush the lower left of the meadow, or Explore."),
    MeadowEntry("Hidden pond",MeadowDiscovery.Pond in world.discoveries,"A quiet little place beneath the grass.","Brush the lower right of the meadow, or Explore."),
    MeadowEntry("Robin’s nest",world.nestStage>=2,"The robin’s story continues as your meadow grows.","240 min at full health."),
    MeadowEntry("Squirrel",world.acornStage>0,"Its acorn becomes a lasting landmark.","180 min at full health."),
    MeadowEntry("Young oak",world.acornStage>=3,"Your visitors have changed the landscape.","360 min at full health."),
)

@Composable
private fun UndergroundMeadow(world:MeadowState,effects:MeadowEffects) {
    val motion=rememberMotionEnabled()
    val painter=remember{MeadowPainter()}
    val dark=MaterialTheme.colorScheme.background.luminance()<.5f
    painter.palette(if(dark)GrassDarkHighlight else GrassHighlight,if(dark)GrassDarkBlade else Forest,
        if(dark)MeadowNightPetal else MeadowPetal,MeadowGold,if(dark)GrassDarkDry else Earth,dark)
    // Own visible clock: the underground keeps moving if its surface has scrolled off screen.
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver{_,_->started=lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)};lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    var frame by remember{mutableIntStateOf(0)}
    var visible by remember{mutableStateOf(false)}
    LaunchedEffect(motion,started,visible){if(motion&&started&&visible){while(true)androidx.compose.animation.core.withInfiniteAnimationFrameNanos{frame++}}}
    Canvas(Modifier.fillMaxWidth().height(230.dp)
        .onGloballyPositioned { visible=it.boundsInWindow().let{b->b.width>0&&b.height>0} }
        .semantics{contentDescription="Underground meadow: roots, mushrooms, a beetle and an ant"}
        .pointerInput(Unit){detectTapGestures{point->val x=point.x/size.width;val y=point.y/size.height
            when{y>.63f&&x>.54f->{effects.antParcel=(effects.antParcel+1)%3;effects.say("An ambitious ant. A very important delivery.")}
                x>.55f&&y<.67f->{effects.beetleUntil=effects.time+5;effects.say("The beetle stretches, then goes for a wander.")}
                else->{effects.glowUntil=effects.time+5;effects.say("The mushrooms answer with a soft glow.")}}
        }}) {
        @Suppress("UNUSED_VARIABLE")val tick=frame+effects.revision
        drawIntoCanvas{canvas->val scale=size.height/230;canvas.nativeCanvas.save();canvas.nativeCanvas.scale(scale,scale);canvas.nativeCanvas.clipRect(0f,0f,size.width/scale,230f);painter.underground(canvas.nativeCanvas,size.width/scale,world,effects,motion);canvas.nativeCanvas.restore()}
    }
}
