package com.codejam.taptation.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned snapshot, containing no raw notification content. Pure JVM for round-trip tests. */
object GameStateCodec {
    fun encode(state: GameState): String {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(6)
            out.writeInt(state.health)
            out.writeLong(state.recoveryAnchorMillis)
            out.writeLong(state.lastObservedMillis)
            out.writeLong(state.lockoutUntilMillis)
            out.writeLong(state.lastCatchMillis ?: -1)
            out.writeLong(state.totalCatches)
            with(state.config) {
                out.writeDouble(catchProbability)
                out.writeUTF(level.name)
                out.writeInt(mildDamage)
                out.writeInt(mediumDamage)
                out.writeInt(savageDamage)
                out.writeInt(recoveryPerHour)
                out.writeInt(lockoutSeconds)
                out.strings(whitelistedPackages)
                out.strings(baitPackages)
                out.strings(baitKeywords)
                out.writeInt(baitAppCategories.size)
                baitAppCategories.sorted().forEach(out::writeInt)
                out.strings(vipNames)
                out.strings(importantKeywords)
                out.writeInt(appProfiles.size)
                appProfiles.toSortedMap().forEach { (pkg, profile) ->
                    out.writeUTF(pkg)
                    out.writeUTF(profile.name)
                }
                out.channels(importantChannels)
                out.channels(baitChannels)
            }
            out.writeInt(state.tapsPerApp.size)
            state.tapsPerApp.toSortedMap().forEach { (pkg, count) ->
                out.writeUTF(pkg)
                out.writeLong(count)
            }
            out.writeInt(state.taps.size)
            state.taps.forEach {
                out.writeLong(it.timestampMillis)
                out.writeUTF(it.packageName)
                out.writeUTF(it.classification.name)
                out.writeUTF(it.outcome.name)
                out.writeInt(it.healthBefore)
                out.writeInt(it.healthAfter)
                out.writeInt(it.damage)
                out.writeUTF(it.reason)
                out.writeBoolean(it.ageMillis != null)
                it.ageMillis?.let(out::writeLong)
                out.writeUTF(it.rule)
            }
            out.writeInt(state.healthHistory.size)
            state.healthHistory.forEach {
                out.writeLong(it.timestampMillis)
                out.writeInt(it.before)
                out.writeInt(it.after)
                out.writeUTF(it.reason)
                out.writeUTF(it.level?.name.orEmpty())
            }
            // Keep main's v4 freshness tail intact; v5 appends the revised-flow fields.
            out.writeInt(state.config.freshnessSeconds)
            out.writeBoolean(state.config.freshnessFromFirstSeen)
            out.writeUTF(state.config.difficulty.name)
            out.writeBoolean(state.config.debugCatchProbability != null)
            state.config.debugCatchProbability?.let(out::writeDouble)
            out.writeBoolean(state.config.active)
            out.writeBoolean(state.config.disabledUntilMillis != null)
            state.config.disabledUntilMillis?.let(out::writeLong)
            out.writeBoolean(state.pendingCatch != null)
            state.pendingCatch?.let {
                out.writeUTF(it.id)
                out.writeBoolean(it.packageName != null)
                it.packageName?.let(out::writeUTF)
            }
            with(state.config) {
                out.writeInt(appRules.size)
                appRules.toSortedMap().forEach { (pkg, rule) -> out.writeUTF(pkg); out.writeUTF(rule.name) }
                out.writeUTF(essentialCriteria)
                out.writeUTF(nonEssentialCriteria)
                out.writeUTF(strictness.name)
            }
        }
        return Base64.getEncoder().encodeToString(buffer.toByteArray())
    }

    fun decode(encoded: String): GameState =
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            val version = input.readInt()
            require(version in 1..6) { "Unsupported game store version" }
            val health = input.readInt().also { require(it in 0..100) }
            val anchor = input.readLong()
            val observed = input.readLong()
            val lockout = input.readLong()
            val lastCatch = input.readLong().takeUnless { it == -1L }
            val catches = input.readLong().also { require(it >= 0) }
            val legacyProbability = input.readDouble()
            require(legacyProbability.isFinite() && legacyProbability in 0.0..1.0)
            var config = GameConfig(
                difficulty = Difficulty.entries.minBy { kotlin.math.abs(it.probability - legacyProbability) },
                level = CatchLevel.valueOf(input.readUTF()),
                mildDamage = input.readInt(),
                mediumDamage = input.readInt(),
                savageDamage = input.readInt(),
                recoveryPerHour = input.readInt(),
                lockoutSeconds = input.readInt(),
                whitelistedPackages = input.strings(),
                baitPackages = input.strings(),
                baitKeywords = input.strings(),
                baitAppCategories = List(input.count()) { input.readInt() }.toSet(),
            )
            if (version >= 2) {
                config = config.copy(
                    vipNames = input.strings(),
                    importantKeywords = input.strings(),
                    appProfiles = buildMap {
                        repeat(input.count()) { put(input.readUTF(), AppProfile.valueOf(input.readUTF())) }
                    },
                    importantChannels = input.channels(),
                    baitChannels = input.channels(),
                )
            }
            val counts = buildMap {
                repeat(input.count()) {
                    put(input.readUTF(), input.readLong().also { require(it >= 0) })
                }
            }
            val taps = List(input.count(GameEngine.HISTORY_LIMIT)) {
                val record = TapRecord(input.readLong(), input.readUTF(), Classification.valueOf(input.readUTF()),
                    TapOutcome.valueOf(input.readUTF()), input.readInt(), input.readInt(),
                    input.readInt(), input.readUTF(),
                    if (version >= 2 && input.readBoolean()) input.readLong() else null)
                // Before v3 the classifier rule wasn't stored. For bait, reason is the roll result,
                // not the rule, so mark it as unknown rather than show a misleading rule.
                when {
                    version >= 3 -> record.copy(rule = input.readUTF())
                    record.classification == Classification.Bait -> record.copy(rule = "")
                    else -> record
                }
            }
            val history = List(input.count(GameEngine.HISTORY_LIMIT)) {
                val timestamp = input.readLong()
                val before = input.readInt()
                val after = input.readInt()
                val reason = input.readUTF()
                val level = input.readUTF().takeIf(String::isNotEmpty)?.let(CatchLevel::valueOf)
                HealthRecord(timestamp, before, after, reason, level)
            }
            if (version >= 4) config = config.copy(freshnessSeconds = input.readInt(),
                freshnessFromFirstSeen = input.readBoolean())
            if (version >= 5) config = config.copy(
                difficulty = Difficulty.valueOf(input.readUTF()),
                debugCatchProbability = if (input.readBoolean()) input.readDouble() else null,
                active = input.readBoolean(),
                disabledUntilMillis = if (input.readBoolean()) input.readLong() else null,
            )
            val pending = if (version >= 5 && input.readBoolean()) PendingCatch(input.readUTF(),
                if (input.readBoolean()) input.readUTF() else null) else null
            config = if (version >= 6) config.copy(
                appRules = buildMap { repeat(input.count()) { put(input.readUTF(), AppRule.valueOf(input.readUTF())) } },
                essentialCriteria = input.readUTF(),
                nonEssentialCriteria = input.readUTF(),
                strictness = Strictness.valueOf(input.readUTF()),
            ) else config.withLegacyAppLists()
            require(input.available() == 0) { "Trailing game store data" }
            GameState(health, config, anchor, observed, lockout, lastCatch, catches, counts, taps, history, pending)
        }

    /**
     * Before v6, whitelisted and bait apps were separate lists. They become per-app rules, and the
     * lists are cleared so the rules are the only place an app's choice lives.
     */
    private fun GameConfig.withLegacyAppLists(): GameConfig = copy(
        appRules = baitPackages.associateWith { AppRule.AlwaysBait } +
            whitelistedPackages.associateWith { AppRule.AlwaysImportant },
        whitelistedPackages = emptySet(),
        baitPackages = emptySet(),
    )

    private fun DataOutputStream.strings(values: Set<String>) {
        writeInt(values.size)
        values.sorted().forEach(::writeUTF)
    }

    private fun DataInputStream.strings(): Set<String> = List(count()) { readUTF() }.toSet()
    private fun DataOutputStream.channels(values: Map<String, Set<String>>) {
        writeInt(values.size)
        values.toSortedMap().forEach { (pkg, names) -> writeUTF(pkg); strings(names) }
    }
    private fun DataInputStream.channels(): Map<String, Set<String>> {
        val input = this
        return buildMap { repeat(input.count()) { put(input.readUTF(), input.strings()) } }
    }
    private fun DataInputStream.count(max: Int = 100_000): Int = readInt().also {
        require(it in 0..max) { "Invalid game store collection size" }
    }
}
