package com.codejam.taptation.service

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.MonitoringStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * What Android currently allows Taptation to do. [overlay] and [postNotifications] are optional
 * (the caught overlay falls back to a notification); [notificationAccess] is required.
 */
data class PermissionStatus(
    val notificationAccess: Boolean = false,
    val overlay: Boolean = false,
    val postNotifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    /** The system notification prompt can still be shown (Android 13+, not asked before). */
    val notificationPromptAvailable: Boolean = false,
) {
    /** Optional permissions still missing, for a "needs attention" badge. */
    val catchReminderIssues: Int get() = listOf(overlay, postNotifications).count { !it }
}

/**
 * The single source of Android permission and listener state for every screen and for the
 * monitoring status. Refreshed by TaptationApplication whenever an activity resumes (the user may
 * come back from system settings), so screens never call Android to check permissions.
 */
class SystemStatus(context: Context, listenerConnected: StateFlow<Boolean>, game: StateFlow<GameState?>, scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _permissions = MutableStateFlow(read())

    val permissions: StateFlow<PermissionStatus> = _permissions.asStateFlow()

    /**
     * Home's one status area: setup needed, paused, connection interrupted or monitoring. Follows
     * permissions, the listener connection and the game config (a timed pause ends on its own: the
     * game repository refreshes itself when it does).
     */
    val monitoring: StateFlow<MonitoringStatus?> = combine(_permissions, listenerConnected, game) { status, connected, state ->
        state?.let { MonitoringStatus.from(status.notificationAccess, connected, it.config, System.currentTimeMillis()) }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    fun refresh() { _permissions.value = read() }

    /** Android only shows the notification prompt a couple of times: remember that we asked. */
    fun markNotificationPromptShown() {
        prefs.edit().putBoolean(ASKED_NOTIFICATIONS, true).apply()
        refresh()
    }

    private fun read() = PermissionStatus(
        notificationAccess = PermissionHelper.isNotificationAccessGranted(appContext),
        overlay = Settings.canDrawOverlays(appContext),
        postNotifications = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
        batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(appContext),
        notificationPromptAvailable = Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean(ASKED_NOTIFICATIONS, false),
    )

    private companion object {
        // Same file and key the Catch reminders page used, so an earlier "asked" answer is kept.
        const val PREFS = "catch_reminders"
        const val ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
