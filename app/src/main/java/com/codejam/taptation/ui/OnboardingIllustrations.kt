package com.codejam.taptation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codejam.taptation.R
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.ui.common.color
import com.codejam.taptation.ui.common.rememberMotionEnabled
import kotlinx.coroutines.delay

/*
 * Small decorative pictures for onboarding. They show what Taptation does with a sample
 * notification; they are not data, so screen readers get one description per picture.
 */

/** Sample app used by the onboarding pictures. */
internal const val SAMPLE_APP = "Instagram"
private const val SAMPLE_TITLE = "sam.k liked your photo"

/** A notification as it looks in the shade, with the rule colour as its left stripe. */
@Composable
internal fun MiniNotification(subtitle: String, modifier: Modifier = Modifier, rule: AppRule = AppRule.LetUsDecide) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(rule.color()))
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).background(rule.color(), CircleShape), contentAlignment = Alignment.Center) {
                    Text(SAMPLE_APP.take(1), color = MaterialTheme.colorScheme.background,
                        style = MaterialTheme.typography.labelLarge)
                }
                Column {
                    Text(SAMPLE_TITLE, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A sample notification being tapped: an expanding ring over it. */
@Composable
internal fun TapIllustration(modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val ring = rememberInfiniteTransition(label = "tap")
    val animated = ring.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    IllustrationFrame(modifier.clearAndSetSemantics { contentDescription = "A notification being tapped" }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box {
                MiniNotification(SAMPLE_APP)
                Box(Modifier.align(Alignment.CenterEnd).offset(x = (-36).dp).size(30.dp)
                    .graphicsLayer {
                        val p = if (motion) animated.value else .45f
                        scaleX = .5f + p * 1.2f; scaleY = scaleX; alpha = if (motion) 1f - p else .8f
                    }
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Text("Taptation sees the tap, not the message", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The catch reminder drawn over another app: a faded app screen with the reminder card on top.
 * The health bar animates once from [health] to [healthAfter]; [compact] leaves out the health row.
 */
@Composable
internal fun OverlayIllustration(
    modifier: Modifier = Modifier,
    health: Int = 100,
    healthAfter: Int = 100,
    compact: Boolean = false,
) {
    val description = if (compact) "The catch reminder shown on top of another app"
        else "The catch reminder shown on top of $SAMPLE_APP, with your health going from $health to $healthAfter"
    IllustrationFrame(modifier.clearAndSetSemantics { contentDescription = description }) {
        val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(9.dp).background(faint, RoundedCornerShape(5.dp)))
            Box(Modifier.fillMaxWidth(.55f).height(9.dp).background(faint, RoundedCornerShape(5.dp)))
            Box(Modifier.fillMaxWidth().height(24.dp).background(faint.copy(alpha = .05f), RoundedCornerShape(8.dp)))
        }
        Surface(Modifier.padding(top = 34.dp).padding(horizontal = 22.dp).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 6.dp) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.caught_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp, lineHeight = 22.sp))
                if (!compact) {
                    Text("$SAMPLE_APP is set as distracting", style = MaterialTheme.typography.labelMedium)
                    HealthDrop(health, healthAfter)
                }
                MiniButton(stringResource(R.string.caught_our_app), filled = true)
                MiniButton(stringResource(R.string.caught_original_app, SAMPLE_APP), filled = false)
            }
        }
    }
}

@Composable
private fun HealthDrop(health: Int, healthAfter: Int) {
    val motion = rememberMotionEnabled()
    val shown = remember(health, healthAfter) { Animatable(health / 100f) }
    LaunchedEffect(health, healthAfter, motion) {
        if (!motion) { shown.snapTo(healthAfter / 100f); return@LaunchedEffect }
        delay(700)
        shown.animateTo(healthAfter / 100f, tween(1200))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Health", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$healthAfter / 100   −${health - healthAfter}", style = MaterialTheme.typography.labelMedium)
    }
    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(3.dp))) {
        Box(Modifier.fillMaxWidth(shown.value.coerceIn(0f, 1f)).height(6.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
    }
}

/** A non-interactive copy of a caught-screen button, with an optional cost line. */
@Composable
internal fun MiniButton(text: String, filled: Boolean, modifier: Modifier = Modifier, caption: String? = null) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().heightIn(min = 30.dp), shape = RoundedCornerShape(10.dp),
        color = if (filled) colors.primary else Color.Transparent,
        contentColor = if (filled) colors.onPrimary else colors.onSurface,
        border = if (filled) null else BorderStroke(1.dp, colors.outlineVariant)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = if (filled) colors.onPrimary else colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun IllustrationFrame(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth()
        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        .padding(1.dp), content = content)
}

/** Onboarding card with a picture above its heading; same look as SectionCard. */
@Composable
internal fun IllustratedCard(
    title: String,
    body: String,
    illustration: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            illustration()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
