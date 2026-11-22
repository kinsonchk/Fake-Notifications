package com.codejam.taptation.logic

import org.junit.Assert.*
import org.junit.Test

class GameEngineTest {
    private class MemoryStore : GameStore {
        var encoded: String? = null
        var failSave = false
        override fun load(): GameState? = encoded?.let(GameStateCodec::decode)
        override fun save(state: GameState) {
            check(!failSave) { "Disk full" }
            encoded = GameStateCodec.encode(state)
        }
    }

    private class Fixture {
        val store = MemoryStore()
        var now = 1_000_000L
        var roll = 0.0
        var rolls = 0
        fun engine() = GameEngine(store, { now }, { rolls++; roll })
        val game = engine()
        fun context(packageName: String) = NotificationContext(packageName, title = "Demo",
            text = "Routine update", category = "email", isConversation = false, ageMillis = 5000)
        fun tap(packageName: String) = game.onTap(context(packageName))
        fun configure(change: (GameConfig) -> GameConfig = { it }) {
            game.updateConfig(change(GameConfig(appRules = mapOf("bait" to AppRule.AlwaysBait))))
        }
        fun hours(count: Double) { now += (count * GameEngine.HOUR_MILLIS).toLong() }
    }

    @Test fun alwaysImportantWinsOverAllBaitRules() {
        val f = Fixture()
        f.configure { it.copy(appRules = mapOf("bait" to AppRule.AlwaysImportant), baitKeywords = setOf("sale")) }
        val result = f.game.onTap(NotificationContext("bait", text = "SALE"))
        assertEquals(TapOutcome.Bypass, result.outcome)
        assertEquals(100, result.state.health)
        assertEquals(0, f.rolls)
        assertEquals(1L, result.state.tapsPerApp["bait"])
    }

    @Test fun resettingAnAppToLetUsDecideMakesItBaitAndKeepsOtherRules() {
        val f = Fixture()
        f.configure { it.copy(
            debugCatchProbability = 1.0,
            appRules = mapOf("bait" to AppRule.AlwaysImportant, "important" to AppRule.AlwaysImportant),
        ) }

        val safe = f.tap("bait")
        assertEquals(TapOutcome.Bypass, safe.outcome)
        assertEquals(0, f.rolls)
        assertEquals(100, safe.state.health)

        f.game.updateConfig(f.game.state.config.copy(appRules = f.game.state.config.appRules - "bait"))
        assertEquals(mapOf("important" to AppRule.AlwaysImportant), f.game.state.config.appRules)

        val caught = f.tap("bait")
        assertEquals("let_us_decide", caught.rule)
        assertEquals(TapOutcome.Caught, caught.outcome)
        assertEquals(1, f.rolls)
        assertEquals(90, caught.state.health)
    }
    @Test fun appsLeftOnLetUsDecideAreBaitAndRoll() {
        val f = Fixture()
        val result = f.game.onTap("unknown")
        assertEquals(Classification.Bait, result.classification)
        assertEquals("let_us_decide", result.rule)
        assertEquals(1, f.rolls)
    }

