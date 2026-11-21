package com.codejam.taptation.service

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codejam.taptation.logic.TapDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches notification removals and reports taps to [TapDispatcher].
 *
 * No foreground service needed: once the user grants Notification access, the system binds
 * this service on its own and keeps it bound.
 *
 * Callbacks arrive on the main thread. Keep them to filter + log + dispatch.
 */
class TapListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "onListenerConnected")
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "onListenerDisconnected")
        _connected.value = false
        // If access was revoked, there is nothing to rebind to: just stop quietly.
        if (!PermissionHelper.isNotificationAccessGranted(this)) {
            Log.d(TAG, "Notification access revoked, not requesting rebind")
            return
        }
        try {
            requestRebind(ComponentName(this, TapListenerService::class.java))
            Log.d(TAG, "requestRebind sent")
        } catch (e: RuntimeException) {
            Log.w(TAG, "requestRebind failed", e)
        }
    }

    override fun onDestroy() {
        _connected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Log.d(TAG, "posted pkg=${sbn.packageName} key=${sbn.key}")
    }

    // Only the three-argument overload is overridden. The base class's three-argument version
    // forwards to the one-argument one, so overriding both would handle each removal twice.
    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        val pkg = sbn.packageName
        val flags = sbn.notification?.flags ?: 0
        Log.d(TAG, "removed pkg=$pkg reason=${TapDetection.reasonName(reason)}($reason) key=${sbn.key}")

        val isTap = TapDetection.shouldDispatch(
            reason = reason,
            flags = flags,
            packageName = pkg,
            ownPackageName = packageName,
            includeOwnPackage = isDebugBuild(),
        )
        if (!isTap) return

        TapLog.record(pkg)
        try {
            TapDispatcher.dispatch(pkg)
        } catch (e: Exception) {
            // A bug in the handler must not take down the listener.
            Log.e(TAG, "TapHandler threw for $pkg", e)
        }
    }

    private fun isDebugBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val TAG = "FakeNotifs"

        private val _connected = MutableStateFlow(false)

        /** True while the system has this listener bound. For the debug screen. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
    }
}
