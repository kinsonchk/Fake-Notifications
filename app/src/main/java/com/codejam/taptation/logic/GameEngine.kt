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

    fun isWhitelisted(packageName: String): Boolean = state.config.ruleFor(packageName) == AppRule.AlwaysImportant
    fun getHealth(): Int = refresh().health

    fun classify(context: NotificationContext): Classification =
        NotificationClassifier.assessTap(context, state.config).classification

    fun onTap(context: NotificationContext): TapResult {
        require(context.packageName.isNotBlank())
        val now = now()
        var next = recovered(state, now)
        if (!next.config.isActive(now)) {
            commit(next)
            return TapResult(TapOutcome.NoPenalty, state, "disabled")
        }
        val assessment = NotificationClassifier.assessTap(context, next.config)
        val classification = assessment.classification
        val before = next.health
        val outcome: TapOutcome
        val reason: String
        val alwaysBaitRule = next.config.ruleFor(context.packageName) == AppRule.AlwaysBait
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
                when {
                    next.isLockedOut(now) -> {
                        outcome = TapOutcome.NoPenalty
                        reason = "lockout"
                    }
                    // At 0 health every bait tap is caught (100%, no roll): no damage is left to
                    // take, but the catch, lockout and choice overlay still happen.
                    next.isDead || alwaysBaitRule ||
                        random() < (if (allowDebugOverrides) next.config.catchProbability else next.config.difficulty.probability) -> {
                        next = caught(next, next.config.level, now).let {
                            it.copy(pendingCatch = it.pendingCatch?.copy(packageName = context.packageName,
                                rule = assessment.reason, channel = context.channelName))
                        }
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
        return commit(if (!next.config.isActive(now) || next.isLockedOut(now)) next else caught(next, level, now))
    }

    fun refresh(): GameState = commit(recovered(state, now()))

    /**
     * When the state next changes on its own: the next recovery step, or the end of a timed
     * disable. Null when nothing is due (full health, dead, no recovery, disabled indefinitely).
     * Valid right after [refresh], which moves the recovery anchor up to the last finished step.
     */
    fun nextScheduledChangeMillis(): Long? {
        val config = state.config
        if (!config.active) return config.disabledUntilMillis
        if (state.isDead || state.health >= 100 || config.recoveryAmount == 0) return null
        return state.recoveryAnchorMillis + config.effectiveRecoveryIntervalMinutes(allowDebugOverrides) * MINUTE_MILLIS
    }

    fun updateConfig(config: GameConfig): GameState {
        val now = now()
        // Settle recovery under the old rate; don't apply a new rate to old partial hours.
        val next = recovered(state, now)
        val rateChanged = config.recoveryAmount != state.config.recoveryAmount ||
            config.effectiveRecoveryIntervalMinutes(allowDebugOverrides) !=
            state.config.effectiveRecoveryIntervalMinutes(allowDebugOverrides)
        return commit(next.copy(config = config,
            recoveryAnchorMillis = if (rateChanged) now else next.recoveryAnchorMillis))
    }

    fun setDebugRecoveryInterval(minutes: Int?): GameState {
        check(allowDebugOverrides) { "Debug recovery overrides are disabled" }
        require(minutes == null || minutes in setOf(1, 5, 15))
        return updateConfig(state.config.copy(debugRecoveryIntervalMinutes = minutes))
    }

    fun setActive(active: Boolean, disabledUntilMillis: Long? = null): GameState {
        val now = now()
        val next = recovered(state, now)
        return commit(next.copy(config = next.config.copy(active = active,
            disabledUntilMillis = if (active) null else disabledUntilMillis),
            recoveryAnchorMillis = now, pendingCatch = if (active) next.pendingCatch else null))
    }

    /** Settles a displayed catch once; continuing adds its saved extra penalty. */
    fun settleCatchChoice(id: String, continueOriginalApp: Boolean): GameState {
        val pending = state.pendingCatch?.takeIf { it.id == id } ?: return state
        val now = now()
        var next = recovered(state, now)
        val extra = if (continueOriginalApp) pending.continueExtraDamage else 0
        if (extra > 0 && next.health > 0) {
            next = withHealth(next, (next.health - extra).coerceAtLeast(0), now, "continue_after_catch")
        }
        return commit(next.copy(pendingCatch = null))
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

    private fun caught(source: GameState, level: CatchLevel, now: Long): GameState {
        val health = (source.health - source.config.damage(level)).coerceAtLeast(0)
        return withHealth(source, health, now, "catch", level).copy(
            totalCatches = source.totalCatches + 1,
            lastCatchMillis = now,
            lockoutUntilMillis = now + source.config.lockoutSeconds * 1000L,
            recoveryAnchorMillis = now,
            // Same choice for tap catches and forced (debug) catches. Continuing can't cost more
            // health than is left, so at 0 the overlay shows no extra penalty.
            pendingCatch = PendingCatch("$now-${source.totalCatches + 1}", null, minOf(CONTINUE_EXTRA_DAMAGE, health),
                healthBefore = source.health, healthAfter = health),
        )
    }

    private fun recovered(source: GameState, now: Long): GameState {
        if (!source.config.active) {
            val until = source.config.disabledUntilMillis
            if (until == null || now < until) return source.copy(lastObservedMillis = now, recoveryAnchorMillis = now)
            return recovered(source.copy(config = source.config.copy(active = true, disabledUntilMillis = null),
                recoveryAnchorMillis = maxOf(source.recoveryAnchorMillis, until)), now)
        }
        val intervalMillis = source.config.effectiveRecoveryIntervalMinutes(allowDebugOverrides) * MINUTE_MILLIS
        val intervals = (now - source.recoveryAnchorMillis).coerceAtLeast(0) / intervalMillis
        var next = source.copy(lastObservedMillis = now)
        if (intervals > 0) {
            if (!source.isDead) {
                val health = (source.health.toLong() + intervals * source.config.recoveryAmount)
                    .coerceAtMost(100).toInt()
                if (health != source.health) next = withHealth(next, health, now, "recovery")
            }
            next = next.copy(recoveryAnchorMillis = source.recoveryAnchorMillis + intervals * intervalMillis)
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
        const val CONTINUE_EXTRA_DAMAGE = 5
        const val HISTORY_LIMIT = 1000
        const val HOUR_MILLIS = 3_600_000L
        const val MINUTE_MILLIS = 60_000L
    }
}
