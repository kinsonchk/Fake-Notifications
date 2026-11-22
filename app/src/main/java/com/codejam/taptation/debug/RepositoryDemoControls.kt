package com.codejam.taptation.debug

import com.codejam.taptation.logic.CatchLevel
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.GameState
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [DemoControls] backed by the real [GameRepository], so the demo panel goes through the same
 * engine and store as normal taps. Every call is queued on the repository's worker; failures show
 * up in [GameRepository.error].
 */
class RepositoryDemoControls(
    private val repository: GameRepository,
    scope: CoroutineScope,
) : DemoControls {
    override val state: StateFlow<DemoControlState> = repository.state
        .map { it.toDemoState() }
        .stateIn(scope, SharingStarted.Eagerly, repository.state.value.toDemoState())

    override fun setCatchProbability(percent: Int) {
        val probability = percent.coerceIn(0, 100) / 100.0
        repository.setDebugCatchProbability(probability)
    }

    override fun setHealth(value: Int) {
        repository.setHealthForDebug(value.coerceIn(0, 100))
    }

    /** Same damage and lockout as a real catch; ignored while dead or locked out. */
    override fun forceCatch() {
        repository.applyCatch(repository.state.value?.config?.level ?: CatchLevel.Mild)
    }

    override fun advanceCleanTime(minutes: Long) {
        repository.advanceTimeForDebug(minutes.coerceAtLeast(0))
    }

    override fun setRecoveryIntervalForDebug(minutes: Int?) {
        repository.setDebugRecoveryInterval(minutes)
    }

    override fun resetDemoData() {
        repository.resetDemoDataForDebug()
    }
}

private fun GameState?.toDemoState(): DemoControlState {
    val state = this ?: GameState(config = GameConfig())
    return DemoControlState(
        health = state.health,
        catchProbabilityPercent = (state.config.catchProbability * 100).roundToInt(),
        catches = state.totalCatches.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        recoveryAmount = state.config.recoveryAmount,
        recoveryIntervalMinutes = state.config.effectiveRecoveryIntervalMinutes(debugBuild = true),
    )
}
