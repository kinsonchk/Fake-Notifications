package com.codejam.taptation.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/** Ephemeral play only. None of these effects award health or earned growth. */
internal class MeadowEffects {
    var revision by mutableIntStateOf(0)
    var note by mutableStateOf("Brush the grass. Circle for wind. Pull up from the soil to peek below.")
    private val epoch = System.nanoTime()
    val time: Float get() = (System.nanoTime() - epoch) / 1_000_000_000f
    var gust = 0f
    var vortex = Offset.Unspecified
    var vortexUntil = -1f
    var rainUntil = -1f
    var seeds = Offset.Unspecified
    var seedsUntil = -1f
    var butterflyUntil = -1f
    var squirrelUntil = -1f
    var birdUntil = -1f
    var glowUntil = -1f
    var beetleUntil = -1f
    var antParcel = 0
    var wormUntil = -1f
    var bubbleAt = -10f
    /** Mushroom whose fungus thread carries a light pulse, and when it started. */
    var pulseFrom = -1
    var pulseAt = -10f
    var dragVelocity = 0f
    fun tick(dt: Float) { gust *= kotlin.math.exp(-3.7f * dt); dragVelocity *= .9f }
    fun whirlwind(at: Offset) { vortex = at; vortexUntil = time + 5; gust = 1f; say("A little whirlwind. The butterfly is riding it.") }
    fun rain() { rainUntil = time + 5; say("A passing shower, just for play.") }
    fun scatter(at: Offset) { seeds = at; seedsUntil = time + 3; revision++ }
    fun flower(at: Offset) { scatter(at); butterflyUntil = time + 7; say("A butterfly came to your flowers.") }
    fun squirrel() { squirrelUntil = time + 8; say("The squirrel is checking on its acorn.") }
    fun say(text: String) { note = text; revision++ }
}

/** A closed turn, rather than arbitrary back-and-forth brushing, creates a whirlwind. */
internal fun isMeadowCircle(points: List<Offset>): Boolean {
    if (points.size < 16) return false
    val minX = points.minOf { it.x }; val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }; val maxY = points.maxOf { it.y }
    if (maxX - minX < 40 || maxY - minY < 40) return false
    if ((points.first() - points.last()).getDistance() > maxOf(maxX - minX, maxY - minY) * .6f) return false
    val center = Offset((minX + maxX) / 2, (minY + maxY) / 2)
    var turn = 0f
    points.zipWithNext { a, b ->
        var angle = atan2(b.y - center.y, b.x - center.x) - atan2(a.y - center.y, a.x - center.x)
        if (angle > PI) angle -= (2 * PI).toFloat()
        if (angle < -PI) angle += (2 * PI).toFloat()
        turn += angle
    }
    return abs(turn) > 5
}
