package com.codejam.taptation.ui.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.codejam.taptation.logic.meadow.MeadowState
import com.codejam.taptation.logic.meadow.ValleyStage
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Backdrop behind the grass. Classic is the original mound; Valley reveals the valley with clean time. */
enum class MeadowScenery { Classic, Valley }

/** What the valley shows this frame: camera, mist line and how far each landmark has grown in (0..1). */
internal class ValleyView(val zoom: Float, val fog: Float, val path: Float, val bridge: Float, val cottage: Float, val windmill: Float)

/** Presentation of [ValleyStage]: camera zoom (about the grass line) and mist line (fraction of height), eased between stages. */
internal object ValleyCamera {
    private val ZOOM = floatArrayOf(2.6f, 2f, 1.45f, 1.2f, 1.13f, 1.04f, 1f, 1f)
    private val FOG = floatArrayOf(.6f, .45f, .27f, .18f, .13f, .03f, -.12f, -.3f)
    fun zoom(cleanMillis: Long) = interpolate(ZOOM, cleanMillis)
    fun fog(cleanMillis: Long) = interpolate(FOG, cleanMillis)
    private fun interpolate(values: FloatArray, clean: Long): Float {
        val stage = ValleyStage.at(clean)
        val next = stage.next ?: return values[stage.ordinal]
        val from = stage.minutes * MeadowState.MINUTE
        val u = ((clean - from).toFloat() / ((next.minutes - stage.minutes) * MeadowState.MINUTE)).coerceIn(0f, 1f)
        val eased = if (u < .5f) 2 * u * u else 1 - (-2 * u + 2).pow(2) / 2
        return values[stage.ordinal] + (values[next.ordinal] - values[stage.ordinal]) * eased
    }
}

/**
 * Rolling hills, a river, distant trees and earned landmarks behind the grass. Presentation only: low
 * health dries the hills and narrows the river; time drifts clouds, ripples, smoke and the mist puffs.
 * Units are the canvas's logical units; the grass and creatures are drawn by [MeadowCanvas] on top.
 */
internal class ValleyScenery {
    private class Hill(val rise: Float, val amp: Float, val f1: Float, val p1: Float, val f2: Float, val p2: Float) {
        fun y(x: Float, width: Float, ground: Float): Float {
            val u = x / width * TAU
            return ground - rise + amp * (.6f * sin(u * f1 + p1) + .4f * sin(u * f2 + p2))
        }
    }

    private class Palette(val skyTop: Color, val skyLow: Color, val far: Color, val mid: Color, val near: Color,
        val field: Color, val conifer: Color, val bushDark: Color, val bushLight: Color, val water: Color,
        val waterLight: Color, val dry: Color, val cloud: Color, val sun: Color, val path: Color, val wood: Color,
        val woodDark: Color, val wall: Color, val roof: Color, val window: Color, val puff: Color)

    // Rises are measured up from the grass line; the far ridges sit above the tallest blades.
    private val farHill = Hill(170f, 9f, 1.3f, .7f, 2.7f, 2.1f)
    private val midHill = Hill(142f, 11f, .9f, 2.4f, 2.1f, .3f)
    private val nearHill = Hill(114f, 8f, 1.1f, 4f, 2.9f, 1.2f)
    private val fieldHill = Hill(22f, 3f, .7f, 1f, 1.9f, 2f)
    private val far = Path(); private val mid = Path(); private val near = Path(); private val field = Path()
    private val river = Path(); private val shape = Path()
    private var builtWidth = -1f; private var builtGround = -1f

    private fun build(width: Float, ground: Float, bottom: Float) {
        if (width == builtWidth && ground == builtGround) return
        builtWidth = width; builtGround = ground
        for ((path, hill) in listOf(far to farHill, mid to midHill, near to nearHill, field to fieldHill)) {
            path.reset(); path.moveTo(-2f, bottom)
            var x = -2f
            while (x <= width + 2f) { path.lineTo(x, hill.y(x, width, ground)); x += 3f }
            path.lineTo(width + 2f, hill.y(width + 2f, width, ground)); path.lineTo(width + 2f, bottom); path.close()
        }
    }

