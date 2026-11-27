package com.codejam.taptation.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codejam.taptation.ui.common.rememberMotionEnabled
import com.codejam.taptation.ui.theme.*
import com.codejam.taptation.logic.meadow.MeadowState
import com.codejam.taptation.logic.meadow.MeadowDiscovery
import com.codejam.taptation.logic.meadow.ValleyStage

/** Mockup meadow: curved blades on a fading mound, coherent wind and springy local brushing. */
@Composable
fun GrassMeadow(health: Int, modifier: Modifier = Modifier) {
    val effects = remember { MeadowEffects() }
    MeadowCanvas(health, modifier, null, effects)
}

@Composable
internal fun MeadowCanvas(health: Int, modifier: Modifier = Modifier, world: MeadowState?, effects: MeadowEffects,
    onDiscover: (MeadowDiscovery) -> Unit = {}, onOpenUnderground: () -> Unit = {},
    scenery: MeadowScenery = MeadowScenery.Classic, soilOpen: Float = 0f) {
    val motion = rememberMotionEnabled()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var visible by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val growth by animateFloatAsState(health.coerceIn(0, 100) / 100f,
        animationSpec = if (motion) tween(700) else snap(), label = "Meadow health")
    val currentGrowth by rememberUpdatedState(growth)
    // Valley camera and landmarks animate when the stage changes (mist lifting, or rolling back after a catch).
    val clean = world?.cleanMillis ?: 0L
    val revealed = world?.revealed ?: ValleyStage.Grass
    fun spec(ms: Int) = if (motion) tween<Float>(ms) else snap()
    val zoom by animateFloatAsState(ValleyCamera.zoom(clean), spec(1400), label = "Valley zoom")
    val fog by animateFloatAsState(ValleyCamera.fog(clean), spec(1400), label = "Valley mist")
    val pathGrown by animateFloatAsState(if (revealed >= ValleyStage.Hills) 1f else 0f, spec(1800), label = "Valley path")
    val bridgeGrown by animateFloatAsState(if (revealed >= ValleyStage.Bridge) 1f else 0f, spec(1200), label = "Valley bridge")
    val cottageGrown by animateFloatAsState(if (revealed >= ValleyStage.Cottage) 1f else 0f, spec(1200), label = "Valley cottage")
    val windmillGrown by animateFloatAsState(if (revealed >= ValleyStage.Valley) 1f else 0f, spec(1200), label = "Valley windmill")
    val oakGrown by animateFloatAsState(if ((world?.acornStage ?: 0) >= 3) 1f else 0f, spec(1200), label = "Valley oak")
    val currentTouch by rememberUpdatedState(if (pressed) touch else Offset.Unspecified)
    val field = remember { MeadowMotion() }
    val painter = remember { MeadowPainter() }
    val valley = remember { ValleyScenery() }
    // Back row first so the brighter front row overlaps it: depth without extra shapes.
    val drawOrder = remember { field.blades.sortedByDescending { it.layer } }
    val currentWorld by rememberUpdatedState(world)
    val currentDiscover by rememberUpdatedState(onDiscover)
    val currentOpenUnder by rememberUpdatedState(onOpenUnderground)
    val bladePath = remember { Path() }
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < .5f
    val bladeColor = if (dark) GrassDarkBlade else GrassBlade
    val highlight = if (dark) GrassDarkHighlight else GrassHighlight
    val dry = if (dark) GrassDarkDry else GrassDry
    val soil = if (dark) MeadowDarkSoil else MeadowSoil
    val tip = if (dark) GrassDarkTip else GrassTip
    val logicalHeight = meadowLogicalHeight(world != null, scenery)
    val unit = canvasSize.height / logicalHeight
    val groundY = canvasSize.height * if (world == null) .86f else WORLD_GROUND
    val groundOffset = if (unit > 0) groundY / unit - MeadowMotion.HEIGHT * .86f else 0f
    painter.palette(highlight, bladeColor, if (dark) MeadowNightPetal else MeadowPetal,
        MeadowGold, dry, dark)
    val hillBrush = remember(canvasSize, soil, background) {
        Brush.verticalGradient(listOf(soil, background), startY = groundY - 14f * unit,
            endY = canvasSize.height.toFloat().coerceAtLeast(1f))
    }
    val hillPath = remember(canvasSize) {
        Path().apply {
            moveTo(0f, groundY + 6f * unit)
            quadraticTo(canvasSize.width * .5f, groundY - 12f * unit, canvasSize.width.toFloat(), groundY + 6f * unit)
            lineTo(canvasSize.width.toFloat(), canvasSize.height.toFloat())
            lineTo(0f, canvasSize.height.toFloat())
            close()
        }
    }
    // Turf band where blades meet the mound, so they grow out of ground instead of floating over beige soil.
    val turfPath = remember(canvasSize) {
        Path().apply {
            moveTo(0f, groundY + 2f * unit)
            quadraticTo(canvasSize.width * .5f, groundY - 14f * unit, canvasSize.width.toFloat(), groundY + 2f * unit)
            lineTo(canvasSize.width.toFloat(), groundY + 12f * unit)
            quadraticTo(canvasSize.width * .5f, groundY, 0f, groundY + 12f * unit)
            close()
        }
    }
    // Read the clock only in drawing: wind does not recompose the surrounding screen every frame.
    LaunchedEffect(started, visible, motion, canvasSize) {
        if (started && visible && motion && unit > 0f) {
            var previous = 0L
            while (true) withInfiniteAnimationFrameNanos { now ->
                val elapsed = if (previous == 0L) 1f / 60f else (now - previous) / 1_000_000_000f
                previous = now
                val pointer = currentTouch
                effects.tick(elapsed.coerceAtMost(.1f))
                field.advance(elapsed, currentGrowth, canvasSize.width / unit,
                    if (pointer.isSpecified) pointer.x / unit else Float.NaN,
                    if (pointer.isSpecified) pointer.y / unit - groundOffset else Float.NaN,
                    effects.gust * 25, effects.dragVelocity,
                    if (effects.vortexUntil > effects.time) effects.vortex.x else Float.NaN,
                    if (effects.vortexUntil > effects.time) 45f else 0f)
                frame++
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(logicalHeight.dp)
        // Offscreen so the edge fade below masks the whole scene (soft sides instead of hard cuts).
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .onSizeChanged { canvasSize = it }
        .onGloballyPositioned { visible = it.boundsInWindow().let { b -> b.width > 0 && b.height > 0 } }
        .semantics { contentDescription = "Meadow health ${health.coerceIn(0, 100)} out of 100" +
            if (scenery == MeadowScenery.Valley && world != null) ". Valley: ${valleyTitle(world.valleyStage)}" else "" }
        .pointerInput(motion, world != null, canvasSize) {
            if (!motion && currentWorld == null) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (unit <= 0) return@awaitEachGesture
                val interactive = currentWorld != null
                if (interactive) down.consume()
                val trail = ArrayList<Offset>()
                val revealed = mutableSetOf<MeadowDiscovery>()
                var distance = 0f
                var previous = down.position
                var pulled = false
                touch = down.position; pressed = true
                try {
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break // Keep vertical page scrolling available.
                        if (interactive) change.consume()
                        distance += (change.position - previous).getDistance() / unit
                        effects.dragVelocity = ((change.position.x - previous.x) / unit * 2).coerceIn(-45f,45f)
                        previous = change.position
                        touch = change.position
                        if (interactive) {
                            val point = touch / unit
                            if (down.position.y > size.height * .93f && (down.position.y - touch.y) / unit > 32 && !pulled) {
                                currentOpenUnder(); pulled = true
                            }
                            if (!pulled && distance > 25 && point.y > logicalHeight * .65f) {
                                val found = if (point.x < size.width / unit * .27f) MeadowDiscovery.Hedgehog
                                    else if (point.x > size.width / unit * .72f) MeadowDiscovery.Pond else null
                                if (found != null && revealed.add(found)) {
                                    currentDiscover(found)
                                    effects.say(if(found == MeadowDiscovery.Hedgehog) "You found a sleeping hedgehog." else "A tiny pond was hiding in the grass.")
                                }
                            }
                            trail.add(point); if (trail.size > 64) trail.removeAt(0)
                            if (!pulled && effects.time >= effects.vortexUntil - 2 && isMeadowCircle(trail)) {
                                effects.whirlwind(Offset(trail.map { it.x }.average().toFloat(), trail.map { it.y }.average().toFloat())); trail.clear()
                            }
                        }
                    } while (change.pressed)
                    if (interactive && distance < 15 && !pulled) {
                        val point = touch / unit
                        val width = size.width / unit
                        val ground = groundY / unit
                        val state = currentWorld!!
                        val flower = (0 until state.flowers).any { i -> (point - Offset(MeadowPainter.FLOWER_X[i] * width, ground - MeadowPainter.FLOWER_H[i])).getDistance() < 26 }
                        when {
                            flower -> effects.flower(point)
                            state.acornStage > 0 && kotlin.math.abs(point.x - width * .48f) < 24 -> effects.squirrel()
                            state.robin && (point - Offset(if(state.nestStage>=2)width*.75f else width*.36f,if(state.nestStage>=2)ground-56 else ground)).getDistance() < 32 -> {
                                effects.birdUntil = effects.time + 1.4f; effects.say("Hello, little robin.")
                            }
                            else -> effects.scatter(point)
                        }
                    }
                } finally { pressed = false }
            }
        }) {
        @Suppress("UNUSED_VARIABLE") val drawFrame = frame + effects.revision
        val scale = size.height / logicalHeight
        val greenAmount = ((growth - .15f) / .1f).coerceIn(0f, 1f)
        fun grass(c: Color) = lerp(dry, c, greenAmount)
        if (scenery == MeadowScenery.Valley && world != null) valley.draw(this, scale, size.width / scale, groundY / scale, logicalHeight,
            growth, dark, background, if (dark) MeadowNightPetal else MeadowPetal, MeadowGold, if (motion) effects.time else 0f,
            ValleyView(zoom, fog, pathGrown, bridgeGrown, cottageGrown, windmillGrown, oakGrown))
        else drawPath(hillPath, hillBrush)
        // Underground open: this canvas draws the soil's top slice; the underground canvas continues it below.
        if (soilOpen > 0f && world != null) drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas; val w = size.width / scale; val ground = groundY / scale
            // restoreToCount: the fade layer adds a second save while the soil animates; one restore()
            // would leave the scale/translate/clip active and misplace the edge fade below.
            val saved = native.save(); native.scale(scale, scale); native.translate(0f, ground)
            native.clipRect(0f, -20f, w, logicalHeight - ground)
            if (soilOpen < 1f) native.saveLayerAlpha(0f, -20f, w, logicalHeight - ground, (soilOpen * 255).toInt())
            painter.soil(native, w, logicalHeight - ground, health, world, effects, motion, field.blades)
            native.restoreToCount(saved)
        }
        drawPath(turfPath, Brush.verticalGradient(listOf(grass(bladeColor).copy(alpha = .85f), Color.Transparent),
            startY = groundY - 8f * unit, endY = groundY + 12f * unit))
        world?.let { drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save(); canvas.nativeCanvas.scale(scale,scale)
            canvas.nativeCanvas.clipRect(0f,0f,size.width/scale,logicalHeight)
            painter.landmarks(canvas.nativeCanvas,size.width/scale,groundY/scale,it,if(motion)effects.time else 0f)
            canvas.nativeCanvas.restore()
        } }
        val activeCount = 8f + growth * (MeadowMotion.COUNT - 8)
        // Healthy grass keeps the mockup's saturated greens, not the pale card/selection palette.
        // One shared root-to-tip gradient per row: darker at the root, sunlit at the tip.
        val tipY = groundY - 100f * scale
        val backBrush = Brush.verticalGradient(listOf(grass(lerp(bladeColor, Color.Black, .22f)), grass(bladeColor)), groundY, tipY)
        val midBrush = Brush.verticalGradient(listOf(grass(bladeColor), grass(highlight)), groundY, tipY)
        val frontBrush = Brush.verticalGradient(listOf(grass(bladeColor), grass(lerp(highlight, tip, .55f))), groundY, tipY)
        drawOrder.forEach { blade ->
            val alpha = (activeCount - blade.activationRank).coerceIn(0f, 1f)
            if (alpha == 0f) return@forEach
            val x = blade.u * size.width
            val y = (blade.baseY + groundOffset) * scale
            val length = blade.height(growth) * scale
            val bend = (if (motion) blade.bend else blade.restBend(growth)) * scale
            // The root stays planted while the upper blade curls and droops into the breeze.
            val rootControlX = x - bend * .12f
            val tipControlX = x + bend * .48f
            val tipY = y - length + (bend * bend / length.coerceAtLeast(scale)).coerceAtMost(18f * scale) * .45f
            val baseWidth = (2.4f + (1f - blade.layer * .25f) * 2f) * scale
            bladePath.reset()
            bladePath.moveTo(x - baseWidth * .5f, y)
            bladePath.cubicTo(rootControlX - baseWidth * .3f, y - length * .38f,
                tipControlX - baseWidth * .15f, y - length * 1.08f, x + bend, tipY)
            bladePath.cubicTo(tipControlX + baseWidth * .15f, y - length * 1.08f,
                rootControlX + baseWidth * .3f, y - length * .38f, x + baseWidth * .5f, y)
            bladePath.close()
            when (blade.layer) {
                0 -> drawPath(bladePath, frontBrush, alpha = alpha * .97f)
                1 -> drawPath(bladePath, midBrush, alpha = alpha * .92f)
                else -> drawPath(bladePath, backBrush, alpha = alpha * .8f)
            }
        }
        world?.let { drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save(); canvas.nativeCanvas.scale(scale,scale)
            canvas.nativeCanvas.clipRect(0f,0f,size.width/scale,logicalHeight)
            painter.life(canvas.nativeCanvas,size.width/scale,groundY/scale,health,it,effects,motion)
            canvas.nativeCanvas.restore()
        } }
        drawRect(EdgeFade, blendMode = BlendMode.DstIn)
        if (scenery == MeadowScenery.Valley) drawRect(TopFade, blendMode = BlendMode.DstIn)
    }
}

private val EdgeFade = Brush.horizontalGradient(0f to Color.Transparent, .07f to Color.Black, .93f to Color.Black, 1f to Color.Transparent)
private val TopFade = Brush.verticalGradient(0f to Color.Transparent, .1f to Color.Black)

private const val WORLD_GROUND = .83f
/** Logical height of the meadow canvas; the valley needs room above the grass tops for the hills and river. */
internal fun meadowLogicalHeight(hasWorld: Boolean, scenery: MeadowScenery) = when {
    !hasWorld -> MeadowMotion.HEIGHT; scenery == MeadowScenery.Valley -> 290f; else -> 230f }
/** Soil depth already shown below the grass line by the meadow canvas; the underground canvas starts there. */
internal fun meadowSoilShown(scenery: MeadowScenery) = meadowLogicalHeight(true, scenery) * (1 - WORLD_GROUND)
