package com.codejam.taptation.logic

import org.junit.Assert.*
import org.junit.Test

class RevisedFlowTest {
    private class Store : GameStore {
        var snapshot: String? = null
        override fun load() = snapshot?.let(GameStateCodec::decode)
        override fun save(state: GameState) { snapshot = GameStateCodec.encode(state) }
    }
    // "Let us decide" needs an AI verdict to roll; without one the tap is Unknown (no penalty).
    private val context = NotificationContext("social.app", title = "An update", text = "Something new",
        category = "social", isConversation = false, ageMillis = 0, judged = Classification.Bait)

    @Test fun difficultyHasFixedChancesAndReleaseIgnoresDebugOverride() {
        assertEquals(listOf(0.2, 0.4, 0.7), Difficulty.entries.map { it.probability })
        val engine = GameEngine(Store(), { 1000 }, { 0.3 }, allowDebugOverrides = false)
        engine.updateConfig(engine.state.config.copy(debugCatchProbability = 1.0))
        assertEquals(TapOutcome.NoPenalty, engine.onTap(context).outcome)
        engine.updateConfig(engine.state.config.copy(difficulty = Difficulty.Medium))
        assertEquals(TapOutcome.Caught, engine.onTap(context).outcome)
    }

    @Test fun debugOverridesStillReachZeroAndOne() {
        val engine = GameEngine(Store(), { 1000 }, { 0.5 })
        engine.updateConfig(engine.state.config.copy(debugCatchProbability = 0.0))
        assertEquals(TapOutcome.NoPenalty, engine.onTap(context).outcome)
        engine.updateConfig(engine.state.config.copy(debugCatchProbability = 1.0))
        assertEquals(TapOutcome.Caught, engine.onTap(context).outcome)
    }

    @Test fun staleTapsSkipClassifierRollAndCleanTimerReset() {
        var rolls = 0
        val engine = GameEngine(Store(), { 1000 }, { rolls++; 0.0 })
        engine.updateConfig(engine.state.config.copy(appRules = mapOf("social.app" to AppRule.AlwaysImportant)))
        val anchor = engine.state.recoveryAnchorMillis
        val result = engine.onTap(context.copy(ageMillis = 600_001))
        assertEquals("stale", result.reason)
        assertEquals(Classification.Unknown, result.classification)
        assertEquals(0, rolls)
        assertEquals(anchor, engine.state.recoveryAnchorMillis)
        assertEquals(100, engine.state.health)
        assertEquals("stale", engine.state.taps.single().rule)
    }

    @Test fun exactFreshnessBoundaryIsEligible() {
        val engine = GameEngine(Store(), { 1000 }, { 0.0 })
        assertEquals(TapOutcome.Caught, engine.onTap(context.copy(ageMillis = 600_000)).outcome)
    }

    @Test fun disabledDoesNotRecordTapCatchOrRecoveryAndSurvivesRestart() {
        val store = Store()
        var now = 1000L
        val engine = GameEngine(store, { now }, { error("Disabled must not roll") })
        engine.setHealthForDebug(50)
        engine.setActive(false)
        now += 10 * GameEngine.HOUR_MILLIS
        assertEquals("disabled", engine.onTap(context).reason)
        assertTrue(engine.state.taps.isEmpty())
        assertTrue(engine.state.tapsPerApp.isEmpty())
        assertEquals(0L, engine.state.totalCatches)
        assertEquals(50, engine.state.health)
        assertFalse(GameEngine(store, { now }).state.config.active)
        engine.setActive(true)
        now += GameEngine.HOUR_MILLIS
        assertEquals(55, engine.getHealth())
    }

    @Test fun timedDisableOnlyRecoversAfterItsDeadline() {
        var now = 1000L
        val engine = GameEngine(Store(), { now })
        engine.setHealthForDebug(50)
        engine.setActive(false, now + GameEngine.HOUR_MILLIS)
        now += 2 * GameEngine.HOUR_MILLIS
        assertEquals(55, engine.getHealth())
        assertTrue(engine.state.config.active)
        assertNull(engine.state.config.disabledUntilMillis)
    }

    @Test fun pendingCatchPersistsAndAcknowledgementDoesNotApplyDamageTwice() {
        val store = Store()
        var now = 1000L
        val engine = GameEngine(store, { now }, { 0.0 })
        engine.onTap(context)
        val first = engine.state.pendingCatch!!
        assertEquals("social.app", first.packageName)
        assertEquals(first, GameEngine(store, { now }).state.pendingCatch)
        now += 30_000
        engine.onTap(context)
        val second = engine.state.pendingCatch!!
        engine.dismissCatch(first.id)
        assertEquals(second, engine.state.pendingCatch)
        engine.dismissCatch(second.id)
        engine.dismissCatch(second.id)
        assertNull(engine.state.pendingCatch)
        assertEquals(80, engine.state.health)
        assertEquals(2L, engine.state.totalCatches)
    }

    @Test fun disablingClearsPendingCatch() {
        val engine = GameEngine(Store(), { 1000 }, { 0.0 })
        engine.onTap(context)
        engine.setActive(false)
        assertNull(engine.state.pendingCatch)
    }

    @Test fun mainVersionFourFreshnessStoreMigratesWithoutLosingItsSettings() {
        val fixture = "AAAABAAAAFoAAAAAAAAD6AAAAAAAAAPoAAAAAAAAeRgAAAAAAAAD6AAAAAAAAAABP/AAAAAAAAAABE1pbGQAAAAKAAAAFAAAACMAAAAFAAAAHgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAA+gABGJhaXQABEJhaXQABkNhdWdodAAAAGQAAABaAAAACgAGY2F1Z2h0AQAAAAAAABOIAA9jb25maWd1cmVkX2JhaXQAAAAAAAAAKgE="
        val old = GameStateCodec.decode(fixture)
        assertEquals(90, old.health)
        assertEquals(1L, old.totalCatches)
        assertEquals(42, old.config.freshnessSeconds)
        assertTrue(old.config.freshnessFromFirstSeen)
        assertEquals("configured_bait", old.taps.single().rule)
        assertNull(old.pendingCatch)
        assertEquals(old, GameStateCodec.decode(GameStateCodec.encode(old)))
    }

    @Test fun disableGateEnforcesWaitCancelAndSingleUse() {
        var elapsed = 0L
        val gate = DisableGate { elapsed }
        val first = gate.begin()
        assertThrows(IllegalStateException::class.java) { gate.confirm(first.id) }
        elapsed = 9999
        assertThrows(IllegalStateException::class.java) { gate.confirm(first.id) }
        elapsed = 10000
        assertThrows(IllegalStateException::class.java) { gate.confirm("wrong") }
        gate.confirm(first.id)
        assertThrows(IllegalStateException::class.java) { gate.confirm(first.id) }
        val second = gate.begin()
        gate.cancel()
        elapsed += 10000
        assertThrows(IllegalStateException::class.java) { gate.confirm(second.id) }
    }
}
