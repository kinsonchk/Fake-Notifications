package com.codejam.taptation.logic

/**
 * What a catch cost and why, for the caught pop-up. Built from the saved tap record, so it holds
 * no notification title or text. [channel] is the notification type, when the listener knew it.
 */
data class CatchDetails(
    val packageName: String?,
    val healthBefore: Int,
    val healthAfter: Int,
    val damage: Int,
    val rule: String,
    val channel: String?,
) {
    companion object {
        /** The newest caught tap from the pending catch's app; falls back to the newest catch of any app. */
        fun from(state: GameState, pending: PendingCatch, channel: String? = null): CatchDetails? {
            val caught = state.taps.filter { it.outcome == TapOutcome.Caught }
            val tap = caught.lastOrNull { it.packageName == pending.packageName } ?: caught.lastOrNull()
            if (tap != null) return CatchDetails(pending.packageName ?: tap.packageName, tap.healthBefore,
                tap.healthAfter, tap.damage, tap.rule, channel)
            val record = state.healthHistory.lastOrNull { it.after < it.before } ?: return null
            return CatchDetails(pending.packageName, record.before, record.after, record.before - record.after, "", channel)
        }
    }
}
