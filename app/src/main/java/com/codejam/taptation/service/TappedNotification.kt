package com.codejam.taptation.service

/**
 * A notification the user opened by tapping it, copied out of the system's StatusBarNotification.
 *
 * Plain Kotlin types only, so code that consumes it can be unit tested without Android.
 *
 * @property packageName app that posted the notification, e.g. "com.instagram.android".
 * @property title EXTRA_TITLE, usually the sender or headline. Null if the app set none.
 * @property text EXTRA_BIG_TEXT if present (the full expanded text), otherwise EXTRA_TEXT.
 * @property category Notification.category, e.g. "msg", "social", "email", "call", "alarm".
 *   Many apps leave it null.
 * @property channelId notification channel the app posted on. Channel ids are app-defined.
 * @property key system key, unique per posted notification.
 * @property postTimeMillis when the notification was posted.
 * @property tapTimeMillis when the tap was detected.
 */
data class TappedNotification(
    val packageName: String,
    val title: String?,
    val text: String?,
    val category: String?,
    val channelId: String?,
    val key: String,
    val postTimeMillis: Long,
    val tapTimeMillis: Long,
)
