package com.codejam.taptation.stats

import java.util.Locale

/** Plain-language labels for stats; pure so it is unit-tested. */
object StatsText {
    /** Android notification category codes -> names people recognise. */
    fun categoryName(code: String?): String = when (code?.trim()?.lowercase(Locale.ROOT)) {
        "social" -> "Social"
        "promo" -> "Promotions"
        "msg", "message" -> "Messages"
        "email" -> "Email"
        "call", "missed_call", "voicemail" -> "Calls"
        "alarm", "stopwatch" -> "Alarms"
        "reminder" -> "Reminders"
        "event" -> "Events"
        "transport" -> "Transport"
        "progress" -> "Progress"
        "service", "sys", "status" -> "System"
        "recommendation", "reco" -> "Recommendations"
        "navigation" -> "Navigation"
        "workout" -> "Workouts"
        "err" -> "Errors"
        null, "", "unknown" -> "No category"
        else -> code.trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /** "2 opened for every 1 swiped away"; null when nothing was swiped away. Same taps / dismissals math as before. */
    fun openedPerSwiped(taps: Int, dismissals: Int): String? {
        if (dismissals == 0) return null
        val ratio = taps.toDouble() / dismissals
        val number = if (ratio == ratio.toLong().toDouble()) ratio.toLong().toString() else "%.1f".format(Locale.US, ratio)
        return "$number opened for every 1 swiped away"
    }

    /** Big number shown beside [openedPerSwiped], e.g. "2 : 1". */
    fun ratioHeadline(taps: Int, dismissals: Int): String {
        if (dismissals == 0) return "—"
        val ratio = taps.toDouble() / dismissals
        val number = if (ratio == ratio.toLong().toDouble()) ratio.toLong().toString() else "%.1f".format(Locale.US, ratio)
        return "$number : 1"
    }

    enum class Emptiness { NoActivity, NoCatches, HasCatches }

    /** Separates "nothing recorded" from "recorded, but nothing caught you". */
    fun emptiness(summary: StatsSummary): Emptiness = when {
        summary.taps + summary.dismissals == 0 -> Emptiness.NoActivity
        summary.caught == 0 -> Emptiness.NoCatches
        else -> Emptiness.HasCatches
    }
}
