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
 * @property media what the message carries besides text (photo, GIF…), from the attachment's MIME
 *   type, a big picture, or the app's placeholder text ("📷 Photo"). Null when none was detected.
 *   Only the kind is known; the media itself is never read.
 * @property sender who sent the latest message, for chat notifications only: the MessagingStyle
 *   message's sender, else the title of a 1-to-1 chat (Discord and Instagram DMs put the user
 *   there), else the "Name:" prefix of a group line. Null when the notification isn't a chat.
 *   Personal data: transient like title/text, never logged or persisted.
 * @property isGroup true for a group chat, false for a 1-to-1 chat, null when unknown or not a chat.
 * @property judged the AI judge's verdict for "Let us decide", set by the listener at removal. Null
 *   when no judge is set up (no API key); Unknown when it is pending, failed or unsure.
 */
/** Kind of attachment a notification announces. See [NotificationContext.media]. */
enum class MediaKind { Image, Gif, Video, Sticker, Audio }

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
    val media: MediaKind? = null,
    val sender: String? = null,
    val isGroup: Boolean? = null,
    /** Transient user action for the original notification; never persisted or invoked automatically. */
    val openOriginal: (() -> Unit)? = null,
)
