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
import android.widget.FrameLayout
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import com.codejam.taptation.ui.common.AppLabels
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
    private val _corrections = MutableStateFlow<CorrectionRequest?>(null)
    /** Set when the user taps "This should be important"; Taptation then offers a rule choice. */
    val corrections = _corrections.asStateFlow()
    private val animators = mutableListOf<ValueAnimator>()
    private var animatedCatchId: String? = null
    private var animateCurrentCatch = false

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

    /** Opens Taptation with the rule choice for the notification that just caught the user. */
    fun requestCorrection(pending: PendingCatch) {
        val details = repository.state.value?.let { com.codejam.taptation.ui.common.catchDetailsForUi(it, pending) }
        val pkg = pending.packageName ?: details?.packageName
        if (pkg != null) _corrections.value = CorrectionRequest(pkg, details?.channel)
        choose(pending, true)
    }

    fun clearCorrection() { _corrections.tryEmit(null) }

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
        animateCurrentCatch = animatedCatchId != pending.id
        animatedCatchId = pending.id
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val background = (if (dark) NightSurface else androidx.compose.ui.graphics.Color.White).toArgb()
        val ink = (if (dark) NightInk else Ink).toArgb()
        val muted = (if (dark) NightMuted else MutedGreen).toArgb()
        val green = (if (dark) NightGreen else Forest).toArgb()
        val onGreen = (if (dark) Night else androidx.compose.ui.graphics.Color.White).toArgb()
        val border = (if (dark) NightBorder else BorderGreen).toArgb()
        val warm = (if (dark) 0xFF403226.toInt() else Warm.toArgb())
        val warmInk = (if (dark) NightInk.toArgb() else 0xFF723B2C.toInt())
        val danger = (if (dark) 0xFFEFAAAA.toInt() else 0xFFA23D3D.toInt())
        val earth = (if (dark) 0xFFC3A575.toInt() else Earth.toArgb())
        val details = repository.state.value?.let { com.codejam.taptation.ui.common.catchDetailsForUi(it, pending) }
        val pkg = pending.packageName ?: details?.packageName
        val appLabel = pkg?.let { AppLabels(context).label(it) }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            this.background = GradientDrawable().apply {
                setColor(background); cornerRadius = dp(26).toFloat(); setStroke(dp(1), border)
            }
            elevation = dp(8).toFloat()
        }
        val badge: LeafBadgeView? = null
        animateCurrentCatch = animatedCatchId != pending.id
        animatedCatchId = pending.id
        panel.addView(TextView(context).apply {
            setText(R.string.caught_title); textSize = 34f; setTextColor(ink); typeface = Typeface.SERIF
        })
        if (details != null && appLabel != null) {
            panel.addView(TextView(context).apply {
                text = com.codejam.taptation.ui.common.TapExplanations.catchReason(details.rule, appLabel, details.channel)
                textSize = 14f; setTextColor(ink); setPadding(0, dp(12), 0, dp(4))
            })
        } else {
            panel.addView(TextView(context).apply {
                setText(R.string.caught_message_no_extra); textSize = 14f; setTextColor(muted); setPadding(0, dp(12), 0, dp(4))
            })
        }
        if (details != null && details.damage > 0) panel.addView(healthBlock(details, badge, ink, muted, green, danger, warm, warmInk, border) { dp(it) })
        if (pending.continueExtraDamage > 0) panel.addView(TextView(context).apply {
            text = context.getString(R.string.caught_message, pending.continueExtraDamage)
            textSize = 14f; setTextColor(muted); setPadding(0, dp(8), 0, dp(8))
        })
        fun choice(label: String, primary: Boolean, action: () -> Unit) = Button(context).apply {
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
        (panel.getChildAt(panel.childCount - 1)).let { last ->
            (last.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
        }
        panel.addView(choice(context.getString(R.string.caught_our_app), true) { choose(pending, true) })
        val destination = appLabel ?: "the original app"
        val continueLabel = if (pending.continueExtraDamage > 0)
            context.getString(R.string.caught_original_app_with_penalty, destination, pending.continueExtraDamage)
        else context.getString(R.string.caught_original_app, destination)
        panel.addView(choice(continueLabel, false) { choose(pending, false) })
        panel.addView(choice(context.getString(R.string.caught_later), false) { removeOverlay() })
        if (pkg != null) panel.addView(Button(context).apply {
            setText(R.string.caught_correct); isAllCaps = false; textSize = 14f; setTextColor(green)
            backgroundTintList = null; stateListAnimator = null; this.background = null
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
            minHeight = dp(48)
            setOnClickListener { requestCorrection(pending) }
        })
        val params = WindowManager.LayoutParams(
            minOf(context.resources.displayMetrics.widthPixels - dp(32), dp(480)), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER; dimAmount = 0.45f }
        val scroll = android.widget.ScrollView(context).apply {
            isFillViewport = false
            addView(panel)
        }
        windows.addView(scroll, params)
        overlay = scroll
    }

    /** "Tree health 90 -> 80" with a shrinking bar, a floating "-10" and a falling leaf; final values when animations are off. */
    private fun healthBlock(details: com.codejam.taptation.logic.CatchDetails, badge: LeafBadgeView?, ink: Int, muted: Int,
        green: Int, danger: Int, warm: Int, warmInk: Int, track: Int, dp: (Int) -> Int,
    ): View {
        val lost = details.damage
        val chip = TextView(context).apply {
            text = "−$lost"; textSize = 12f; setTextColor(warmInk); typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(3), dp(10), dp(3)); alpha = 0f
            background = GradientDrawable().apply { setColor(warm); cornerRadius = dp(20).toFloat() }
        }
        val number = TextView(context).apply { textSize = 26f; setTextColor(ink); typeface = Typeface.SERIF }
        val floating = TextView(context).apply {
            text = "−$lost"; textSize = 14f; setTextColor(danger); typeface = Typeface.DEFAULT_BOLD; alpha = 0f
        }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        row.addView(TextView(context).apply { text = "💚 Health"; textSize = 14f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD })
        row.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(number)
        row.addView(TextView(context).apply { text = " / 100"; textSize = 12f; setTextColor(muted) })
        val frame = FrameLayout(context).apply {
            contentDescription = "Health ${details.healthAfter} out of 100, down $lost"
            addView(row, FrameLayout.LayoutParams(-1, -2))
            addView(floating, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        }
        val bar = HealthBarView(context, track, green)
        fun setValue(k: Float) {
            val value = details.healthBefore + (details.healthAfter - details.healthBefore) * k
            number.text = Math.round(value).toString(); bar.fraction = value / 100f; chip.alpha = k
        }
        setValue(0f)
        if (animateCurrentCatch && ValueAnimator.areAnimatorsEnabled()) {
            fun run(duration: Long, delay: Long, interpolator: android.animation.TimeInterpolator, update: (Float) -> Unit) {
                animators += ValueAnimator.ofFloat(0f, 1f).apply {
                    this.duration = duration; startDelay = delay; this.interpolator = interpolator
                    addUpdateListener { update(it.animatedValue as Float) }
                    start()
                }
            }
            run(1150, 0, android.view.animation.LinearInterpolator()) {
                setValue(com.codejam.taptation.ui.common.damageDropProgress(it))
                bar.fillColor = androidx.core.graphics.ColorUtils.blendARGB(green, danger,
                    com.codejam.taptation.ui.common.damageFlashMix(it))
            }
            run(550, 120, DecelerateInterpolator()) { badge?.fall = it }
            run(900, 150, android.view.animation.LinearInterpolator()) {
                floating.alpha = (if (it < .25f) it / .25f else 1f - (it - .25f) / .75f).coerceIn(0f, 1f)
                floating.translationY = -dp(16) * it
            }
        } else {
            setValue(1f); badge?.fall = 1f
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            background = GradientDrawable().apply {
                setColor((if (dark) Night else Cream).toArgb())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), track)
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) }
            addView(frame, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(8) })
        }
    }

    private fun removeOverlay() {
        animators.forEach { it.cancel() }
        animators.clear()
        overlay?.let { runCatching { windows.removeView(it) } }
        overlay = null
    }

    private fun homeIntent() = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun postNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Catch reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, homeIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            notifications.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_debug).setContentTitle("Caught you")
                .setContentText("A distracting notification reduced your health. Open Taptation to choose what to do next.")
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (error: SecurityException) { Log.w("FakeNotifs", "Catch notification permission unavailable", error) }
    }

    companion object { const val CHANNEL = "caught_feedback"; private const val NOTIFICATION_ID = 900_001 }
}

/** The app (and notification type, when known) behind a catch the user says should be important. */
data class CorrectionRequest(val packageName: String, val channel: String?)
