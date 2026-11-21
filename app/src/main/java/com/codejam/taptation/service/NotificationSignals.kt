package com.codejam.taptation.service

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification

/**
 * Classification signals of one notification, for Logcat only.
 *
 * Holds metadata, never the title or text: those must not be logged or persisted.
 * Nullable fields are null when Android doesn't provide them (older API level, or the
 * ranking for the key is missing, which is common once a notification was removed).
 */
data class NotificationSignals(
    val category: String?,
    val channelId: String?,
    val channelName: String?,
    /** `NotificationManager.IMPORTANCE_*` from the ranking. */
    val importance: Int?,
    /** `Ranking.isConversation`, API 31+. */
    val isConversation: Boolean?,
    /** `Notification.EXTRA_IS_GROUP_CONVERSATION`, API 28+, only set by MessagingStyle. */
    val isGroupConversation: Boolean?,
    /** Short template name, for example `MessagingStyle` or `BigTextStyle`. */
    val style: String?,
    val flags: Int,
    val hasContentIntent: Boolean,
    val hasGroup: Boolean,
    val postTimeMillis: Long,
) {
    /**
     * One-line `key=value` summary. `autoCancel=false` means a tap won't remove the
     * notification, so no REASON_CLICK and no tap is ever detected for it.
     */
    fun describe(nowMillis: Long): String = buildString {
        append("cat=").append(category ?: "none")
        append(" channelId=").append(channelId ?: "none")
        append(" channel=").append(channelName?.let { "\"$it\"" } ?: "?")
        append(" importance=").append(importance?.let(::importanceName) ?: "?")
        append(" conversation=").append(isConversation ?: "?")
        append(" groupConversation=").append(isGroupConversation ?: "?")
        append(" style=").append(style ?: "none")
        append(" autoCancel=").append(flags and Notification.FLAG_AUTO_CANCEL != 0)
        append(" ongoing=").append(flags and Notification.FLAG_ONGOING_EVENT != 0)
        append(" summary=").append(flags and Notification.FLAG_GROUP_SUMMARY != 0)
        append(" group=").append(hasGroup)
        append(" contentIntent=").append(hasContentIntent)
        append(" postTime=").append(postTimeMillis)
        append(" age=").append(nowMillis - postTimeMillis).append("ms")
    }

    companion object {
        fun importanceName(importance: Int): String = when (importance) {
            NotificationManager.IMPORTANCE_NONE -> "NONE"
            NotificationManager.IMPORTANCE_MIN -> "MIN"
            NotificationManager.IMPORTANCE_LOW -> "LOW"
            NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
            NotificationManager.IMPORTANCE_HIGH -> "HIGH"
            NotificationManager.IMPORTANCE_MAX -> "MAX"
            NotificationManager.IMPORTANCE_UNSPECIFIED -> "UNSPECIFIED"
            else -> "UNKNOWN($importance)"
        }

        /** `android.app.Notification$MessagingStyle` -> `MessagingStyle`. */
        fun styleName(template: String?): String? =
            template?.substringAfterLast('$')?.substringAfterLast('.')?.ifBlank { null }

        fun from(sbn: StatusBarNotification, rankingMap: RankingMap?): NotificationSignals {
            val notification = sbn.notification
            val extras = notification.extras
            val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
            return NotificationSignals(
                category = notification.category,
                channelId = notification.channelId,
                channelName = ranking?.channel?.name?.toString(),
                importance = ranking?.importance,
                isConversation = if (ranking != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    ranking.isConversation else null,
                isGroupConversation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    extras?.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION) == true
                ) extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION) else null,
                style = styleName(extras?.getString(Notification.EXTRA_TEMPLATE)),
                flags = notification.flags,
                hasContentIntent = notification.contentIntent != null,
                hasGroup = notification.group != null,
                postTimeMillis = sbn.postTime,
            )
        }
    }
}
