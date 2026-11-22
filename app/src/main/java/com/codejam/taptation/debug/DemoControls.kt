package com.codejam.taptation.debug

import kotlinx.coroutines.flow.StateFlow

/** State exposed by the app's real consequence logic for the debug panel. */
data class DemoControlState(
    val health: Int,
    val catchProbabilityPercent: Int,
    val catches: Int,
    val recoveryAmount: Int = 5,
    val recoveryIntervalMinutes: Int = 60,
)

/**
 * Debug-only controls. Person 2 should implement this with the same domain logic/store used by
 * normal taps; debug UI must not edit persistence directly.
 */
interface DemoControls {
    val state: StateFlow<DemoControlState>

    fun setCatchProbability(percent: Int)
    fun setHealth(value: Int)
    fun forceCatch()
    fun advanceCleanTime(minutes: Long)
    fun setRecoveryIntervalForDebug(minutes: Int?)
    fun resetDemoData()
}
