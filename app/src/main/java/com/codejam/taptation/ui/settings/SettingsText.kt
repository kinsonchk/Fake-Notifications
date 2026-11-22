package com.codejam.taptation.ui.settings

import com.codejam.taptation.logic.Difficulty

internal fun listSummary(values: Set<String>, empty: String): String {
    val sorted = values.sortedBy { it.lowercase() }
    return when {
        sorted.isEmpty() -> empty
        sorted.size <= 3 -> sorted.joinToString(", ")
        else -> sorted.take(3).joinToString(", ") + " +${sorted.size - 3}"
    }
}

internal fun chanceSummary(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.Easy -> "Let us decide: about 1 in 5 AI-classified distracting taps."
    Difficulty.Medium -> "Let us decide: about 2 in 5 AI-classified distracting taps."
    Difficulty.Hard -> "Let us decide: about 7 in 10 AI-classified distracting taps."
} + " Always distracting catches eligible taps; Always important stays safe."

internal fun timeLimitText(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds == 60 -> "1 minute"
    seconds < 3600 -> "${seconds / 60} minutes"
    seconds == 3600 -> "1 hour"
    else -> "${seconds / 3600} hours"
}
