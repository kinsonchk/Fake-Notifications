package com.codejam.taptation.logic

import android.util.Log

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
}

/** Placeholder until Person 2's handler is installed. Only logs. */
class LoggingTapHandler : TapHandler {
    override fun onTap(packageName: String) {
        Log.d("FakeNotifs", "TAP (stub handler): $packageName")
    }
}
