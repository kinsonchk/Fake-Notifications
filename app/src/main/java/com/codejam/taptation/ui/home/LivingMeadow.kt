package com.codejam.taptation.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateFloatAsState
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
import com.codejam.taptation.ui.common.formatDuration
import com.codejam.taptation.ui.common.rememberMotionEnabled
import com.codejam.taptation.ui.theme.*

/** Public Home has no time-skip or unlock switches. The debug preview is isolated from this store. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LivingMeadow(health: Int, world: MeadowState, scenery: MeadowScenery = MeadowScenery.Valley,
    onDiscover: (MeadowDiscovery) -> Unit) {
    val effects = remember { MeadowEffects() }
    var underground by rememberSaveable { mutableStateOf(false) }
    var collection by rememberSaveable { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(320f) }
    // A newly earned acorn gets one visit; returning later can replay it without awarding anything.
    var seenAcorn by rememberSaveable { mutableIntStateOf(world.acornStage) }
    LaunchedEffect(world.acornStage) {
        if (world.acornStage > seenAcorn) { effects.squirrel(); seenAcorn = world.acornStage }
    }
    // Valley feedback while Home is open: the mist lifts on a new stage; a catch sends a gust and rolls it back.
    var seenStage by rememberSaveable { mutableIntStateOf(world.valleyStage.ordinal) }
    var seenCatches by rememberSaveable { mutableLongStateOf(world.checkpointCatches) }
    LaunchedEffect(world.valleyStage, world.checkpointCatches) {
        if (seenCatches >= 0 && world.checkpointCatches > seenCatches) { effects.gust = 1f; effects.say("Caught. The mist rolls back in.") }
        else if (world.valleyStage.ordinal > seenStage) effects.say("The mist lifts. ${valleyText(world.valleyStage)}")
        seenStage = world.valleyStage.ordinal; seenCatches = world.checkpointCatches
    }
    val motion = rememberMotionEnabled()
    val soilOpen by animateFloatAsState(if (underground) 1f else 0f, if (motion) tween(500) else snap(), label = "Soil open")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Meadow and underground touch: one cross-section, the meadow drawing the soil's top slice.
        Column {
            BoxWithConstraints {
                SideEffect { width = maxWidth.value }
                MeadowCanvas(health = health, world = world, effects = effects,
                    onDiscover = onDiscover, onOpenUnderground = { underground = true }, scenery = scenery, soilOpen = soilOpen)
            }
            AnimatedVisibility(underground) { UndergroundMeadow(health, world, effects, scenery) }
        }
        TextButton(onClick = { underground = !underground }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (underground) "Back to the surface ↑" else "Below ground ↓")
        }
        if (underground) Text("Tap a mushroom, the beetle, the ant, the worm or the fish.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

internal fun valleyTitle(stage: ValleyStage) = when (stage) {
    ValleyStage.Grass -> "Your patch of grass"
    ValleyStage.NearField -> "The near field"
    ValleyStage.River -> "A river"
    ValleyStage.Hills -> "Rolling hills"
    ValleyStage.Bridge -> "A bridge"
    ValleyStage.Ridges -> "Far ridges"
    ValleyStage.Cottage -> "A cottage"
    ValleyStage.Valley -> "The whole valley"
}

internal fun valleyText(stage: ValleyStage) = when (stage) {
    ValleyStage.Grass -> "Mist hides everything past the grass."
    ValleyStage.NearField -> "Bushes and daisies at the edge of the meadow."
    ValleyStage.River -> "Water glints beyond the grass."
    ValleyStage.Hills -> "A path winds down towards the water."
    ValleyStage.Bridge -> "Someone built a crossing over the river."
    ValleyStage.Ridges -> "Pines, open sky and the sun."
    ValleyStage.Cottage -> "Smoke curls from a chimney on the hill."
    ValleyStage.Valley -> "A windmill turns on the far ridge."
}

/** Clean streak and what the next stretch of clean time reveals. */
@Composable
internal fun MeadowValleyCard(world: MeadowState) {
    SectionCard(valleyTitle(world.valleyStage)) {
        Text("Clean for ${formatDuration(world.cleanMillis)}", style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { world.valleyProgress }, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.primaryContainer)
        val next = world.valleyStage.next
        Text(if (next != null) "${valleyTitle(next)} in ${formatDuration(world.valleyRemainingMillis ?: 0)}"
            else "Your whole valley is visible.", style = MaterialTheme.typography.bodySmall)
        Text(when {
            !world.checkpointMonitoring -> "Clean time waits while monitoring is off."
            world.revealed > world.valleyStage -> "A catch brings the mist back one stage. What you uncovered stays behind it."
            else -> "A catch brings the mist back one stage."
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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

/** Continues the meadow's soil below its bottom edge, with the same painter, scale and soil coordinates. */
@Composable
private fun UndergroundMeadow(health: Int, world: MeadowState, effects: MeadowEffects, scenery: MeadowScenery) {
    val motion = rememberMotionEnabled()
    val painter = remember { MeadowPainter() }
    val blades = remember { MeadowMotion().blades }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    painter.palette(if (dark) GrassDarkHighlight else GrassHighlight, if (dark) GrassDarkBlade else Forest,
        if (dark) MeadowNightPetal else MeadowPetal, MeadowGold, if (dark) GrassDarkDry else Earth, dark)
    // Own visible clock: the underground keeps moving if its surface has scrolled off screen.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    var frame by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(motion, started, visible) { if (motion && started && visible) { while (true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos { frame++ } } }
    val shown = meadowSoilShown(scenery)
    Canvas(Modifier.fillMaxWidth().height(UNDERGROUND_HEIGHT.dp)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .onGloballyPositioned { visible = it.boundsInWindow().let { b -> b.width > 0 && b.height > 0 } }
        .semantics { contentDescription = "Underground: soil layers, roots, mushrooms, a beetle, an ant, a worm" +
            (if (MeadowDiscovery.Pond in world.discoveries) ", the pond's fish" else "") + if (health < 60) ", dry and cracked" else "" }
        .pointerInput(world.discoveries) { detectTapGestures { point ->
            // Back to soil coordinates (y = 0 at the grass line), as drawn.
            val scale = size.height / UNDERGROUND_HEIGHT; val w = size.width / scale
            val x = point.x / scale; val y = point.y / scale + shown
            fun near(cx: Float, cy: Float, r: Float) = kotlin.math.hypot(x - cx, y - cy) < r
            val mushroom = (0..2).firstOrNull { val (mx, my) = painter.mushroom(it, w); near(mx, my - 14, 24f) }
            when {
                mushroom != null -> { effects.glowUntil = effects.time + 5; effects.pulseFrom = mushroom; effects.pulseAt = effects.time
                    effects.say("The mushrooms glow, and light runs along the threads to the roots.") }
                near(w * .76f, 110f, 28f) -> { effects.beetleUntil = effects.time + 5; effects.say("The beetle stretches, then goes for a wander.") }
                MeadowDiscovery.Pond in world.discoveries && near(w * .81f, 32f, 32f) -> { effects.bubbleAt = effects.time; effects.say("A little fish lives in your pond.") }
                near(w * .40f + 13, 40f, 18f) -> { effects.wormUntil = effects.time + 3; effects.say("A worm, busy keeping the soil soft.") }
                near(w * .58f, 168f, 16f) -> effects.say("An old shell, older than the meadow itself.")
                x in w * .58f..w * .74f && y < 100f -> { effects.antParcel = (effects.antParcel + 1) % 3; effects.say("An ambitious ant. A very important delivery.") }
                else -> { effects.glowUntil = effects.time + 5; effects.say("The mushrooms answer with a soft glow.") }
            }
        } }) {
        @Suppress("UNUSED_VARIABLE") val tick = frame + effects.revision
        val scale = size.height / UNDERGROUND_HEIGHT; val w = size.width / scale
        drawIntoCanvas { canvas -> val native = canvas.nativeCanvas
            native.save(); native.scale(scale, scale); native.translate(0f, -shown)
            native.clipRect(0f, shown, w, shown + UNDERGROUND_HEIGHT)
            painter.soil(native, w, shown + UNDERGROUND_HEIGHT, health, world, effects, motion, blades)
            native.restore()
        }
        // Same soft sides as the meadow, plus a fade into the page at the bottom.
        drawRect(Brush.horizontalGradient(0f to Color.Transparent, .07f to Color.Black, .93f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        drawRect(Brush.verticalGradient(0f to Color.Black, .85f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }
}

private const val UNDERGROUND_HEIGHT = 260f
