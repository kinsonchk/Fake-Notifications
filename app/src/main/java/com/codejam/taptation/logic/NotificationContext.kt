package com.codejam.taptation.logic

/**
 * What the listener knows about a tapped notification.
 *
 * Notification contents are transient: title, text, action titles and contact identities are
 * never persisted or logged. Only user-entered VIP names belong in saved settings.
 *
 * @property ageMillis time since Android's postTime, which restarts on every update (each new
 *   chat message re-posts the same key), set by the listener at removal.
 * @property firstSeenAgeMillis time since the listener first saw this key; updates keep it. Equals
 *   [ageMillis] when the listener didn't see the first post (e.g. it was restarted).
 * @property actions titles of the notification's action buttons (e.g. "Reply", "Mark as read"),
 *   in display order. Empty when the notification has none.
 * @property judged the AI judge's verdict for "Let us decide", set by the listener at removal. Null
 *   when no judge is set up (no API key); Unknown when it is pending, failed or unsure.
 */
data class NotificationContext(
    val packageName: String,
    val key: String? = null,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
    val appCategory: Int? = null,
    val isConversation: Boolean? = null,
    val channelName: String? = null,
    val ageMillis: Long? = null,
    val firstSeenAgeMillis: Long? = null,
    val actions: List<String> = emptyList(),
    val judged: Classification? = null,
    /** Transient user action for the original notification; never persisted or invoked automatically. */
    val openOriginal: (() -> Unit)? = null,
)
