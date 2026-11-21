package com.codejam.taptation.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import com.codejam.taptation.ui.theme.*
import com.codejam.taptation.MainActivity
import com.codejam.taptation.R
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.PendingCatch
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Presents a committed catch once. Never automatically launches an activity over another app. */
class CatchFeedback(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targets = ConcurrentHashMap<String, () -> Unit>()
    private val windows = context.getSystemService(WindowManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var repository: GameRepository
    private var foreground = false
    private var current: PendingCatch? = null
    private var overlay: View? = null
    private var lastChosenId: String? = null
    private val _homeRequests = MutableStateFlow(0L)
    val homeRequests = _homeRequests.asStateFlow()

    fun attach(repository: GameRepository) {
        this.repository = repository
        scope.launch {
            repository.state.map { it?.pendingCatch }.distinctUntilChanged().collect {
                current = it
                removeOverlay()
                notifications.cancel(NOTIFICATION_ID)
                if (it == null) targets.clear()
                else if (!foreground) present(it)
            }
        }
    }

    fun rememberDestination(pending: PendingCatch, open: (() -> Unit)?) {
        if (targets.size >= 8) targets.clear()
        if (open != null) targets[pending.id] = open
    }

    fun setForeground(value: Boolean) {
        foreground = value
        removeOverlay()
        notifications.cancel(NOTIFICATION_ID)
        if (!value) current?.let(::present)
    }

    /** Releases UI observers for isolated device tests; the app instance lives for the process. */
    fun close() { scope.cancel(); removeOverlay(); notifications.cancel(NOTIFICATION_ID); targets.clear() }

    fun choose(pending: PendingCatch, ourApp: Boolean) {
        if (lastChosenId == pending.id) return
        lastChosenId = pending.id
        val target = targets[pending.id]
        repository.settleCatchChoice(pending.id, continueOriginalApp = !ourApp).whenComplete { _, failure -> main.post {
            if (failure != null) {
                lastChosenId = null
                Toast.makeText(context, "Could not save your choice. Please try again.", Toast.LENGTH_LONG).show()
                return@post
            }
            // Don't hide a newer catch if it arrived while the old choice was being saved.
            if (current?.id == pending.id) { removeOverlay(); notifications.cancel(NOTIFICATION_ID) }
            targets.remove(pending.id)
            if (ourApp) {
                _homeRequests.value++
                context.startActivity(homeIntent())
            } else {
                try {
                    if (target != null) target()
                    else pending.packageName?.let { pkg ->
                        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                } catch (error: Exception) {
                    Log.w("FakeNotifs", "Original notification action unavailable", error)
                    val launch = pending.packageName?.let(context.packageManager::getLaunchIntentForPackage)
                    if (launch == null || runCatching {
                        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.isFailure) Toast.makeText(context, "Original app is no longer available", Toast.LENGTH_LONG).show()
                }
            }
        } }
    }

    private fun present(pending: PendingCatch) {
        if (Settings.canDrawOverlays(context)) {
            try { showOverlay(pending); return }
            catch (error: RuntimeException) {
                removeOverlay()
                Log.w("FakeNotifs", "Caught overlay unavailable; using notification", error)
            }
        }
        postNotification()
    }

    private fun showOverlay(pending: PendingCatch) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val background = (if (dark) NightSurface else androidx.compose.ui.graphics.Color.White).toArgb()
        val ink = (if (dark) NightInk else Ink).toArgb()
        val muted = (if (dark) NightMuted else MutedGreen).toArgb()
        val green = (if (dark) NightGreen else Forest).toArgb()
        val onGreen = (if (dark) Night else androidx.compose.ui.graphics.Color.White).toArgb()
        val border = (if (dark) NightBorder else BorderGreen).toArgb()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            this.background = GradientDrawable().apply {
                setColor(background); cornerRadius = dp(26).toFloat(); setStroke(dp(1), border)
            }
            elevation = dp(8).toFloat()
        }
        panel.addView(TextView(context).apply {
            setText(R.string.caught_overlay_label); textSize = 11f; setTextColor(green)
            letterSpacing = 0.1f; setPadding(0, 0, 0, dp(16))
        })
        panel.addView(TextView(context).apply {
            setText(R.string.caught_title); textSize = 34f; setTextColor(ink); typeface = Typeface.SERIF
        })
        panel.addView(TextView(context).apply {
            text = if (pending.continueExtraDamage > 0)
                context.getString(R.string.caught_message, pending.continueExtraDamage)
            else context.getString(R.string.caught_message_no_extra)
            textSize = 14f; setTextColor(muted); setPadding(0, dp(12), 0, dp(20))
        })
        fun choice(label: CharSequence, primary: Boolean, action: () -> Unit) = Button(context).apply {
            text = label; isAllCaps = false; textSize = 14f
            setTextColor(if (primary) onGreen else green)
            backgroundTintList = null; stateListAnimator = null
            this.background = GradientDrawable().apply {
                setColor(if (primary) green else background); cornerRadius = dp(16).toFloat()
                if (!primary) setStroke(dp(1), border)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            minHeight = dp(48); setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { action() }
        }
        panel.addView(choice(context.getText(R.string.caught_our_app), true) { choose(pending, true) })
        val continueLabel = if (pending.continueExtraDamage > 0)
            context.getString(R.string.caught_original_app_with_penalty, pending.continueExtraDamage)
        else context.getString(R.string.caught_original_app)
        panel.addView(choice(continueLabel, false) { choose(pending, false) })
        val params = WindowManager.LayoutParams(
            minOf(context.resources.displayMetrics.widthPixels - dp(32), dp(480)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER; dimAmount = 0.45f }
        windows.addView(panel, params)
        overlay = panel
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
    }

    private fun homeIntent() = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun postNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Caught feedback", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, homeIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            notifications.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_debug).setContentTitle("A moment to choose")
                .setContentText("Caught on a distracting notification. Open Taptation to choose your next step.")
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (error: SecurityException) { Log.w("FakeNotifs", "Catch notification permission unavailable", error) }
    }

    companion object { const val CHANNEL = "caught_feedback"; private const val NOTIFICATION_ID = 900_001 }
}
