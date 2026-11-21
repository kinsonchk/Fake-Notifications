package com.codejam.taptation.logic

/**
 * What the listener knows about a tapped notification.
 *
 * Metadata is transient: notification text, action titles and contact identities are never
 * persisted or logged.
 *
 * @property actions titles of the notification's action buttons (e.g. "Reply", "Mark as read"),
 *   in display order. Empty when the notification has none.
 */
data class NotificationContext(
    val packageName: String,
    val key: String? = null,
    val title: String? = null,
    val text: String? = null,
    val category: String? = null,
    val appCategory: Int? = null,
    val actions: List<String> = emptyList(),
)
