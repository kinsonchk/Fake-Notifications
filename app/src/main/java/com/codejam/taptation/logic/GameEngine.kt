package com.codejam.taptation.logic

/** Single-thread confined. All Android I/O is supplied through GameStore. */
class GameEngine(
    private val store: GameStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: () -> Double = { kotlin.random.Random.nextDouble() },
    private val allowDebugOverrides: Boolean = true,
) {
    var state: GameState = store.load() ?: clock().let {
        GameState(recoveryAnchorMillis = it, lastObservedMillis = it)
    }
        private set

    fun isWhitelisted(packageName: String): Boolean = packageName in state.config.whitelistedPackages
    fun getHealth(): Int = refresh().health

    fun classify(context: NotificationContext): Classification =
        NotificationClassifier.assess(context, state.config).classification

    fun onTap(context: NotificationContext): TapResult {
        require(context.packageName.isNotBlank())
        val now = now()
        var next = recovered(state, now)
        if (!next.config.isActive(now)) {
            commit(next)
            return TapResult(TapOutcome.NoPenalty, state, "disabled")
        }
        val assessment = if (context.ageMillis != null && context.ageMillis > next.config.freshnessWindowMillis)
            Assessment(Classification.Unknown, "stale")
        else NotificationClassifier.assess(context, next.config)
        val classification = assessment.classification
        val before = next.health
        val outcome: TapOutcome
        val reason: String
        when {
            classification == Classification.Important -> {
                outcome = TapOutcome.Bypass
                reason = assessment.reason
            }
            classification == Classification.Unknown -> {
                outcome = TapOutcome.NoPenalty
                reason = assessment.reason
            }
            else -> {
                next = next.copy(recoveryAnchorMillis = now)
                when {
                    next.isDead -> {
                        outcome = TapOutcome.NoPenalty
                        reason = "dead"
                    }
                    next.isLockedOut(now) -> {
                        outcome = TapOutcome.NoPenalty
                        reason = "lockout"
                    }
                    random() < (if (allowDebugOverrides) next.config.catchProbability else next.config.difficulty.probability) -> {
                        next = caught(next, next.config.level, now)
                            .copy(pendingCatch = PendingCatch("$now-${next.totalCatches + 1}", context.packageName))
                        outcome = TapOutcome.Caught
                        reason = "caught"
                    }
                    else -> {
                        outcome = TapOutcome.NoPenalty
                        reason = "roll_passed"
                    }
                }
            }
        }
        val record = TapRecord(now, context.packageName, classification, outcome, before,
            next.health, before - next.health, reason, context.ageMillis, assessment.reason)
        next = next.copy(
            tapsPerApp = next.tapsPerApp + (context.packageName to
                ((next.tapsPerApp[context.packageName] ?: 0) + 1)),
            taps = (next.taps + record).takeLast(HISTORY_LIMIT),
        )
        commit(next)
        return TapResult(outcome, state, reason, classification, assessment.reason)
    }

    fun onTap(packageName: String): TapResult = onTap(NotificationContext(packageName))
    fun onTap(packageName: String, ageMillis: Long): TapResult =
        onTap(NotificationContext(packageName, ageMillis = ageMillis))

    /** Already called by onTap on a successful roll. UI must not call it again for Caught. */
    fun applyCatch(level: CatchLevel): GameState {
        val now = now()
        val next = recovered(state, now)
        return commit(if (!next.config.isActive(now) || next.isDead || next.isLockedOut(now)) next else caught(next, level, now))
    }

    fun refresh(): GameState = commit(recovered(state, now()))

    fun updateConfig(config: GameConfig): GameState {
        val now = now()
        // Settle recovery under the old rate; don't apply a new rate to old partial hours.
        val next = recovered(state, now)
        return commit(next.copy(config = config,
            recoveryAnchorMillis = if (config.recoveryPerHour != state.config.recoveryPerHour)
                now else next.recoveryAnchorMillis))
    }

    fun setActive(active: Boolean, disabledUntilMillis: Long? = null): GameState {
        val now = now()
        val next = recovered(state, now)
        return commit(next.copy(config = next.config.copy(active = active,
            disabledUntilMillis = if (active) null else disabledUntilMillis),
            recoveryAnchorMillis = now, pendingCatch = if (active) next.pendingCatch else null))
    }

    fun dismissCatch(id: String): GameState =
        if (state.pendingCatch?.id == id) commit(state.copy(pendingCatch = null)) else state

    fun reset(): GameState {
        check(state.isDead) { "Reset is available only after death" }
        val now = now()
        return commit(withHealth(state, 100, now, "reset").copy(
            recoveryAnchorMillis = now, lastObservedMillis = now, lockoutUntilMillis = 0, pendingCatch = null))
    }

    /** Debug integration only; production UI should use reset(). */
    fun setHealthForDebug(health: Int): GameState {
        require(health in 0..100)
        val now = now()
        return commit(withHealth(state, health, now, "debug").copy(
            recoveryAnchorMillis = now, lastObservedMillis = now, lockoutUntilMillis = 0, pendingCatch = null))
    }

    /**
     * Debug only: acts as if [millis] of clean time passed. Recovery and the lockout deadline move
     * forward; taps, catches and settings are untouched.
     */
    fun advanceTimeForDebug(millis: Long): GameState {
        require(millis >= 0)
        val now = now()
        val shifted = state.copy(
            recoveryAnchorMillis = state.recoveryAnchorMillis - millis,
            lockoutUntilMillis = (state.lockoutUntilMillis - millis).coerceAtLeast(0),
        )
        return commit(recovered(shifted, now))
    }

    /** Debug only: full health, no lockout, empty history and stats. Settings are kept. */
    fun resetDemoDataForDebug(): GameState {
        val now = now()
        return commit(GameState(config = state.config, recoveryAnchorMillis = now, lastObservedMillis = now))
    }

    private fun caught(source: GameState, level: CatchLevel, now: Long): GameState =
        withHealth(source, (source.health - source.config.damage(level)).coerceAtLeast(0),
            now, "catch", level).copy(
            totalCatches = source.totalCatches + 1,
            lastCatchMillis = now,
            lockoutUntilMillis = now + source.config.lockoutSeconds * 1000L,
            recoveryAnchorMillis = now,
            pendingCatch = PendingCatch("$now-${source.totalCatches + 1}", null),
        )

    private fun recovered(source: GameState, now: Long): GameState {
        if (!source.config.active) {
            val until = source.config.disabledUntilMillis
            if (until == null || now < until) return source.copy(lastObservedMillis = now, recoveryAnchorMillis = now)
            return recovered(source.copy(config = source.config.copy(active = true, disabledUntilMillis = null),
                recoveryAnchorMillis = maxOf(source.recoveryAnchorMillis, until)), now)
        }
        val hours = (now - source.recoveryAnchorMillis).coerceAtLeast(0) / HOUR_MILLIS
        var next = source.copy(lastObservedMillis = now)
        if (hours > 0) {
            if (!source.isDead) {
                val health = (source.health.toLong() + hours * source.config.recoveryPerHour)
                    .coerceAtMost(100).toInt()
                if (health != source.health) next = withHealth(next, health, now, "recovery")
            }
            next = next.copy(recoveryAnchorMillis = source.recoveryAnchorMillis + hours * HOUR_MILLIS)
        }
        return next
    }

    private fun withHealth(source: GameState, health: Int, now: Long, reason: String,
        level: CatchLevel? = null): GameState = source.copy(
        health = health,
        healthHistory = (source.healthHistory + HealthRecord(now, source.health, health, reason, level))
            .takeLast(HISTORY_LIMIT),
    )

    // Backward wall-clock changes cannot create negative recovery or shorten a saved lockout.
    private fun now(): Long = maxOf(clock(), state.lastObservedMillis)

    private fun commit(next: GameState): GameState {
        if (next != state) {
            store.save(next)
            state = next
        }
        return state
    }

    companion object {
        const val HISTORY_LIMIT = 1000
        const val HOUR_MILLIS = 3_600_000L
    }
}
