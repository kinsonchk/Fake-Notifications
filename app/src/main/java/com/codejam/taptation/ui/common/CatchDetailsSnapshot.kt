package com.codejam.taptation.ui.common

import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.PendingCatch
import com.codejam.taptation.logic.TapOutcome

/** Read-only presentation snapshot. Never animate an unrelated older catch for a manual catch. */
fun catchDetailsForUi(state: GameState, pending: PendingCatch): CatchDetails? {
    val timestamp = pending.id.substringBeforeLast('-').toLongOrNull() ?: return null
    val tap = state.taps.lastOrNull { it.timestampMillis == timestamp && it.outcome == TapOutcome.Caught &&
        (pending.packageName == null || it.packageName == pending.packageName) }
    if (tap != null) return CatchDetails(tap.packageName, tap.healthBefore, tap.healthAfter, tap.damage, tap.rule, null)
    val record = state.healthHistory.lastOrNull { it.timestampMillis == timestamp && it.reason == "catch" } ?: return null
    return CatchDetails(pending.packageName, record.before, record.after, record.before - record.after, "", null)
}
