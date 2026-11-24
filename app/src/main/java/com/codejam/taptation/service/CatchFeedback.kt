package com.codejam.taptation.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codejam.taptation.MainActivity
import com.codejam.taptation.R
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.CatchPrompt
import com.codejam.taptation.logic.CorrectionRequest
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.PendingCatch
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Draws a caught prompt over other apps. Implemented by the UI (ui/overlay/CatchOverlay) and
 * installed by TaptationApplication, so this backend class decides when and what, never how.
 */
interface CatchSurface {
    /** Shows [prompt]; returns false when it can't (the backend then posts a notification instead). */
    fun show(prompt: CatchPrompt, actions: CatchActions): Boolean
    fun hide()
}

/** What the user can do on a caught prompt. Every screen (overlay or in-app dialog) calls these. */
interface CatchActions {
    /** Settles the catch; [ourApp] opens Taptation, otherwise the notification's original app. */
    fun choose(ourApp: Boolean)
    /** "Not now": hides the prompt; the choice stays pending and Taptation reminds about it. */
    fun postpone()
    /** "This should be important": opens Taptation with the correction sheet. Only when [CatchPrompt.canCorrect]. */
    fun correct()
}

/**
 * The catch flow's backend: turns the game's pending catch into a [CatchPrompt] (with app label and
 * cost), decides where to show it (overlay over other apps, a notification, or the in-app dialog when
 * Taptation is open) and carries out the user's choice. Never launches an activity over another app
 * on its own.
 */
class CatchFeedback(private val context: Context, private val apps: AppDirectory = AppDirectory(context)) {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val targets = ConcurrentHashMap<String, () -> Unit>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private lateinit var repository: GameRepository
    private var foreground = false
    private var lastChosenId: String? = null
    private val postponedId = MutableStateFlow<String?>(null)
    // Catches already shown once (anywhere): only the first showing animates the health loss.
    private val shownIds = mutableSetOf<String>()

    /** Draws the overlay over other apps; installed by TaptationApplication (tests install their own). */
    var surface: CatchSurface? = null

    private val _prompt = MutableStateFlow<CatchPrompt?>(null)
    /** The pending catch as the caught screens show it; null when nothing is pending. */
    val prompt: StateFlow<CatchPrompt?> = _prompt.asStateFlow()

    private val _homeRequests = MutableStateFlow(0L)
    /** Increments when the user chose "Go to Taptation": the app should show Home. */
    val homeRequests = _homeRequests.asStateFlow()

    private val _corrections = MutableStateFlow<CorrectionRequest?>(null)
    /** Set when the user taps "This should be important"; Taptation then offers the rule change. */
    val corrections = _corrections.asStateFlow()

    fun attach(repository: GameRepository) {
        this.repository = repository
        scope.launch {
            combine(repository.state.map { it?.pendingCatch }.distinctUntilChanged(), postponedId) { pending, postponed ->
                pending?.let { toPrompt(it, postponed == it.id) }
            }.collect { prompt ->
                _prompt.value = prompt
                hideEverywhere()
                if (prompt == null) { targets.clear(); postponedId.value = null }
                else {
                    // A newer catch superseded the old one: drop the old destination with it.
                    targets.keys.retainAll(setOf(prompt.catchId))
                    if (!foreground && !prompt.postponed) present(prompt)
                }
            }
        }
    }

    /** Actions for the prompt with [catchId]; ignored when that catch is no longer pending. */
    fun actionsFor(catchId: String): CatchActions = object : CatchActions {
        override fun choose(ourApp: Boolean) { pending(catchId)?.let { choose(it, ourApp) } }
        override fun postpone() { if (pending(catchId) != null) { postponedId.value = catchId; hideEverywhere() } }
        override fun correct() { pending(catchId)?.let(::requestCorrection) }
    }

    /** "Choose now" after a postpone: the prompt is shown again. */
    fun resume(catchId: String) { if (postponedId.value == catchId) postponedId.value = null }

    fun clearCorrection() { _corrections.value = null }

    fun rememberDestination(pending: PendingCatch, open: (() -> Unit)?) {
        if (targets.size >= 8) targets.clear()
        if (open != null) targets[pending.id] = open
    }

    fun setForeground(value: Boolean) {
        foreground = value
        hideEverywhere()
        if (!value) _prompt.value?.takeIf { !it.postponed }?.let(::present)
    }

    /** Releases observers for isolated device tests; the app instance lives for the process. */
    fun close() { scope.cancel(); hideEverywhere(); targets.clear() }

    private fun pending(catchId: String): PendingCatch? =
        repository.state.value?.pendingCatch?.takeIf { it.id == catchId }

    private fun toPrompt(pending: PendingCatch, postponed: Boolean): CatchPrompt {
        val animate = synchronized(shownIds) { shownIds.add(pending.id) }
        return CatchPrompt(
            catchId = pending.id,
            packageName = pending.packageName,
            appLabel = pending.packageName?.let(apps::label),
            details = CatchDetails.of(pending),
            continueExtraDamage = pending.continueExtraDamage,
            postponed = postponed,
            animate = animate,
        )
    }

    private fun choose(pending: PendingCatch, ourApp: Boolean) {
        if (lastChosenId == pending.id) return
        lastChosenId = pending.id
        val target = targets[pending.id]
        repository.settleCatchChoice(pending.id, continueOriginalApp = !ourApp).whenComplete { _, failure -> main.post {
            if (failure != null) {
                lastChosenId = null
                Toast.makeText(context, R.string.caught_choice_failed, Toast.LENGTH_LONG).show()
                return@post
            }
            targets.remove(pending.id)
            if (ourApp) {
                _homeRequests.value++
                context.startActivity(homeIntent())
            } else openOriginal(pending, target)
        } }
    }

    private fun openOriginal(pending: PendingCatch, target: (() -> Unit)?) {
        try {
            if (target != null) target()
            else pending.packageName?.let(context.packageManager::getLaunchIntentForPackage)
                ?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } catch (error: Exception) {
            Log.w("FakeNotifs", "Original notification action unavailable", error)
            val launch = pending.packageName?.let(context.packageManager::getLaunchIntentForPackage)
            if (launch == null || runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
                Toast.makeText(context, R.string.caught_original_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestCorrection(pending: PendingCatch) {
        val pkg = pending.packageName ?: return
        _corrections.value = CorrectionRequest(pkg, apps.label(pkg), pending.channel)
        choose(pending, true)
    }

    private fun present(prompt: CatchPrompt) {
        val surface = surface
        if (surface != null && PermissionHelper.canDrawOverlays(context)) {
            val shown = try { surface.show(prompt, actionsFor(prompt.catchId)) } catch (error: RuntimeException) {
                Log.w("FakeNotifs", "Caught overlay failed", error); false
            }
            if (shown) return
            surface.hide()
            Log.w("FakeNotifs", "Caught overlay unavailable; using notification")
        }
        postNotification()
    }

    private fun hideEverywhere() {
        surface?.hide()
        notifications.cancel(NOTIFICATION_ID)
    }

    private fun homeIntent() = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun postNotification() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        notifications.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.caught_channel_name), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, homeIntent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            notifications.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_debug).setContentTitle(context.getString(R.string.caught_notification_title))
                .setContentText(context.getString(R.string.caught_notification_text))
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (error: SecurityException) { Log.w("FakeNotifs", "Catch notification permission unavailable", error) }
    }

    companion object { const val CHANNEL = "caught_feedback"; private const val NOTIFICATION_ID = 900_001 }
}
