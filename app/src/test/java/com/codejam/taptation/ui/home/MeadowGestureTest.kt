package com.codejam.taptation.ui.home

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class MeadowGestureTest {
    @Test fun closedTurnsWorkInEitherDirectionButBrushingDoesNotTriggerVortices() {
        val circle=(0..48).map { val a=it*2*PI/48;Offset(100+cos(a).toFloat()*45,100+sin(a).toFloat()*45) }
        assertTrue(isMeadowCircle(circle));assertTrue(isMeadowCircle(circle.reversed()))
        assertFalse(isMeadowCircle(circle.take(20)))
        assertFalse(isMeadowCircle((0..48).map{Offset(it*3f,100f)}))
        assertFalse(isMeadowCircle(circle.map{it*.2f}))
    }
}
