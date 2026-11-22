package com.codejam.taptation.service

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Notification-access and battery helpers. Reusable by the onboarding screen.
 *
 * Notification access can't be granted in code: the user has to flip the toggle in system
 * settings. Re-check [isNotificationAccessGranted] on every ON_RESUME, because that's when the
 * user comes back from settings.
 */
object PermissionHelper {

    fun isNotificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Opens the system "Notification access" (notification listeners) list. */
    fun openNotificationAccessSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    /** "Display over other apps", needed for the caught overlay (optional: it falls back to a notification). */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Opens this app's "Display over other apps" page, or App info when the ROM lacks that screen.
     * Returns false when neither could be opened (the caller shows a message).
     */
    fun openOverlaySettings(context: Context): Boolean = tryLaunch(context,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null)))

    /** Opens this app's notification settings (when the system prompt can no longer be shown). */
    fun openAppNotificationSettings(context: Context): Boolean = tryLaunch(context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))

    /** True when the app is already exempt from battery optimization ("Unrestricted"). */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the battery optimization list. The user should set this app to Unrestricted
     * (App info > App battery usage > Unrestricted on Pixel) so the listener isn't killed.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        launch(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /** Like [launch], but never throws: false when neither the screen nor App info could open. */
    private fun tryLaunch(context: Context, intent: Intent): Boolean = try {
        launch(context, intent); true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Couldn't open ${intent.action} or App info", e); false
    }

    /** Falls back to this app's App info page if the OEM removed the requested screen. */
    private fun launch(context: Context, intent: Intent) {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
    Log.w(TAG, "No activity for ${intent.action}, opening App info instead", e)
    val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
    if (context !is Activity) fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(fallback)
}
}

private const val TAG = "FakeNotifs"
}
