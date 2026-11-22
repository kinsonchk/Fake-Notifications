package com.codejam.taptation.service

import android.app.Notification
import android.service.notification.NotificationListenerService

/**
 * Pure decision logic for "was this notification removal a user tap?".
 * No Android framework calls here, only constants, so it runs in plain JVM unit tests.
 */
object TapDetection {

    /**
     * Channel of the debug test notification. In debug builds, this is the only channel of our
     * own package that counts as a tap. Our other notifications (for example a future "you got
     * caught" alert) must never count, or tapping one could trigger another catch in a loop.
     */
    const val OWN_TEST_CHANNEL_ID = "debug_test"

    /**
     * True only for REASON_CLICK: the user tapped the notification and it auto-cancelled.
     *
     * Everything else is ignored on purpose:
     * - REASON_CANCEL: swiped away by the user.
     * - REASON_CANCEL_ALL: "Clear all".
     * - REASON_APP_CANCEL / REASON_APP_CANCEL_ALL: the posting app removed it.
     * - REASON_GROUP_SUMMARY_CANCELED: a group summary removed because its children went away
     *   (tapping a child can trigger this, so counting it would double-count the tap).
     * - Timeouts, snoozes, channel/package changes, etc.
     */
    fun isTap(reason: Int): Boolean = reason == NotificationListenerService.REASON_CLICK

    /** What a removal means for stats: a tap, a dismissal, or neither. */
    enum class RemovalKind { Tap, Dismiss, Ignored }

    /**
     * True for removals the user caused without opening the notification: a swipe
     * (REASON_CANCEL) or "Clear all" (REASON_CANCEL_ALL). Everything else (app cancel, timeout,
     * snooze, group summary...) is neither a tap nor a dismissal.
     */
    fun isDismiss(reason: Int): Boolean =
        reason == NotificationListenerService.REASON_CANCEL ||
            reason == NotificationListenerService.REASON_CANCEL_ALL

    /**
     * Whether a notification could ever count as a tap or dismissal: not a group summary, not
     * ongoing, and not ours unless it is the debug test channel. The listener judges only these
     * with the AI, so music/navigation updates and our own alerts never spend quota.
     */
    fun isCountable(
        flags: Int,
        channelId: String?,
        packageName: String,
        ownPackageName: String,
        includeOwnPackage: Boolean,
    ): Boolean {
        // Summaries are containers, not something the user meant to open or dismiss.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        // Ongoing notifications (music, navigation, downloads) aren't tapped or swiped away meaningfully.
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        return packageName != ownPackageName || (includeOwnPackage && channelId == OWN_TEST_CHANNEL_ID)
    }

    /**
     * Classifies a removal with the same filters for taps and dismissals: group summaries,
     * ongoing notifications and our own non-test notifications are always [RemovalKind.Ignored].
     */
    fun removalKind(
        reason: Int,
        flags: Int,
        channelId: String?,
        packageName: String,
        ownPackageName: String,
        includeOwnPackage: Boolean,
    ): RemovalKind {
        val kind = when {
            isTap(reason) -> RemovalKind.Tap
            isDismiss(reason) -> RemovalKind.Dismiss
            else -> return RemovalKind.Ignored
        }
        return if (isCountable(flags, channelId, packageName, ownPackageName, includeOwnPackage)) kind
        else RemovalKind.Ignored
    }

    /**
     * Full filter applied before a removal is handed to [com.codejam.taptation.logic.TapDispatcher].
     *
     * @param flags `Notification.flags` of the removed notification.
     * @param channelId `Notification.channelId` of the removed notification.
     * @param includeOwnPackage true in debug builds so the in-app test notification counts.
     */
    fun shouldDispatch(
        reason: Int,
        flags: Int,
        channelId: String?,
        packageName: String,
        ownPackageName: String,
        includeOwnPackage: Boolean,
    ): Boolean = removalKind(reason, flags, channelId, packageName, ownPackageName, includeOwnPackage) ==
        RemovalKind.Tap

    /** Readable name for logs. */
    fun reasonName(reason: Int): String = when (reason) {
        NotificationListenerService.REASON_CLICK -> "CLICK"
        NotificationListenerService.REASON_CANCEL -> "CANCEL"
        NotificationListenerService.REASON_CANCEL_ALL -> "CANCEL_ALL"
        NotificationListenerService.REASON_ERROR -> "ERROR"
        NotificationListenerService.REASON_PACKAGE_CHANGED -> "PACKAGE_CHANGED"
        NotificationListenerService.REASON_USER_STOPPED -> "USER_STOPPED"
        NotificationListenerService.REASON_PACKAGE_BANNED -> "PACKAGE_BANNED"
        NotificationListenerService.REASON_APP_CANCEL -> "APP_CANCEL"
        NotificationListenerService.REASON_APP_CANCEL_ALL -> "APP_CANCEL_ALL"
        NotificationListenerService.REASON_LISTENER_CANCEL -> "LISTENER_CANCEL"
        NotificationListenerService.REASON_LISTENER_CANCEL_ALL -> "LISTENER_CANCEL_ALL"
        NotificationListenerService.REASON_GROUP_SUMMARY_CANCELED -> "GROUP_SUMMARY_CANCELED"
        NotificationListenerService.REASON_GROUP_OPTIMIZATION -> "GROUP_OPTIMIZATION"
        NotificationListenerService.REASON_PACKAGE_SUSPENDED -> "PACKAGE_SUSPENDED"
        NotificationListenerService.REASON_PROFILE_TURNED_OFF -> "PROFILE_TURNED_OFF"
        NotificationListenerService.REASON_UNAUTOBUNDLED -> "UNAUTOBUNDLED"
        NotificationListenerService.REASON_CHANNEL_BANNED -> "CHANNEL_BANNED"
        NotificationListenerService.REASON_SNOOZED -> "SNOOZED"
        NotificationListenerService.REASON_TIMEOUT -> "TIMEOUT"
        else -> "UNKNOWN"
    }
}
