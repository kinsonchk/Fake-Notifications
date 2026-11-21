package com.codejam.taptation.logic

import android.util.Log
import java.util.concurrent.CompletableFuture

/**
 * Contract between the notification service (Person 1) and the consequence logic (Person 2).
 *
 * The service calls [onTap] once for every notification the user opened by tapping it.
 * Whitelisting, bait classification, the random roll and health all live behind this interface.
 *
 * Called on the main thread from the listener callback: keep it fast, and move disk or
 * network work to a background thread.
 */
interface TapHandler {
    fun onTap(packageName: String)

    /** Existing package-only handlers remain source-compatible. */
    fun onTap(context: NotificationContext) = onTap(context.packageName)
    /**
     * The user swiped the notification away or used "Clear all" (never a tap). No game effect;
     * used for stats. [NotificationContext.ageMillis] is set.
     */
    fun onDismiss(context: NotificationContext) {}
    fun onNotificationPosted(context: NotificationContext) {}
    fun onNotificationRemoved(key: String) {}
    fun onListenerReset() {}
}

interface ResultTapHandler : TapHandler {
    /** Android adapter returns asynchronously because evaluating a tap includes persistent I/O. */
    fun onTap(packageName: String, ageMillis: Long): CompletableFuture<TapResult>
}

/**
 * Single entry point the service uses. Person 2 plugs in the real logic at app start:
 *
 * ```
 * TapDispatcher.handler = MyRealTapHandler(...)
 * ```
 */
object TapDispatcher {
    @Volatile
    var handler: TapHandler = LoggingTapHandler()

    fun dispatch(packageName: String) = handler.onTap(packageName)
    fun dispatch(context: NotificationContext) = handler.onTap(context)
    fun dispatchDismiss(context: NotificationContext) = handler.onDismiss(context)
    fun dispatch(packageName: String, ageMillis: Long): CompletableFuture<TapResult> =
        (handler as? ResultTapHandler)?.onTap(packageName, ageMillis)
            ?: CompletableFuture<TapResult>().apply {
                completeExceptionally(IllegalStateException("No result-returning tap handler installed"))
            }
}

/** Placeholder until Person 2's handler is installed. Only logs. */
class LoggingTapHandler : TapHandler {
    override fun onTap(packageName: String) {
        Log.d("FakeNotifs", "TAP (stub handler): $packageName")
    }
}
