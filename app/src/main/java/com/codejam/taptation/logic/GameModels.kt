package com.codejam.taptation.logic

enum class CatchLevel { Mild, Medium, Savage }
enum class Classification { Important, Bait, Unknown }
enum class TapOutcome { Bypass, NoPenalty, Caught }
enum class AppProfile { Outlook, Instagram, Discord }
enum class Difficulty(val probability: Double) { Easy(0.20), Medium(0.40), Hard(0.70) }
data class PendingCatch(val id: String, val packageName: String?, val continueExtraDamage: Int = 0) {
    init { require(continueExtraDamage in 0..100) }
}

data class GameConfig(
    val difficulty: Difficulty = Difficulty.Easy,
    val debugCatchProbability: Double? = null,
    val level: CatchLevel = CatchLevel.Mild,
    val mildDamage: Int = 10,
    val mediumDamage: Int = 20,
    val savageDamage: Int = 35,
    val recoveryPerHour: Int = 5,
    val lockoutSeconds: Int = 30,
    val whitelistedPackages: Set<String> = emptySet(),
    val baitPackages: Set<String> = emptySet(),
    val baitKeywords: Set<String> = emptySet(),
    val baitAppCategories: Set<Int> = emptySet(),
    val vipNames: Set<String> = emptySet(),
    val importantKeywords: Set<String> = setOf("urgent", "code", "meeting invite", "meeting invitation"),
    val appProfiles: Map<String, AppProfile> = emptyMap(),
    val importantChannels: Map<String, Set<String>> = emptyMap(),
    val baitChannels: Map<String, Set<String>> = emptyMap(),
    val active: Boolean = true,
    val disabledUntilMillis: Long? = null,
    val freshnessSeconds: Int = 600,
    val freshnessFromFirstSeen: Boolean = false,
) {
    val catchProbability: Double get() = debugCatchProbability ?: difficulty.probability
    val freshnessWindowMillis: Long get() = freshnessSeconds * 1000L
    fun isActive(nowMillis: Long): Boolean = active || disabledUntilMillis?.let { nowMillis >= it } == true
    init {
        require(catchProbability.isFinite() && catchProbability in 0.0..1.0)
        require(listOf(mildDamage, mediumDamage, savageDamage).all { it in 1..100 })
        require(recoveryPerHour in 0..100)
        require(lockoutSeconds in 1..3600)
        require(freshnessSeconds in 0..MAX_FRESHNESS_SECONDS)
        require((whitelistedPackages + baitPackages + baitKeywords + vipNames + importantKeywords +
            appProfiles.keys + importantChannels.keys + baitChannels.keys +
            importantChannels.values.flatten() + baitChannels.values.flatten()).none { it.isBlank() })
    }

    /** True when [context] is older than the freshness window; unknown ages are never stale. */
    fun isStale(context: NotificationContext): Boolean {
        if (freshnessSeconds == 0) return false
        val age = (if (freshnessFromFirstSeen) context.firstSeenAgeMillis else null) ?: context.ageMillis
        return age != null && age > freshnessSeconds * 1000L
    }

    companion object {
        const val MAX_FRESHNESS_SECONDS = 24 * 60 * 60
    }

    fun damage(level: CatchLevel): Int = when (level) {
        CatchLevel.Mild -> mildDamage
        CatchLevel.Medium -> mediumDamage
        CatchLevel.Savage -> savageDamage
    }
}

/**
 * One processed tap. [reason] is the final decision (for bait: `caught`, `roll_passed`, `lockout`,
 * `dead`); [rule] is the classifier rule that produced [classification] (e.g. `vip_title`,
 * `bait_category`), so the UI can explain why a notification counted as important or bait.
 */
data class TapRecord(
    val timestampMillis: Long,
    val packageName: String,
    val classification: Classification,
    val outcome: TapOutcome,
    val healthBefore: Int,
    val healthAfter: Int,
    val damage: Int,
    val reason: String,
    val ageMillis: Long? = null,
    val rule: String = reason,
)

data class HealthRecord(
    val timestampMillis: Long,
    val before: Int,
    val after: Int,
    val reason: String,
    val level: CatchLevel? = null,
)

data class GameState(
    val health: Int = 100,
    val config: GameConfig = GameConfig(),
    val recoveryAnchorMillis: Long = 0,
    val lastObservedMillis: Long = 0,
    val lockoutUntilMillis: Long = 0,
    val lastCatchMillis: Long? = null,
    val totalCatches: Long = 0,
    val tapsPerApp: Map<String, Long> = emptyMap(),
    val taps: List<TapRecord> = emptyList(),
    val healthHistory: List<HealthRecord> = emptyList(),
    val pendingCatch: PendingCatch? = null,
) {
    val isDead: Boolean get() = health == 0
    fun isLockedOut(nowMillis: Long): Boolean = nowMillis < lockoutUntilMillis
    fun timeSinceLastCatchMillis(nowMillis: Long): Long? =
        lastCatchMillis?.let { (nowMillis - it).coerceAtLeast(0) }
}

data class TapResult(
    val outcome: TapOutcome,
    val state: GameState,
    val reason: String,
    val classification: Classification = Classification.Unknown,
    /** Classifier rule behind [classification]; see [TapRecord.rule]. */
    val rule: String = reason,
)

interface GameStore {
    fun load(): GameState?
    /** Throw on failure; the engine publishes a new state only after a successful save. */
    fun save(state: GameState)
}
