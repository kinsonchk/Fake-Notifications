package com.codejam.taptation.debug

import kotlinx.coroutines.flow.StateFlow

/** State exposed by the app's real consequence logic for the debug panel. */
data class DemoControlState(
    val health: Int,
    val catchProbabilityPercent: Int,
    val catches: Int,
    val recoveryAmount: Int = 5,
    val recoveryIntervalMinutes: Int = 60,
    /** Health lost per catch at the selected level. */
    val catchDamage: Int = 10,
    /** Catch lockout (cooldown) in seconds. */
    val lockoutSeconds: Int = 15,
)

/**
 * Debug-only controls. Implementations must use the same domain logic/store used by
 * normal taps; debug UI must not edit persistence directly.
 */
interface DemoControls {
    val state: StateFlow<DemoControlState>

    fun setCatchProbability(percent: Int)
    fun setHealth(value: Int)
    fun forceCatch()
    fun advanceCleanTime(minutes: Long)
    fun setRecoveryIntervalForDebug(minutes: Int?)
    /** Health lost per catch (1–100); null restores the default. */
    fun setCatchDamage(damage: Int?)
    /** Catch lockout in seconds (1–3600); null restores the default. */
    fun setLockoutSeconds(seconds: Int?)
    fun resetDemoData()
}
