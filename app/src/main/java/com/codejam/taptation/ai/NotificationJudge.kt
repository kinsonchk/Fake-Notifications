package com.codejam.taptation.ai

import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Classification

/** What "Let us decide" concluded about one notification. Unsure never costs health. */
enum class Verdict { Important, Bait, Unsure }

/** Verdict plus a safe, content-free reason code for tap history and debug screens. */
data class JudgeResult(val verdict: Verdict, val reason: String) {
    val classification: Classification get() = when (verdict) {
        Verdict.Important -> Classification.Important
        Verdict.Bait -> Classification.Bait
        Verdict.Unsure -> Classification.Unknown
    }
}

/**
 * Judges one notification for apps left on "Let us decide". Implementations may block on the
 * network, so call them off the main thread. Notification title/text are sent to the judge only;
 * they must never be logged or persisted.
 */
interface NotificationJudge {
    /** False when the judge can't work (e.g. missing API key): "Let us decide" gets no penalty. */
    val isConfigured: Boolean get() = true

    fun judge(context: NotificationContext, config: GameConfig): Verdict

    /** Existing judges get a generic code unless they can provide a more specific cause. */
    fun judgeWithReason(context: NotificationContext, config: GameConfig): JudgeResult {
        val verdict = judge(context, config)
        val reason = when (verdict) {
            Verdict.Important -> "ai_important"
            Verdict.Bait -> "ai_bait"
            Verdict.Unsure -> "ai_unsure"
        }
        return JudgeResult(verdict, reason)
    }
}
