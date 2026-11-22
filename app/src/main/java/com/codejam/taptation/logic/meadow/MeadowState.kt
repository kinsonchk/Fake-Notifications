package com.codejam.taptation.logic.meadow

import com.codejam.taptation.logic.GameState

enum class MeadowDiscovery { Hedgehog, Pond }
enum class MeadowCameo { Fish, Robin, Squirrel, Hedgehog }
enum class MeadowMilestone(val minutes: Long) {
    Flowers(30), Wildflowers(90), Visitors(180), Nest(240), Eggs(300), Oak(360)
}

/**
 * How much of the valley behind the meadow is visible. Driven by the current clean streak (time without
 * a catch while monitoring works); each catch rolls the view back one stage. Landmarks, once revealed,
 * stay (see [MeadowState.revealed]); only the camera and mist follow the streak.
 */
enum class ValleyStage(val minutes: Long) {
    Grass(0), NearField(15), River(30), Hills(60), Bridge(120), Ridges(240), Cottage(360), Valley(480);
    val next: ValleyStage? get() = entries.getOrNull(ordinal + 1)
    companion object {
        fun at(cleanMillis: Long): ValleyStage = entries.last { cleanMillis >= it.minutes * MeadowState.MINUTE }
        /** One catch: back to the start of the previous stage (or the start of the valley). */
        fun afterCatch(cleanMillis: Long): Long = entries[(at(cleanMillis).ordinal - 1).coerceAtLeast(0)].minutes * MeadowState.MINUTE
    }
}

/** Earned growth is separate from health. Catches never remove discoveries or landmarks. */
data class MeadowState(
    val healthyMillis: Long = 0,
    val checkpointMillis: Long = 0,
    val checkpointFull: Boolean = false,
    val checkpointMonitoring: Boolean = false,
    val discoveries: Set<MeadowDiscovery> = emptySet(),
    /** Current clean streak, rolled back one stage per catch. */
    val cleanMillis: Long = 0,
    /** Game catch count at the checkpoint; -1 before the first observation (no rollback for old catches). */
    val checkpointCatches: Long = -1,
    /** Furthest [ValleyStage] ever reached; its landmarks stay even after a catch. */
    val revealedStage: Int = 0,
) {
    val valleyStage get() = ValleyStage.at(cleanMillis)
    val revealed get() = ValleyStage.entries[maxOf(revealedStage.coerceIn(0, ValleyStage.entries.size - 1), valleyStage.ordinal)]
    val valleyRemainingMillis get() = valleyStage.next?.let { (it.minutes * MINUTE - cleanMillis).coerceAtLeast(0) }
    val valleyProgress: Float get() {
        val next = valleyStage.next ?: return 1f
        val from = valleyStage.minutes * MINUTE
        return ((cleanMillis - from).toDouble() / (next.minutes * MINUTE - from)).toFloat().coerceIn(0f, 1f)
    }
    val healthyMinutes get() = healthyMillis / MINUTE
    val flowers get() = when { healthyMinutes >= 90 -> 7; healthyMinutes >= 30 -> 3; else -> 0 }
    val butterfly get() = healthyMinutes >= 30
    val robin get() = healthyMinutes >= 90
    val fireflies get() = healthyMinutes >= 180
    val nestStage get() = when { healthyMinutes >= 300 -> 3; healthyMinutes >= 240 -> 2; healthyMinutes >= 180 -> 1; else -> 0 }
    val acornStage get() = when { healthyMinutes >= 360 -> 3; healthyMinutes >= 240 -> 2; healthyMinutes >= 180 -> 1; else -> 0 }
    val catchCameos: List<MeadowCameo> get() = buildList {
        add(MeadowCameo.Fish)
        if(robin)add(MeadowCameo.Robin)
        if(acornStage>0)add(MeadowCameo.Squirrel)
        if(MeadowDiscovery.Hedgehog in discoveries)add(MeadowCameo.Hedgehog)
    }
    val nextMilestone get() = MeadowMilestone.entries.firstOrNull { healthyMillis < it.minutes * MINUTE }
    val remainingMillis get() = nextMilestone?.let { (it.minutes * MINUTE - healthyMillis).coerceAtLeast(0) }
    val milestoneProgress: Float get() {
        val next = nextMilestone ?: return 1f
        val previous = MeadowMilestone.entries.lastOrNull { it.minutes < next.minutes }?.minutes ?: 0
        return ((healthyMillis - previous * MINUTE).toDouble() / ((next.minutes - previous) * MINUTE)).toFloat().coerceIn(0f, 1f)
    }
    companion object { const val MINUTE = 60_000L }
}

/** Pure accounting, including health changes while the UI was closed. No UI clock or game writes. */
object MeadowProgression {
    fun advance(saved: MeadowState, game: GameState, monitoring: Boolean, now: Long): MeadowState {
        val end = now.coerceAtLeast(saved.checkpointMillis)
        val eligible = monitoring && game.config.isActive(end)
        var earned = 0L
        if (saved.checkpointMillis > 0 && saved.checkpointMonitoring && eligible) {
            var cursor = saved.checkpointMillis
            var full = saved.checkpointFull
            val records = game.healthHistory.filter { it.timestampMillis > cursor && it.timestampMillis <= end }
                .sortedBy { it.timestampMillis }
            // Missing transition history is ambiguous: don't invent time at full health.
            if (records.isNotEmpty() || full == (game.health == 100)) {
                for (record in records) {
                    if (full && record.before == 100) earned += (record.timestampMillis - cursor).coerceAtLeast(0)
                    cursor = record.timestampMillis
                    full = record.after == 100
                }
                if (full && game.health == 100) earned += (end - cursor).coerceAtLeast(0)
            }
        }
        // Clean streak: same eligibility as growth, but measured from the last catch, not from full health.
        val catches = if (saved.checkpointCatches < 0) 0L else (game.totalCatches - saved.checkpointCatches).coerceAtLeast(0)
        var clean = saved.cleanMillis.coerceIn(0, MAX_GROWTH)
        var accrueFrom = saved.checkpointMillis
        if (catches > 0) {
            repeat(minOf(catches, ValleyStage.entries.size.toLong()).toInt()) { clean = ValleyStage.afterCatch(clean) }
            game.lastCatchMillis?.let { if (it > accrueFrom) accrueFrom = it.coerceAtMost(end) }
        }
        if (saved.checkpointMillis > 0 && saved.checkpointMonitoring && eligible) clean += (end - accrueFrom).coerceAtLeast(0)
        clean = clean.coerceAtMost(MAX_GROWTH)
        val revealed = maxOf(saved.revealedStage, ValleyStage.at(saved.cleanMillis).ordinal, ValleyStage.at(clean).ordinal)
        return saved.copy(cleanMillis = clean, checkpointCatches = game.totalCatches, revealedStage = revealed,
            healthyMillis = (saved.healthyMillis.coerceIn(0,MAX_GROWTH) + earned.coerceIn(0,MAX_GROWTH)).coerceAtMost(MAX_GROWTH),
            checkpointMillis = end, checkpointFull = game.health == 100, checkpointMonitoring = eligible)
    }
    private const val MAX_GROWTH = 365L * 24 * 60 * MeadowState.MINUTE
}
