package com.codejam.taptation.service

import android.content.ComponentName
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.logic.NotificationContext
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
    // Text stays in memory only; bound the cache even if removal callbacks are missed.
    private val contexts = object : LinkedHashMap<String, NotificationContext>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NotificationContext>?): Boolean =
            size > 500
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "onListenerConnected")
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        contexts.clear()
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
        contexts.clear()
        _connected.value = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // update=true: same key already active, so the app replaced it (e.g. a new chat message).
        val update = contexts.containsKey(sbn.key)
        contexts[sbn.key] = contextFor(sbn)
        val signals = NotificationSignals.from(sbn, currentRankingOrNull())
        Log.d(TAG, "posted pkg=${sbn.packageName} update=$update " +
            "${signals.describe(System.currentTimeMillis())} key=${sbn.key}")
    }

    // Only the three-argument overload is overridden. The base class's three-argument version
    // forwards to the one-argument one, so overriding both would handle each removal twice.
    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        val pkg = sbn.packageName
        val context = contexts.remove(sbn.key) ?: contextFor(sbn)
        val flags = sbn.notification?.flags ?: 0
        val signals = NotificationSignals.from(sbn, rankingMap)
        Log.d(TAG, "removed pkg=$pkg reason=${TapDetection.reasonName(reason)}($reason) " +
            "${signals.describe(System.currentTimeMillis())} key=${sbn.key}")

        val isTap = TapDetection.shouldDispatch(
            reason = reason,
            flags = flags,
            channelId = sbn.notification?.channelId,
            packageName = pkg,
            ownPackageName = packageName,
            includeOwnPackage = isDebugBuild(),
        )
        if (!isTap) return

        TapLog.record(pkg)
        try {
            TapDispatcher.dispatch(context)
        } catch (e: Exception) {
            // A bug in the handler must not take down the listener.
            Log.e(TAG, "TapHandler threw for $pkg", e)
        }
    }

    private fun contextFor(sbn: StatusBarNotification): NotificationContext {
        val notification = sbn.notification
        val extras = notification.extras
        return NotificationContext(
            packageName = sbn.packageName,
            key = sbn.key,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            category = notification.category,
        )
    }

    private fun currentRankingOrNull(): RankingMap? =
        try {
            currentRanking
        } catch (e: RuntimeException) {
            null
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
