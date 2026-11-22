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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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

/** Mockup meadow: curved blades on a fading mound, coherent wind and springy local brushing. */
@Composable
fun GrassMeadow(health: Int, modifier: Modifier = Modifier) {
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
    val currentTouch by rememberUpdatedState(if (pressed) touch else Offset.Unspecified)
    val field = remember { MeadowMotion() }
    val bladePath = remember { Path() }
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < .5f
    val bladeColor = if (dark) GrassDarkBlade else Forest
    val highlight = if (dark) GrassDarkHighlight else GrassHighlight
    val dry = if (dark) GrassDarkDry else Earth
    val soil = if (dark) MeadowDarkSoil else MeadowSoil
    val unit = canvasSize.height / MeadowMotion.HEIGHT
    val groundY = canvasSize.height * .86f
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
    // Read the clock only in drawing: wind does not recompose the surrounding screen every frame.
    LaunchedEffect(started, visible, motion, canvasSize) {
        if (started && visible && motion && unit > 0f) {
            var previous = 0L
            while (true) withInfiniteAnimationFrameNanos { now ->
                val elapsed = if (previous == 0L) 1f / 60f else (now - previous) / 1_000_000_000f
                previous = now
                val pointer = currentTouch
                field.advance(elapsed, currentGrowth, canvasSize.width / unit,
                    if (pointer.isSpecified) pointer.x / unit else Float.NaN,
                    if (pointer.isSpecified) pointer.y / unit else Float.NaN)
                frame++
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(165.dp)
        .onSizeChanged { canvasSize = it }
        .onGloballyPositioned { visible = it.boundsInWindow().let { b -> b.width > 0 && b.height > 0 } }
        .semantics { contentDescription = "Meadow health ${health.coerceIn(0, 100)} out of 100" }
        .pointerInput(motion) {
            if (!motion) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touch = down.position; pressed = true
                try {
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break // Keep vertical page scrolling available.
                        touch = change.position
                    } while (change.pressed)
                } finally { pressed = false }
            }
        }) {
        @Suppress("UNUSED_VARIABLE") val drawFrame = frame
        val scale = size.height / MeadowMotion.HEIGHT
        drawPath(hillPath, hillBrush)
        val activeCount = 8f + growth * (MeadowMotion.COUNT - 8)
        // Healthy grass keeps the mockup's saturated greens, not the pale card/selection palette.
        val greenAmount = ((growth - .15f) / .1f).coerceIn(0f, 1f)
        field.blades.forEach { blade ->
            val alpha = (activeCount - blade.activationRank).coerceIn(0f, 1f)
            if (alpha == 0f) return@forEach
            val x = blade.u * size.width
            val y = blade.baseY * scale
            val length = blade.height(growth) * scale
            val bend = (if (motion) blade.bend else blade.restBend(growth)) * scale
            val controlX = x + bend * .35f
            val controlY = y - length * .55f
            val baseWidth = (2.4f + (1f - blade.layer * .25f) * 2f) * scale
            bladePath.reset()
            bladePath.moveTo(x - baseWidth * .5f, y)
            bladePath.quadraticTo(controlX - baseWidth * .2f, controlY, x + bend, y - length)
            bladePath.quadraticTo(controlX + baseWidth * .2f, controlY, x + baseWidth * .5f, y)
            bladePath.close()
            drawPath(bladePath, lerp(dry, if (blade.layer == 0) bladeColor else highlight, greenAmount),
                alpha = alpha * if (blade.layer == 2) .72f else .95f)
        }
    }
}
