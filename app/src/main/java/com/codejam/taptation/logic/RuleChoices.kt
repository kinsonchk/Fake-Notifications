package com.codejam.taptation.logic

/** What the user wants for one app or one notification type. */
enum class RuleBehavior { Distracting, AlwaysSafe, Default }

/** The whole app, or one notification type ([channel]) of it. */
data class RuleTarget(val packageName: String, val channel: String? = null) {
    val isWholeApp: Boolean get() = channel == null
}

data class SavedRule(val target: RuleTarget, val behavior: RuleBehavior)

/**
 * Turns the friendly choice (app, type, behavior) into the existing [GameConfig] sets, so the
 * classifier keeps working unchanged. Pure, so it is unit-tested.
 */
object RuleChoices {
    fun behaviorOf(config: GameConfig, target: RuleTarget): RuleBehavior {
        val pkg = target.packageName
        val channel = target.channel
        return if (channel == null) when {
            pkg in config.whitelistedPackages -> RuleBehavior.AlwaysSafe
            pkg in config.baitPackages -> RuleBehavior.Distracting
            else -> RuleBehavior.Default
        } else when {
            config.importantChannels.has(pkg, channel) -> RuleBehavior.AlwaysSafe
            config.baitChannels.has(pkg, channel) -> RuleBehavior.Distracting
            else -> RuleBehavior.Default
        }
    }

    fun apply(config: GameConfig, target: RuleTarget, behavior: RuleBehavior): GameConfig {
        val pkg = target.packageName
        val channel = target.channel
        require(pkg.isNotBlank() && channel?.isBlank() != true)
        val cleared = if (channel == null) config.copy(
            whitelistedPackages = config.whitelistedPackages - pkg,
            baitPackages = config.baitPackages - pkg,
        ) else config.copy(
            importantChannels = config.importantChannels.without(pkg, channel),
            baitChannels = config.baitChannels.without(pkg, channel),
        )
        return when (behavior) {
            RuleBehavior.Default -> cleared
            RuleBehavior.AlwaysSafe -> if (channel == null) cleared.copy(whitelistedPackages = cleared.whitelistedPackages + pkg)
                else cleared.copy(importantChannels = cleared.importantChannels.with(pkg, channel))
            RuleBehavior.Distracting -> if (channel == null) cleared.copy(baitPackages = cleared.baitPackages + pkg)
                else cleared.copy(baitChannels = cleared.baitChannels.with(pkg, channel))
        }
    }

    /**
     * Rules shown in "Notification rules": distracting apps and per-type rules. App-wide always-safe
     * apps live on their own screen, so they are not repeated here.
     */
    fun savedRules(config: GameConfig): List<SavedRule> = buildList {
        config.baitPackages.sorted().forEach { add(SavedRule(RuleTarget(it), RuleBehavior.Distracting)) }
        config.baitChannels.toSortedMap().forEach { (pkg, names) ->
            names.sorted().forEach { add(SavedRule(RuleTarget(pkg, it), RuleBehavior.Distracting)) }
        }
        config.importantChannels.toSortedMap().forEach { (pkg, names) ->
            names.sorted().forEach { add(SavedRule(RuleTarget(pkg, it), RuleBehavior.AlwaysSafe)) }
        }
    }

    private fun Map<String, Set<String>>.has(pkg: String, name: String) =
        this[pkg].orEmpty().any { it.trim().equals(name.trim(), ignoreCase = true) }

    private fun Map<String, Set<String>>.without(pkg: String, name: String): Map<String, Set<String>> {
        val left = this[pkg].orEmpty().filterNot { it.trim().equals(name.trim(), ignoreCase = true) }.toSet()
        return if (left.isEmpty()) this - pkg else this + (pkg to left)
    }

    private fun Map<String, Set<String>>.with(pkg: String, name: String): Map<String, Set<String>> =
        this + (pkg to (this[pkg].orEmpty() + name.trim()))
}
