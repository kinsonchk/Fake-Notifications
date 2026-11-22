package com.codejam.taptation.logic

/**
 * What a catch cost and why, for the caught pop-up. Recorded on the [PendingCatch] at catch time,
 * so it holds no notification title or text. [channel] is the notification type, when known.
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
        /** Null for catches saved before the details were recorded (snapshot v8). */
        fun of(pending: PendingCatch): CatchDetails? {
            val before = pending.healthBefore ?: return null
            val after = pending.healthAfter ?: return null
            return CatchDetails(pending.packageName, before, after, before - after, pending.rule, pending.channel)
        }
    }
}

/**
 * Everything a caught screen shows (in-app dialog or the overlay over other apps), built by the
 * backend (service/CatchFeedback.prompt). Screens render it and call back with the user's choice;
 * they never look into the game state to work it out.
 *
 * @property appLabel display name of the app behind the catch; null for a forced debug catch.
 * @property postponed the user chose "Not now"; the choice is still pending.
 * @property animate true the first time this catch is shown anywhere (overlay or dialog).
 */
data class CatchPrompt(
    val catchId: String,
    val packageName: String?,
    val appLabel: String?,
    val details: CatchDetails?,
    val continueExtraDamage: Int,
    val postponed: Boolean,
    val animate: Boolean,
) {
    /** "This should be important" needs a real app behind the catch. */
    val canCorrect: Boolean get() = packageName != null
}

/** "This should be important": the app (and notification type, when known) behind a catch. */
data class CorrectionRequest(val packageName: String, val appLabel: String, val channel: String?)
