package com.codejam.taptation.debug

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codejam.taptation.MainActivity
import com.codejam.taptation.R
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.service.DebugSimulation
import com.codejam.taptation.service.TapDetection
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a notification from our own app that produces REASON_CLICK when tapped.
 *
 * Both parts matter: without a contentIntent the tap does nothing, and without
 * setAutoCancel(true) the notification stays put and no removal callback fires.
 */
object TestNotification {
    // The tap filter only counts our own notifications from this channel.
    private const val CHANNEL_ID = TapDetection.OWN_TEST_CHANNEL_ID
    private const val TAG = "FakeNotifs"
    private val nextId = AtomicInteger(1000)

    /** Android 13+ needs the runtime POST_NOTIFICATIONS grant; older versions don't. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context) {
        val id = nextId.getAndIncrement()
        val notification = builder(context, id)
            .setContentTitle("Test notification #$id")
            .setContentText("Tap me to log a tap. Swipe me away to check nothing is logged.")
            .build()
        notify(context, id, notification)
    }

    /**
     * Posts a notification described by [spec], standing in for another app (debug builds only).
     *
     * Title, text, category and action buttons are set on the notification itself. Package,
     * channel name, conversation flag and app category go in [DebugSimulation] extras, which the
     * listener applies only to this channel of our own package. Blank fields are left unset.
     * [NotificationContext.key] and [NotificationContext.ageMillis] are ignored: the system
     * assigns the key and the age is measured at tap time. [largeIcon] is the picture shown next to
     * the text (the status-bar icon stays the debug icon).
     */
    fun post(context: Context, spec: NotificationContext, largeIcon: Bitmap? = null) {
        val id = nextId.getAndIncrement()
        val simulation = DebugSimulation(
            packageName = spec.packageName.trim().takeIf(String::isNotEmpty),
            channelName = spec.channelName?.trim()?.takeIf(String::isNotEmpty),
            isConversation = spec.isConversation,
            appCategory = spec.appCategory,
        )
        val builder = builder(context, id)
            .setContentTitle(spec.title?.takeIf(String::isNotBlank))
            .setContentText(spec.text?.takeIf(String::isNotBlank))
            .setCategory(spec.category?.trim()?.takeIf(String::isNotEmpty))
            .addExtras(Bundle().apply { simulation.toExtras().forEach(::putString) })
            .setLargeIcon(largeIcon)
        spec.text?.takeIf(String::isNotBlank)?.let {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(it))
        }
        spec.actions.map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, title ->
            // Action buttons only open the app: pressing one is not a REASON_CLICK tap.
            builder.addAction(0, title, openAppIntent(context, id * 10 + index + 1))
        }
        notify(context, id, builder.build())
    }

    private fun builder(context: Context, id: Int): NotificationCompat.Builder {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_debug)
            .setContentIntent(openAppIntent(context, id * 10))
            .setAutoCancel(true)
    }

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, requestCode, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
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
