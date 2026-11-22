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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codejam.taptation.ui.common.rememberMotionEnabled
import kotlin.math.*
import kotlin.random.Random

private data class Blade(val x: Float, val depth: Float, val height: Float, val phase: Float, val lean: Float)

/** Display-only meadow: a stable seeded landscape whose height and density represent saved health. */
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
    var phase by remember { mutableFloatStateOf(0f) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var pressed by remember { mutableStateOf(false) }
    val influence by animateFloatAsState(if (pressed && motion) 1f else 0f,
        animationSpec = if (motion) tween(if (pressed) 90 else 650) else snap(), label = "Grass brush")
    val growth by animateFloatAsState(health.coerceIn(0, 100) / 100f,
        animationSpec = if (motion) tween(700) else snap(), label = "Meadow health")
    val blades = remember {
        val random = Random(42)
        List(320) { Blade(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat() * 6.28f,
            random.nextFloat() * 2f - 1f) }.sortedBy { it.depth }
    }
    val path = remember { Path() }
    val green = MaterialTheme.colorScheme.primary
    val pale = MaterialTheme.colorScheme.primaryContainer
    val earth = MaterialTheme.colorScheme.tertiary
    // Infinite-animation frames are skipped by Compose test clocks. Health transitions remain finite.
    LaunchedEffect(started, visible, motion) {
        if (started && visible && motion) {
            var previous = 0L
            while (true) withInfiniteAnimationFrameNanos { now ->
                if (previous != 0L) phase = (phase + ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)) % 628f
                previous = now
            }
        }
    }
    Canvas(modifier.fillMaxWidth().height(180.dp)
        .semantics { contentDescription = "Meadow health ${health.coerceIn(0, 100)} out of 100" }
        .onGloballyPositioned { visible = it.boundsInWindow().let { b -> b.width > 0 && b.height > 0 } }
        .semantics { contentDescription = "Grass meadow, health ${health.coerceIn(0, 100)} out of 100" }
        .pointerInput(motion) {
            if (!motion) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                touch = down.position; pressed = true
                try {
                    do {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break // Let the surrounding page take vertical drags.
                        touch = change.position
                    } while (change.pressed)
                } finally { pressed = false }
            }
        }) {
        val densityCount = 35f + 285f * growth
        blades.forEachIndexed { i, blade ->
            // Stable positions, with a short fade at the density boundary instead of random popping.
            val opacity = (densityCount - i).coerceIn(0f, 1f)
            if (opacity == 0f) return@forEachIndexed
            val x = blade.x * size.width
            val y = size.height * (.57f + .36f * blade.depth)
            val length = size.height * (.045f + .34f * growth) * (.45f + .55f * blade.height) * (.65f + .35f * blade.depth)
            val sway = sin(phase * 1.2f + blade.phase + blade.x * 4f) * length * .12f
            val distance = if (touch.isSpecified) abs(x - touch.x) else Float.MAX_VALUE
            val bend = if (touch.isSpecified) {
                val proximity = (1f - distance / (size.width * .24f)).coerceIn(0f, 1f)
                proximity * influence * length * .8f * if (x < touch.x) -1f else 1f
            } else 0f
            val tipX = x + blade.lean * length * .2f + sway + bend
            val tipY = y - length + abs(bend) * .22f
            val halfWidth = (1.1f + blade.depth * 1.5f).dp.toPx()
            path.reset()
            path.moveTo(x - halfWidth, y)
            path.quadraticTo(x + sway * .4f, y - length * .55f, tipX, tipY)
            path.quadraticTo(x + halfWidth + bend * .3f, y - length * .35f, x + halfWidth, y)
            path.close()
            val color = if (growth < .15f) lerp(earth, green, growth / .15f)
                else lerp(pale, green, .5f + blade.depth * .5f)
            drawPath(path, color, alpha = opacity * (.6f + .4f * blade.depth))
        }
    }
}
