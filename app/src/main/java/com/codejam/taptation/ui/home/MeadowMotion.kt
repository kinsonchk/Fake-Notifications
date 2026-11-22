package com.codejam.taptation.ui.home

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** Presentation-only spring simulation ported from grass-screen-mockup.html. Units are mockup pixels. */
internal class MeadowMotion {
    class Blade(index: Int) {
        val u = (index + .3f + ((index * 37) % 10) * .05f) / COUNT
        val heightFactor = .55f + ((index * 53) % 100) / 210f
        val lean = (((index * 29) % 100) / 100f - .5f) * .24f
        val phase = index * .42f
        val layer = index % 3
        // Spread the disappearing blades across the whole meadow as health drops.
        val activationRank = (index * 31) % COUNT
        var bend = restBend(1f)
        var velocity = 0f
        fun restBend(growth: Float) = sin(phase + u * 4f) * (2.5f + growth * 5f) + lean * 18f
        fun height(growth: Float) = (16f + growth * 90f) * heightFactor
        val baseY get() = HEIGHT * .86f + 4f - sin(u * PI).toFloat() * 8f + layer * 2f
    }

    val blades = Array(COUNT) { Blade(it) }
    private var windPhase = 0f
    private var remainder = 0f

    /** Fixed 60Hz physics retains the HTML's feel on both 60Hz and higher-refresh displays. */
    fun advance(seconds: Float, growth: Float, width: Float, touchX: Float = Float.NaN, touchY: Float = Float.NaN) {
        if (!seconds.isFinite() || seconds <= 0f || width <= 0f) return
        remainder += seconds.coerceAtMost(.1f)
        val health = growth.coerceIn(0f, 1f)
        while (remainder + .000001f >= STEP) {
            remainder = (remainder - STEP).coerceAtLeast(0f)
            windPhase = (windPhase + 1.92f * STEP) % (2f * PI.toFloat())
            blades.forEach { blade ->
                var target = sin(windPhase + blade.phase + blade.u * 4f) * (2.5f + health * 5f) + blade.lean * 18f
                if (touchX.isFinite() && touchY.isFinite()) {
                    val dx = blade.u * width - touchX
                    val dy = blade.baseY - blade.height(health) * .5f - touchY
                    val distance = hypot(dx, dy)
                    if (distance < 58f) target += (if (dx >= 0f) 1f else -1f) * (1f - distance / 58f) * 34f
                }
                blade.velocity = (blade.velocity + (target - blade.bend) * .14f) * .78f
                blade.bend += blade.velocity
            }
        }
    }

    companion object {
        const val COUNT = 78
        const val HEIGHT = 165f
        private const val STEP = 1f / 60f
    }
}