    fun draw(scope: DrawScope, scale: Float, width: Float, ground: Float, height: Float, growth: Float,
        dark: Boolean, background: Color, petal: Color, gold: Color, time: Float, view: ValleyView) = with(scope) {
        val p = if (dark) NIGHT else DAY
        build(width, ground, height + 2f)
        val drought = (1f - growth) * .6f
        fun land(c: Color) = lerp(c, p.dry, drought)
        val water = .45f + .55f * growth
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            // Camera: zoom about the grass line so the foreground stays put while the valley opens above it.
            withTransform({ scale(view.zoom, view.zoom, pivot = Offset(width / 2, ground)) }) {
                val horizon = ground - 150f
                val sky = Brush.verticalGradient(listOf(p.skyTop, p.skyLow), startY = 0f, endY = horizon)
                drawRect(sky, topLeft = Offset(-width, -height), size = Size(width * 3, horizon + 50f + height))
                val sunX = width * .8f
                if (!dark) {
                    drawCircle(p.sun, 21f, Offset(sunX, 30f), alpha = .28f)
                    drawCircle(p.sun, 12f, Offset(sunX, 30f))
                } else {
                    for (i in 0..15) {
                        val sx = ((i * 73 + 19) % 100) / 100f * width; val sy = 6f + (i * 37) % 62
                        drawCircle(STAR, .9f + (i % 3) * .3f, Offset(sx, sy), alpha = .35f + .3f * sin(time * 1.3f + i))
                    }
                    drawCircle(p.sun, 9f, Offset(sunX, 28f))
                    drawCircle(sky, 8f, Offset(sunX + 4.5f, 25f))   // crescent: cut with the sky itself
                }
                for (i in 0..2) {
                    val span = width + 70f
                    val cx = ((CLOUD_X[i] * width + time * (2.2f + i * .8f)) % span + span) % span - 35f
                    cloud(cx, CLOUD_Y[i], CLOUD_S[i], p.cloud, if (dark) .55f else .92f)
                }
                drawPath(far, land(p.far))
                for (u in CONIFERS) { val x = u * width; conifer(x, farHill.y(x, width, ground) + 3f, .75f, land(p.conifer)) }
                windmill(view.windmill, width * .78f, farHill.y(width * .78f, width, ground) + 4f, p, time)
                drawPath(mid, land(p.mid))
                for (u in MID_BUSHES) { val x = u * width; bush(x, midHill.y(x, width, ground) + 3f, 5.5f, land(p.bushDark), land(p.bushLight)) }
                cottage(view.cottage, width * .24f, midHill.y(width * .24f, width, ground) + 7f, p, dark, time)
                drawPath(near, land(p.near))
                // The river meanders towards the viewer: thin far left, wider near right; drought lowers it.
                river.reset()
                for (k in 0..STEPS) { val t = k / STEPS.toFloat(); val x = -12f + (width + 24f) * t
                    if (k == 0) river.moveTo(x, riverY(t, ground) - half(t, water)) else river.lineTo(x, riverY(t, ground) - half(t, water)) }
                for (k in STEPS downTo 0) { val t = k / STEPS.toFloat(); river.lineTo(-12f + (width + 24f) * t, riverY(t, ground) + half(t, water)) }
                river.close()
                drawPath(river, p.water)
                for (i in 0..8) {
                    val t = ((i / 9f) + time * .012f) % 1f
                    val x = -12f + (width + 24f) * t; val y = riverY(t, ground) - half(t, water) * .3f; val len = 2f + 4f * t
                    drawLine(p.waterLight, Offset(x - len, y), Offset(x + len, y), strokeWidth = .9f, cap = StrokeCap.Round, alpha = .8f)
                }
                path(view, width, ground, land(p.path))
                bridge(view.bridge, width * BRIDGE_U, ground, water, p)
                for (u in NEAR_TREES) { val x = u * width; bush(x, nearHill.y(x, width, ground) + 4f, if (u == .2f) 6f else 8f, land(p.bushDark), land(p.bushLight)) }
                drawPath(field, Brush.verticalGradient(listOf(land(p.field), land(p.field), background), startY = ground - 26f, endY = height))
                // Daisies bloom on the near slope only at high health.
                val shown = (((growth - .7f) / .3f).coerceIn(0f, 1f) * DAISIES).toInt()
                for (i in 0 until shown) {
                    val x = ((i * 47 + 11) % 100) / 100f * width
                    val y = nearHill.y(x, width, ground) + 5f + ((i * 29) % 100) / 100f * 20f
                    drawCircle(petal, 1.3f, Offset(x, y)); drawCircle(gold, .5f, Offset(x, y))
                }
            }
            // Mist in the page colour: opaque above the line, clearing below it, with drifting puffs so it reads as fog.
            if (view.fog > -.25f) {
                val edge = view.fog * height
                if (edge - 55f > 0f) drawRect(background, topLeft = Offset(0f, -10f), size = Size(width, edge - 45f))
                drawRect(Brush.verticalGradient(listOf(background, background.copy(alpha = 0f)), startY = edge - 55f, endY = edge + 25f),
                    topLeft = Offset(0f, -10f), size = Size(width, (edge + 35f).coerceAtLeast(0f)))
                for (i in 0..8) {
                    val span = width + 80f
                    val x = ((i / 9f) * span + time * (3 + i % 3)) % span - 40f
                    val y = edge - 18f + sin(time * .4f + i * 1.7f) * 5f + (i % 2) * 8f
                    val r = 22f + (i * 7) % 14
                    drawCircle(Brush.radialGradient(listOf(p.puff, p.puff.copy(alpha = 0f)), Offset(x, y), r), r, Offset(x, y))
                }
            }
        }
    }

    private fun riverY(t: Float, ground: Float) = ground - 104f + 22f * t + 6f * sin(t * TAU * 1.15f + .6f)
    private fun half(t: Float, water: Float) = (1.6f + 5.4f * t) * water
    private fun riverT(x: Float, width: Float) = (x + 12f) / (width + 24f)

    /** Sandy path from the foreground to the bridge, grown out from the viewer; beyond it climbs to the cottage. */
    private fun DrawScope.path(view: ValleyView, width: Float, ground: Float, color: Color) {
        if (view.path <= 0f) return
        val bx = width * BRIDGE_U; val bt = riverT(bx, width); val top = riverY(bt, ground) + half(bt, 1f) + 1f
        val n = 24; val shown = maxOf(2, (n * view.path).toInt())
        shape.reset()
        for (k in 0..shown) { val u = k / n.toFloat(); val x = pathX(u, width, bx); val y = ground + 8f + (top - ground - 8f) * u
            val w = 9f + (2.2f - 9f) * u; if (k == 0) shape.moveTo(x - w, y) else shape.lineTo(x - w, y) }
        for (k in shown downTo 0) { val u = k / n.toFloat(); shape.lineTo(pathX(u, width, bx) + 9f + (2.2f - 9f) * u, ground + 8f + (top - ground - 8f) * u) }
        shape.close()
        drawPath(shape, color)
        if (view.bridge > .9f && view.cottage > 0f) {
            val tt = riverY(bt, ground) - half(bt, 1f) - 1f; val cx = width * .26f; val cy = midHill.y(cx, width, ground) + 8f
            shape.reset(); shape.moveTo(bx - 1.6f, tt); shape.quadraticTo(width * .5f, tt - 10f, cx, cy); shape.lineTo(cx + 2f, cy)
            shape.quadraticTo(width * .52f, tt - 9f, bx + 1.6f, tt); shape.close()
            drawPath(shape, color, alpha = view.cottage)
        }
    }
    private fun pathX(u: Float, width: Float, bx: Float) = width * .57f + (bx - width * .57f) * u + sin(u * 5f) * 14f * (1 - u)

    private fun DrawScope.bridge(grown: Float, x: Float, ground: Float, water: Float, p: Palette) {
        if (grown <= 0f) return
        val t = riverT(x, builtWidth); val y = riverY(t, ground); val h = half(t, water) + 2.5f
        withTransform({ scale(1f, grown, pivot = Offset(x, y)) }) {
            drawRect(p.woodDark, Offset(x - 6f, y - h), Size(12f, h * 2))
            var k = -h; while (k < h) { drawRect(p.wood, Offset(x - 5.2f, y + k), Size(10.4f, 1.5f)); k += 2.2f }
            for (s in listOf(-6.2f, 6.2f)) {
                shape.reset(); shape.moveTo(x + s, y - h - 2f); shape.quadraticTo(x + s + (if (s < 0) -1.5f else 1.5f), y, x + s, y + h + 2f)
                drawPath(shape, p.woodDark, style = Stroke(.9f))
            }
        }
    }

    private fun DrawScope.cottage(grown: Float, x: Float, y: Float, p: Palette, dark: Boolean, time: Float) {
        if (grown <= 0f) return
        withTransform({ scale(.4f + .6f * grown, .4f + .6f * grown, pivot = Offset(x, y)) }) {
            drawRect(p.wall, Offset(x - 9f, y - 11f), Size(18f, 11f), alpha = grown)
            shape.reset(); shape.moveTo(x - 11f, y - 10f); shape.lineTo(x, y - 19f); shape.lineTo(x + 11f, y - 10f); shape.close()
            drawPath(shape, p.roof, alpha = grown)
            drawRect(p.roof, Offset(x + 4f, y - 19f), Size(3f, 6f), alpha = grown)
            drawRect(p.woodDark, Offset(x - 2f, y - 6f), Size(4f, 6f), alpha = grown)
            drawRect(p.window, Offset(x - 7f, y - 8f), Size(3.5f, 3f), alpha = grown)
            drawRect(p.window, Offset(x + 3.5f, y - 8f), Size(3.5f, 3f), alpha = grown)
            if (dark) drawCircle(p.window, 14f, Offset(x, y - 6f), alpha = .08f * grown)
            for (k in 0..3) { val age = (time * .25f + k / 4f) % 1f
                drawCircle(p.cloud, 1.5f + age * 2.6f, Offset(x + 5.5f + sin(age * 5 + k) * 2 + age * 4, y - 21f - age * 16f), alpha = (1 - age) * .7f * grown) }
        }
    }

    private fun DrawScope.windmill(grown: Float, x: Float, y: Float, p: Palette, time: Float) {
        if (grown <= 0f) return
        val s = .7f * (.4f + .6f * grown)
        withTransform({ scale(s, s, pivot = Offset(x, y)) }) {
            shape.reset(); shape.moveTo(x - 5f, y); shape.lineTo(x - 3f, y - 20f); shape.lineTo(x + 3f, y - 20f); shape.lineTo(x + 5f, y); shape.close()
            drawPath(shape, p.wall, alpha = grown)
            shape.reset(); shape.moveTo(x - 4f, y - 20f); shape.lineTo(x, y - 25f); shape.lineTo(x + 4f, y - 20f); shape.close()
            drawPath(shape, p.roof, alpha = grown)
            val hub = Offset(x, y - 21f)
            for (k in 0..3) withTransform({ rotate(time * 46f + k * 90f, pivot = hub) }) {
                drawRect(p.woodDark, Offset(x - .6f, y - 21f), Size(1.2f, 14f), alpha = grown)
                drawRect(p.wall, Offset(x + .6f, y - 18f), Size(3f, 10f), alpha = grown)
            }
        }
    }

    private fun DrawScope.cloud(x: Float, y: Float, s: Float, color: Color, alpha: Float) {
        drawCircle(color, 6f * s, Offset(x - 9f * s, y), alpha = alpha)
        drawCircle(color, 8.5f * s, Offset(x, y - 3.5f * s), alpha = alpha)
        drawCircle(color, 6.5f * s, Offset(x + 9f * s, y - .5f * s), alpha = alpha)
        drawRoundRect(color, Offset(x - 15f * s, y - 1f * s), Size(30f * s, 7f * s), CornerRadius(3.5f * s), alpha = alpha)
    }

    private fun DrawScope.conifer(x: Float, y: Float, s: Float, color: Color) {
        drawLine(color, Offset(x, y), Offset(x, y - 4f * s), strokeWidth = 1.2f * s)
        for (k in 0..2) {
            val top = y - 17f * s + k * 4.2f * s; val half = (2.6f + k * 1.7f) * s
            shape.reset(); shape.moveTo(x, top); shape.lineTo(x + half, top + 7.5f * s); shape.lineTo(x - half, top + 7.5f * s); shape.close()
            drawPath(shape, color)
        }
    }

    private fun DrawScope.bush(x: Float, y: Float, r: Float, dark: Color, light: Color) {
        drawCircle(dark, r * .8f, Offset(x - r * .7f, y - r * .5f))
        drawCircle(dark, r * .8f, Offset(x + r * .75f, y - r * .45f))
        drawCircle(dark, r, Offset(x, y - r * .9f))
        drawCircle(light, r * .55f, Offset(x + r * .3f, y - r * 1.3f))
    }

    private companion object {
        const val TAU = (2 * PI).toFloat()
        const val DAISIES = 26
        const val STEPS = 36
        /** Clear of the oak (.48) and below the robin's nest (.75). */
        const val BRIDGE_U = .66f
        val STAR = Color(0xFFE5ECE2)
        val CLOUD_X = floatArrayOf(.12f, .5f, .86f); val CLOUD_Y = floatArrayOf(22f, 40f, 16f); val CLOUD_S = floatArrayOf(.9f, .7f, 1f)
        val CONIFERS = floatArrayOf(.06f, .1f, .14f, .6f, .64f, .69f, .93f)
        val MID_BUSHES = floatArrayOf(.36f, .43f, .78f, .84f)
        val NEAR_TREES = floatArrayOf(.05f, .95f, .2f)
        val DAY = Palette(Color(0xFFBEDBE2), Color(0xFFEEF3E6), Color(0xFFB8D3A2), Color(0xFF97C17B), Color(0xFF78AC5F),
            Color(0xFF64A04F), Color(0xFF4E8365), Color(0xFF5D9350), Color(0xFF86B86A), Color(0xFF6EAEC7),
            Color(0xFFAAD6E2), Color(0xFFCDB880), Color(0xFFFFFFFF), Color(0xFFF5DF9C), Color(0xFFE3CF9B), Color(0xFFA87A4F),
            Color(0xFF7E5A39), Color(0xFFF1E6D0), Color(0xFFB9674A), Color(0xFF8FB7C4), Color(0xCCFFFFFF))
        val NIGHT = Palette(Color(0xFF16252F), Color(0xFF1E2D28), Color(0xFF2A4033), Color(0xFF304D3A), Color(0xFF375F42),
            Color(0xFF2D573A), Color(0xFF26453A), Color(0xFF2E5137), Color(0xFF477954), Color(0xFF2D5B6B),
            Color(0xFF4F8696), Color(0xFF5A5038), Color(0xFF34443F), Color(0xFFE9E3C4), Color(0xFF6D6346), Color(0xFF7A5B3E),
            Color(0xFF5A422D), Color(0xFF5E584A), Color(0xFF7A4A38), Color(0xFFF2CF7A), Color(0xAA2C3833))
    }
}
