package com.codejam.taptation.feedback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codejam.taptation.CaughtResultActivity
import com.codejam.taptation.R
import com.codejam.taptation.logic.TapOutcome
import com.codejam.taptation.logic.TapResult

/** Presents caught feedback without replacing the source notification's PendingIntent. */
object CaughtFeedbackManager {
    private const val TAG = "FakeNotifs"
    private const val PREFS = "caught_feedback"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
    private const val KEY_PENDING_RESULT = "pending_result"
    private const val CHANNEL_ID = "caught_feedback"
    private const val NOTIFICATION_ID = 8701
    private const val PENDING_INTENT_ID = 8702
    private val main = Handler(Looper.getMainLooper())
    private var activeOverlay: View? = null
    private var activeWindowManager: WindowManager? = null

    fun isOverlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OVERLAY_ENABLED, false)

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasPendingResult(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PENDING_RESULT, false)

    fun clearPendingResult(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PENDING_RESULT, false).apply()
    }

    /** Called after GameRepository has committed the catch, on its worker thread. */
    fun show(context: Context, result: TapResult) {
        if (result.outcome != TapOutcome.Caught) return
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PENDING_RESULT, true).apply()
        main.post {
            if (isOverlayEnabled(appContext) && canDrawOverlays(appContext)) {
                if (!showOverlay(appContext, result)) postFallback(appContext, result)
            } else {
                postFallback(appContext, result)
            }
        }
    }

    private fun showOverlay(context: Context, result: TapResult): Boolean {
        return try {
            removeActiveOverlay()
            val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
            val view = createOverlay(context, result)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }
            windowManager.addView(view, params)
            view.requestFocus()
            activeOverlay = view
            activeWindowManager = windowManager
            true
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Could not show caught overlay; posting fallback notification", failure)
            removeActiveOverlay()
            false
        }
    }

    private fun createOverlay(context: Context, result: TapResult): View {
        val damage = result.state.taps.lastOrNull { it.reason == "caught" }?.damage ?: 0
        val root = FrameLayout(context).apply {
            setBackgroundColor(0xCC000000.toInt())
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { /* Consume touches outside the card so the source app stays paused. */ }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    clearPendingResult(context)
                    removeActiveOverlay()
                    true
                } else false
            }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 28), dp(context, 24), dp(context, 24))
            background = GradientDrawable().apply {
                setColor(Color.rgb(32, 31, 36))
                cornerRadius = dp(context, 24).toFloat()
            }
            elevation = dp(context, 12).toFloat()
        }
        val title = TextView(context).apply {
            text = "You got caught"
            setTextColor(Color.WHITE)
            textSize = 28f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val message = TextView(context).apply {
            text = if (damage > 0) "Your tree lost $damage health. It is now at ${result.state.health}/100."
            else "Your tree is now at ${result.state.health}/100 health."
            setTextColor(Color.LTGRAY)
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 16), 0, dp(context, 20))
        }
        val continueButton = Button(context).apply {
            text = "Continue in this app"
            setOnClickListener {
                clearPendingResult(context)
                removeActiveOverlay()
            }
        }
        val openButton = Button(context).apply {
            text = "Open Taptation"
            setOnClickListener {
                removeActiveOverlay()
                try {
                    clearPendingResult(context)
                    context.startActivity(CaughtResultActivity.createIntent(context))
                } catch (failure: RuntimeException) {
                    Log.e(TAG, "Could not open catch result screen", failure)
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_PENDING_RESULT, true).apply()
                    postFallback(context, result)
                }
            }
        }
        card.addView(title, LinearLayout.LayoutParams(-1, -2))
        card.addView(message, LinearLayout.LayoutParams(-1, -2))
        card.addView(continueButton, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(context, 8)
        })
        card.addView(openButton, LinearLayout.LayoutParams(-1, -2))
        root.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply {
            leftMargin = dp(context, 24)
            rightMargin = dp(context, 24)
        })
        return root
    }

    private fun removeActiveOverlay() {
        val view = activeOverlay ?: return
        activeOverlay = null
        val manager = activeWindowManager
        activeWindowManager = null
        try {
            manager?.removeView(view)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Overlay was already removed", failure)
        }
    }

    private fun postFallback(context: Context, result: TapResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Catch saved; notification permission is off, result will show in Taptation")
            return
        }
        try {
            ensureChannel(context)
            val damage = result.state.taps.lastOrNull { it.reason == "caught" }?.damage ?: 0
            val pendingIntent = PendingIntent.getActivity(
                context,
                PENDING_INTENT_ID,
                CaughtResultActivity.createIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_debug)
                .setContentTitle("You got caught")
                .setContentText("Your tree lost $damage health. Tap to see your progress.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (failure: SecurityException) {
            Log.w(TAG, "Catch saved; Android did not allow the fallback notification", failure)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Catch feedback", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
