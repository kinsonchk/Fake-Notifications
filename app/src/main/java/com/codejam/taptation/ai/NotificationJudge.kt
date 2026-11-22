package com.codejam.taptation.ai

import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.NotificationContext

/** What "Let us decide" concluded about one notification. Unsure never costs health. */
enum class Verdict { Important, Bait, Unsure }

/**
 * Judges one notification for apps left on "Let us decide". Implementations may block on the
 * network, so call them off the main thread. Notification title/text are sent to the judge only;
 * they must never be logged or persisted.
 */
interface NotificationJudge {
    /** False when the judge can't work at all (e.g. no API key): "Let us decide" keeps its fallback. */
    val isConfigured: Boolean get() = true

    fun judge(context: NotificationContext, config: GameConfig): Verdict
}
