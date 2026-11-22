package com.codejam.taptation.ui.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MeadowMotionTest {
    @Test fun springMotionIsIndependentOfDisplayRefreshRate() {
        val sixty = MeadowMotion()
        val oneTwenty = MeadowMotion()
        repeat(120) { sixty.advance(1f / 60f, 1f, 340f, 170f, 95f) }
        repeat(240) { oneTwenty.advance(1f / 120f, 1f, 340f, 170f, 95f) }
        sixty.blades.zip(oneTwenty.blades).forEach { (a, b) -> assertEquals(a.bend, b.bend, .001f) }
    }

    @Test fun brushingBendsNearbyGrassThenSpringsBackToTheWind() {
        val brushed = MeadowMotion()
        val windOnly = MeadowMotion()
        repeat(120) {
            brushed.advance(1f / 60f, 1f, 340f, 170f, 95f)
            windOnly.advance(1f / 60f, 1f, 340f)
        }
        assertTrue(brushed.blades.zip(windOnly.blades).any { (a, b) -> abs(a.bend - b.bend) > 5f })
        repeat(180) {
            brushed.advance(1f / 60f, 1f, 340f)
            windOnly.advance(1f / 60f, 1f, 340f)
        }
        brushed.blades.zip(windOnly.blades).forEach { (a, b) -> assertEquals(a.bend, b.bend, .01f) }
    }
}
