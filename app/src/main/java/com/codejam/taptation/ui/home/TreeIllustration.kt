package com.codejam.taptation.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Decorative brand illustration. It deliberately does not choose the still-undecided home metric. */
@Composable
fun TreeIllustration(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 235.dp) {
    val leaves = MaterialTheme.colorScheme.primary
    val earth = MaterialTheme.colorScheme.tertiary
    val soft = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = "A leafy tree illustration" }) {
        drawOval(Brush.radialGradient(listOf(soft, soft.copy(alpha = 0f)),
            center = center, radius = size.minDimension * .7f), size = size)
        val unit = minOf(size.width / 260f, size.height / 240f)
        withTransform({
            translate((size.width - 260 * unit) / 2, (size.height - 240 * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            drawOval(leaves.copy(alpha = .12f), Offset(43f, 207f), Size(174f, 24f))
            drawRoundRect(earth, Offset(123f, 116f), Size(14f, 99f), CornerRadius(7f))
            drawLine(earth, Offset(130f, 154f), Offset(101f, 112f), 8f)
            drawLine(earth, Offset(130f, 167f), Offset(160f, 118f), 8f)
            rotate(-25f, Offset(97f, 102f)) {
                drawOval(leaves.copy(alpha = .87f), Offset(50f, 48f), Size(94f, 108f))
            }
            rotate(25f, Offset(167f, 103f)) {
                drawOval(leaves.copy(alpha = .75f), Offset(120f, 48f), Size(94f, 108f))
            }
            drawOval(leaves, Offset(90f, 22f), Size(96f, 118f))
            drawOval(leaves, Offset(77f, 98f), Size(120f, 66f))
            val leaf = Path().apply {
                moveTo(65f, 216f); cubicTo(67f, 203f, 78f, 202f, 86f, 207f)
                cubicTo(86f, 216f, 77f, 219f, 65f, 216f); close()
            }
            drawPath(leaf, earth)
            rotate(35f, Offset(178f, 217f)) {
                drawOval(earth, Offset(168f, 213f), Size(20f, 8f))
            }
        }
    }
}
