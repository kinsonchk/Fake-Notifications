package com.codejam.taptation.ui.common

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** True unless the user turned animations off in Android's accessibility or developer settings. */
fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/** The tree in miniature with one leaf that drops as [fall] goes from 0 to 1. Display only. */
@Composable
fun LeafBadge(fall: Float, modifier: Modifier = Modifier) {
    val leaves = MaterialTheme.colorScheme.primary
    val earth = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.size(width = 92.dp, height = 84.dp).semantics { contentDescription = "A tree losing a leaf" }) {
        val unit = minOf(size.width / 260f, size.height / 240f)
        withTransform({
            translate((size.width - 260 * unit) / 2, (size.height - 240 * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            drawOval(leaves.copy(alpha = .12f), Offset(43f, 207f), Size(174f, 24f))
            drawRoundRect(earth, Offset(123f, 116f), Size(14f, 99f), CornerRadius(7f))
            drawLine(earth, Offset(130f, 154f), Offset(101f, 112f), 8f)
            drawLine(earth, Offset(130f, 167f), Offset(160f, 118f), 8f)
            rotate(-25f, Offset(97f, 102f)) { drawOval(leaves.copy(alpha = .87f), Offset(50f, 48f), Size(94f, 108f)) }
            rotate(25f, Offset(167f, 103f)) { drawOval(leaves.copy(alpha = .75f), Offset(120f, 48f), Size(94f, 108f)) }
            drawOval(leaves, Offset(90f, 22f), Size(96f, 118f))
            drawOval(leaves, Offset(77f, 98f), Size(120f, 66f))
            // The falling leaf: from the canopy edge to the ground, spinning a little.
            val x = 196f + 10f * fall
            val y = 96f + 114f * fall
            withTransform({ translate(x, y); rotate(160f * fall, Offset.Zero); scale(1.6f, 1.6f, Offset.Zero) }) {
                val leaf = Path().apply {
                    moveTo(0f, 0f); cubicTo(2f, -13f, 13f, -14f, 21f, -9f); cubicTo(21f, 0f, 12f, 3f, 0f, 0f); close()
                }
                drawPath(leaf, earth)
            }
        }
    }
}

/**
 * "Tree health 90 → 80" with a bar that shrinks and a "−10" that floats up. Shows the final value
 * straight away when animations are off, and always states the loss in text.
 */
@Composable
fun HealthLossBlock(healthBefore: Int, healthAfter: Int, onFall: (Float) -> Unit = {}) {
    val animate = remember { animationsEnabled() }
    val drop = remember { Animatable(if (animate) 0f else 1f) }
    val rise = remember { Animatable(if (animate) 0f else 1f) }
    val fall = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            coroutineScope {
                launch { drop.animateTo(1f, tween(600, delayMillis = 150, easing = FastOutSlowInEasing)) }
                launch { rise.animateTo(1f, tween(900, delayMillis = 150, easing = LinearEasing)) }
                launch { fall.animateTo(1f, tween(550, delayMillis = 120, easing = FastOutSlowInEasing)) }
            }
        }
    }
    LaunchedEffect(fall.value) { onFall(fall.value) }
    val lost = (healthBefore - healthAfter).coerceAtLeast(0)
    val shown = healthBefore + ((healthAfter - healthBefore) * drop.value).roundToInt()
    val bar = (healthBefore + (healthAfter - healthBefore) * drop.value) / 100f
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Tree health $healthAfter out of 100, down $lost" },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tree health", style = MaterialTheme.typography.titleSmall)
                    if (lost > 0) Surface(Modifier.alpha(drop.value), shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                        Text("−$lost", Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$shown", style = MaterialTheme.typography.headlineSmall)
                    Text(" / 100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            if (lost > 0 && animate && rise.value in 0.01f..0.99f) Text("−$lost", Modifier.align(Alignment.TopEnd)
                .offset { androidx.compose.ui.unit.IntOffset(0, (-18.dp * rise.value).roundToPx()) }.alpha(1f - rise.value),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.fillMaxWidth(bar.coerceIn(0f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp))
                .background(if (healthAfter < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
        }
    }
}