    @Test fun keywordMatchingIsCaseInsensitiveAndDoesNotPersistContent() {
        val f = Fixture()
        f.configure { it.copy(baitKeywords = setOf("SALE")) }
        assertEquals(TapOutcome.Caught, f.game.onTap(NotificationContext("shop", title = "Shop", text = "private sale message")).outcome)
        val bytes = java.util.Base64.getDecoder().decode(f.store.encoded)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("private sale message"))
    }

    @Test fun categoriesClassifyButMissingMetadataStaysUnknown() {
        val f = Fixture()
        f.configure { it.copy(baitAppCategories = setOf(2)) }
        val rules = { context: NotificationContext -> NotificationClassifier.assessByRules(context, f.game.state.config).classification }
        assertEquals(Classification.Bait, rules(NotificationContext("music", title = "Music", text = "New release", appCategory = 2)))
        assertEquals(Classification.Unknown, rules(NotificationContext("music")))
    }

    @Test fun probabilityZeroNeverCatches() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 0.0) }
        assertEquals(TapOutcome.NoPenalty, f.tap("bait").outcome)
    }

    @Test fun probabilityOneAlwaysCatches() {
        val f = Fixture()
        f.roll = 0.999999
        f.configure { it.copy(debugCatchProbability = 1.0) }
        assertEquals(TapOutcome.Caught, f.tap("bait").outcome)
    }

    @Test fun rollAtThresholdIsNotCaught() {
        val f = Fixture()
        f.configure()
        f.roll = 0.20
        assertEquals(TapOutcome.NoPenalty, f.tap("bait").outcome)
    }

    @Test fun eachLevelAppliesExactlyOneDamageAndCatch() {
        listOf(CatchLevel.Mild to 90, CatchLevel.Medium to 80, CatchLevel.Savage to 65).forEach { (level, health) ->
            val f = Fixture()
            f.configure { it.copy(level = level) }
            val result = f.tap("bait")
            assertEquals(health, result.state.health)
            assertEquals(1L, result.state.totalCatches)
            assertEquals(100 - health, result.state.taps.single().damage)
            assertEquals(level, result.state.healthHistory.single().level)
        }
    }

    @Test fun activeLockoutLogsWithoutRollingDamagingOrExtending() {
        val f = Fixture()
        f.configure()
        f.tap("bait")
        val until = f.game.state.lockoutUntilMillis
        f.now += 1000
        val result = f.tap("bait")
        assertEquals("lockout", result.reason)
        assertEquals(90, result.state.health)
        assertEquals(until, result.state.lockoutUntilMillis)
        assertEquals(1, f.rolls)
        assertEquals(2, result.state.taps.size)
    }

    @Test fun expiryAllowsAnotherCatchAndSurvivesRestart() {
        val f = Fixture()
        f.configure()
        f.tap("bait")
        val restored = f.engine()
        assertEquals("lockout", restored.onTap(f.context("bait")).reason)
        f.now += 30_000
        assertEquals(TapOutcome.Caught, restored.onTap(f.context("bait")).outcome)
        assertEquals(80, restored.state.health)
    }

    @Test fun recoveryRetainsPartialHoursAndDoesNotDoubleCount() {
        val f = Fixture()
        f.game.setHealthForDebug(50)
        f.hours(1.5)
        assertEquals(55, f.game.getHealth())
        assertEquals(55, f.game.getHealth())
        f.hours(0.5)
        assertEquals(60, f.game.getHealth())
    }

    @Test fun recoveryWorksAcrossRestartAndCapsAt100() {
        val f = Fixture()
        f.game.setHealthForDebug(95)
        f.hours(10.0)
        val restored = f.engine()
        assertEquals(100, restored.getHealth())
        assertEquals(1, restored.state.healthHistory.count { it.reason == "recovery" })
    }

    @Test fun missedRollPreservesRecoveryButBypassAndUnknownDoNot() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 0.0, appRules = it.appRules + ("urgent" to AppRule.AlwaysImportant)) }
        f.game.setHealthForDebug(50)
        f.hours(0.75)
        f.tap("bait")
        f.hours(0.75)
        f.game.onTap("urgent")
        // Too old for the freshness window: Unknown, so the clean timer keeps running.
        f.game.onTap(NotificationContext("unknown", ageMillis = 3_600_000))
        assertEquals(55, f.game.getHealth())
        f.hours(0.25)
        assertEquals(55, f.game.getHealth())
    }

    @Test fun recoveryIsAppliedBeforeDamage() {
        val f = Fixture()
        f.configure()
        f.game.setHealthForDebug(50)
        f.hours(1.0)
        val tap = f.tap("bait").state.taps.last()
        assertEquals(55, tap.healthBefore)
        assertEquals(45, tap.healthAfter)
    }

    @Test fun deathClampsAndRequiresExplicitResetWithoutErasingStats() {
        val f = Fixture()
        f.configure { it.copy(level = CatchLevel.Savage) }
        f.game.setHealthForDebug(10)
        f.tap("bait")
        assertTrue(f.game.state.isDead)
        assertEquals(10, f.game.state.taps.last().damage)
        f.hours(24.0)
        assertEquals(0, f.game.getHealth())
        assertEquals("dead", f.tap("bait").reason)
        assertEquals(1, f.rolls)
        val reset = f.game.reset()
        assertEquals(100, reset.health)
        assertEquals(1L, reset.totalCatches)
        assertEquals(2L, reset.tapsPerApp["bait"])
        assertEquals(0L, reset.lockoutUntilMillis)
    }

    @Test(expected = IllegalStateException::class) fun livingTreeCannotReset() {
        Fixture().game.reset()
    }

    @Test fun debugCatchAlsoRespectsDeathAndLockout() {
        val f = Fixture()
        f.game.applyCatch(CatchLevel.Medium)
        f.game.applyCatch(CatchLevel.Savage)
        assertEquals(80, f.game.state.health)
        assertEquals(1L, f.game.state.totalCatches)
        f.game.setHealthForDebug(0)
        f.game.applyCatch(CatchLevel.Mild)
        assertEquals(0, f.game.state.health)
    }

    @Test fun configAndAllHistoryRoundTrip() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 0.4, level = CatchLevel.Savage,
            whitelistedPackages = setOf("urgent"), baitKeywords = setOf("sale"),
            baitAppCategories = setOf(0, 2), mildDamage = 12, recoveryPerHour = 7) }
        f.game.onTap("urgent")
        f.tap("bait")
        assertEquals(f.game.state, f.store.load())
        assertEquals(f.game.state, f.engine().state)
    }

    @Test fun detailsAreBoundedButTotalsSurvive() {
        val f = Fixture()
        repeat(1005) { f.game.onTap("unknown") }
        assertEquals(1000, f.game.state.taps.size)
        assertEquals(1005L, f.engine().state.tapsPerApp["unknown"])
    }

    @Test fun failedSaveDoesNotPublishPartialCatch() {
        val f = Fixture()
        f.configure()
        val before = f.game.state
        f.store.failSave = true
        assertThrows(IllegalStateException::class.java) { f.tap("bait") }
        assertEquals(before, f.game.state)
        assertEquals(before, f.store.load())
    }

    @Test fun backwardClockDoesNotRegrowOrShortenSavedLockout() {
        val f = Fixture()
        f.configure()
        f.tap("bait")
        val before = f.game.state
        f.now -= GameEngine.HOUR_MILLIS
        f.game.refresh()
        assertEquals(before, f.game.state)
        assertEquals("lockout", f.tap("bait").reason)
    }

    @Test fun recoveryRateChangeDoesNotRetroactivelyMultiplyPartialHour() {
        val f = Fixture()
        f.game.setHealthForDebug(50)
        f.hours(0.75)
        f.game.updateConfig(f.game.state.config.copy(recoveryPerHour = 20))
        f.hours(0.25)
        assertEquals(50, f.game.getHealth())
        f.hours(0.75)
        assertEquals(70, f.game.getHealth())
    }

    @Test fun invalidConfigAndCorruptStoreAreRejected() {
        listOf(-0.1, 1.1, Double.NaN).forEach { probability ->
            assertThrows(IllegalArgumentException::class.java) { GameConfig(debugCatchProbability = probability) }
        }
        assertThrows(IllegalArgumentException::class.java) { GameConfig(baitKeywords = setOf(" ")) }
        assertThrows(Exception::class.java) { GameStateCodec.decode("broken") }
        val encoded = GameStateCodec.encode(GameState())
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        bytes[3] = 8
        assertThrows(IllegalArgumentException::class.java) {
            GameStateCodec.decode(java.util.Base64.getEncoder().encodeToString(bytes))
        }
    }

    @Test fun baitTapKeepsClassifierRuleNextToRollResult() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0) }
        val result = f.tap("bait")
        assertEquals("caught", result.reason)
        assertEquals(Classification.Bait, result.classification)
        assertEquals("app_always_bait", result.rule)
        val record = f.game.state.taps.last()
        assertEquals("caught", record.reason)
        assertEquals("app_always_bait", record.rule)
        assertEquals(record, GameStateCodec.decode(GameStateCodec.encode(f.game.state)).taps.last())
    }

    @Test fun versionTwoBaitTapsDecodeWithUnknownRule() {
        // Fixed historical v2 snapshot; don't fabricate v2 by editing the current format.
        val fixture = "AAAAAgAAAFoAAAAAAAAD6AAAAAAAAAPoAAAAAAAAeRgAAAAAAAAD6AAAAAAAAAABP/AAAAAAAAAABE1pbGQAAAAKAAAAFAAAACMAAAAFAAAAHgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAA+gABGJhaXQABEJhaXQABkNhdWdodAAAAGQAAABaAAAACgAGY2F1Z2h0AQAAAAAAABOIAAAAAA=="
        val decoded = GameStateCodec.decode(fixture)
        assertEquals("caught", decoded.taps.single().reason)
        assertEquals("", decoded.taps.single().rule)
    }

    @Test fun staleBaitTapSkipsRollDamageAndTimerReset() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0, freshnessSeconds = 60) }
        val anchor = f.game.state.recoveryAnchorMillis
        f.now += 1000
        val result = f.game.onTap(f.context("bait").copy(ageMillis = 61_000))
        assertEquals(TapOutcome.NoPenalty, result.outcome)
        assertEquals("stale", result.reason)
        assertEquals(Classification.Unknown, result.classification)
        assertEquals("stale", result.rule)
        assertEquals(0, f.rolls)
        assertEquals(100, result.state.health)
        assertEquals(anchor, result.state.recoveryAnchorMillis)
        assertEquals("stale", result.state.taps.last().reason)
    }

    @Test fun freshBaitAndUnknownAgeStillRoll() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0, freshnessSeconds = 60) }
        assertEquals(TapOutcome.Caught, f.game.onTap(f.context("bait").copy(ageMillis = 60_000)).outcome)
        val g = Fixture()
        g.configure { it.copy(debugCatchProbability = 1.0, freshnessSeconds = 60) }
        assertEquals(TapOutcome.Caught, g.game.onTap(g.context("bait").copy(ageMillis = null)).outcome)
    }

    @Test fun freshnessOffNeverSkips() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0, freshnessSeconds = 0) }
        assertEquals(TapOutcome.Caught, f.game.onTap(f.context("bait").copy(ageMillis = 86_400_000)).outcome)
    }

    @Test fun firstSeenModeIgnoresUpdatesThatRestartPostTime() {
        // A chat notification first posted 20 min ago, updated by a new message 10 s ago.
        val chat = NotificationContext("bait", title = "Demo", text = "Routine update", category = "email",
            isConversation = false, ageMillis = 10_000, firstSeenAgeMillis = 20 * 60_000)
        val latest = GameConfig(baitPackages = setOf("bait"), freshnessSeconds = 600)
        assertFalse(latest.isStale(chat))
        assertTrue(latest.copy(freshnessFromFirstSeen = true).isStale(chat))
        // No first-seen time (listener restarted): falls back to the update age.
        assertFalse(latest.copy(freshnessFromFirstSeen = true).isStale(chat.copy(firstSeenAgeMillis = null)))
    }

    @Test fun freshnessSettingsPersistAndOldStoresGetDefaults() {
        val state = GameState(config = GameConfig(freshnessSeconds = 30, freshnessFromFirstSeen = true))
        val decoded = GameStateCodec.decode(GameStateCodec.encode(state))
        assertEquals(30, decoded.config.freshnessSeconds)
        assertTrue(decoded.config.freshnessFromFirstSeen)
        // Fixed original v3 fixture: current formats append more fields than freshness alone.
        val fixture = "AAAAAwAAAFoAAAAAAAAD6AAAAAAAAAPoAAAAAAAAeRgAAAAAAAAD6AAAAAAAAAABP/AAAAAAAAAABE1pbGQAAAAKAAAAFAAAACMAAAAFAAAAHgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABAAAAAAAAA+gABGJhaXQABEJhaXQABkNhdWdodAAAAGQAAABaAAAACgAGY2F1Z2h0AQAAAAAAABOIAA9jb25maWd1cmVkX2JhaXQAAAAA"
        val old = GameStateCodec.decode(fixture)
        assertEquals(GameConfig().freshnessSeconds, old.config.freshnessSeconds)
        assertFalse(old.config.freshnessFromFirstSeen)
        assertThrows(IllegalArgumentException::class.java) { GameConfig(freshnessSeconds = -1) }
    }

    @Test fun debugFastForwardRecoversAndEndsLockout() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0) }
        f.tap("bait")
        assertEquals(90, f.game.state.health)
        assertTrue(f.game.state.isLockedOut(f.now))
        val state = f.game.advanceTimeForDebug(GameEngine.HOUR_MILLIS)
        assertEquals(95, state.health)
        assertFalse(state.isLockedOut(f.now))
        assertEquals(1L, state.totalCatches)
    }

    @Test fun debugResetClearsStatsButKeepsSettings() {
        val f = Fixture()
        f.configure { it.copy(debugCatchProbability = 1.0, whitelistedPackages = setOf("work")) }
        f.tap("bait")
        val state = f.game.resetDemoDataForDebug()
        assertEquals(100, state.health)
        assertEquals(0L, state.totalCatches)
        assertTrue(state.taps.isEmpty())
        assertTrue(state.tapsPerApp.isEmpty())
        assertFalse(state.isLockedOut(f.now))
        assertEquals(setOf("work"), state.config.whitelistedPackages)
    }

    @Test fun codecV6KeepsAppRulesAnswersAndStrictness() {
        val config = GameConfig(
            appRules = mapOf("a" to AppRule.AlwaysBait, "b" to AppRule.AlwaysImportant),
            essentialCriteria = "Family, shift changes", nonEssentialCriteria = "Likes", strictness = Strictness.Strict,
        )
        val state = GameState(config = config)
        assertEquals(state, GameStateCodec.decode(GameStateCodec.encode(state)))
    }

    @Test fun codecV5WhitelistAndBaitAppsBecomeAppRules() {
        val legacy = GameState(config = GameConfig(whitelistedPackages = setOf("safe"), baitPackages = setOf("bait")))
        val bytes = java.util.Base64.getDecoder().decode(GameStateCodec.encode(legacy))
        // v6 tail for empty rules: count (4) + two empty strings (2 + 2) + "Balanced" (2 + 8).
        val v5 = bytes.copyOf(bytes.size - 18).also { it[3] = 5 }
        val config = GameStateCodec.decode(java.util.Base64.getEncoder().encodeToString(v5)).config
        assertEquals(mapOf("safe" to AppRule.AlwaysImportant, "bait" to AppRule.AlwaysBait), config.appRules)
        assertTrue(config.whitelistedPackages.isEmpty() && config.baitPackages.isEmpty())
    }
}
