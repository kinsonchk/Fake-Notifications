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
            game.updateConfig(change(GameConfig(baitPackages = setOf("bait"))))
        }
        fun hours(count: Double) { now += (count * GameEngine.HOUR_MILLIS).toLong() }
    }

    @Test fun whitelistWinsOverAllBaitRules() {
        val f = Fixture()
        f.configure { it.copy(whitelistedPackages = setOf("bait"), baitKeywords = setOf("sale")) }
        val result = f.game.onTap(NotificationContext("bait", text = "SALE"))
        assertEquals(TapOutcome.Bypass, result.outcome)
        assertEquals(100, result.state.health)
        assertEquals(0, f.rolls)
        assertEquals(1L, result.state.tapsPerApp["bait"])
    }

    @Test fun unknownNeverRolls() {
        val f = Fixture()
        assertEquals("missing_content", f.game.onTap("unknown").reason)
        assertEquals(0, f.rolls)
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
        assertEquals(Classification.Bait, f.game.classify(NotificationContext("music", title = "Music", text = "New release", appCategory = 2)))
        assertEquals(Classification.Unknown, f.game.classify(NotificationContext("music")))
    }

    @Test fun probabilityZeroNeverCatches() {
        val f = Fixture()
        f.configure { it.copy(catchProbability = 0.0) }
        assertEquals(TapOutcome.NoPenalty, f.tap("bait").outcome)
    }

    @Test fun probabilityOneAlwaysCatches() {
        val f = Fixture()
        f.roll = 0.999999
        f.configure { it.copy(catchProbability = 1.0) }
        assertEquals(TapOutcome.Caught, f.tap("bait").outcome)
    }

    @Test fun rollAtThresholdIsNotCaught() {
        val f = Fixture()
        f.configure()
        f.roll = 0.075
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

    @Test fun missedRollStillResetsRecoveryButBypassAndUnknownDoNot() {
        val f = Fixture()
        f.configure { it.copy(catchProbability = 0.0, whitelistedPackages = setOf("urgent")) }
        f.game.setHealthForDebug(50)
        f.hours(0.75)
        f.tap("bait")
        f.hours(0.75)
        f.game.onTap("urgent")
        f.game.onTap("unknown")
        assertEquals(50, f.game.getHealth())
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
        f.configure { it.copy(catchProbability = 0.4, level = CatchLevel.Savage,
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
            assertThrows(IllegalArgumentException::class.java) { GameConfig(catchProbability = probability) }
        }
        assertThrows(IllegalArgumentException::class.java) { GameConfig(baitKeywords = setOf(" ")) }
        assertThrows(Exception::class.java) { GameStateCodec.decode("broken") }
        val encoded = GameStateCodec.encode(GameState())
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        bytes[3] = 3
        assertThrows(IllegalArgumentException::class.java) {
            GameStateCodec.decode(java.util.Base64.getEncoder().encodeToString(bytes))
        }
    }
}
