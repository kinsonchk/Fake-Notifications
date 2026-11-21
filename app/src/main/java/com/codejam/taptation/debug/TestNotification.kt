package com.codejam.taptation.debug

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codejam.taptation.MainActivity
import com.codejam.taptation.R
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a notification from our own app that produces REASON_CLICK when tapped.
 *
 * Both parts matter: without a contentIntent the tap does nothing, and without
 * setAutoCancel(true) the notification stays put and no removal callback fires.
 */
object TestNotification {
    private const val CHANNEL_ID = "debug_test"
    private const val TAG = "FakeNotifs"
    private val nextId = AtomicInteger(1000)

    /** Android 13+ needs the runtime POST_NOTIFICATIONS grant; older versions don't. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context) {
        ensureChannel(context)

        val id = nextId.getAndIncrement()
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, id, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_debug)
            .setContentTitle("Test notification #$id")
            .setContentText("Tap me to log a tap. Swipe me away to check nothing is logged.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
            Log.d(TAG, "Posted test notification id=$id")
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted, test notification not shown", e)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Debug test", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}



