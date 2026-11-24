package com.codejam.taptation.service

import android.content.ComponentName
import android.app.Notification
import android.os.Build
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.codejam.taptation.logic.TapDispatcher
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.TaptationApplication
import android.app.ActivityOptions
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
    // Text stays in memory only; bound the cache even if removal callbacks are missed. Access order
    // (true): an updated key moves to the end, so long-lived notifications aren't evicted first.
    private val contexts = object : LinkedHashMap<String, NotificationContext>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NotificationContext>?): Boolean =
            size > 500
    }

    // When each key was first posted. Updates re-post the same key with a new postTime, so this keeps
    // the original time (putIfAbsent still refreshes its access order). Same bound as contexts.
    private val firstSeen = object : LinkedHashMap<String, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean = size > 500
    }

    // Debug-only overrides for our own test notifications, by key. Re-applied after every ranking
    // update so ranking data can't overwrite the simulated channel/conversation values.
    private val simulations = HashMap<String, DebugSimulation>()

    // A doubled listener connection delivers each callback twice; handle each event once.
    private val duplicates = DuplicateEventFilter()
    private fun isDuplicate(kind: DuplicateEventFilter.Kind, sbn: StatusBarNotification): Boolean {
        if (duplicates.isFirst(kind, sbn.key, sbn.postTime, System.currentTimeMillis())) return false
        Log.d(TAG, "duplicate ${kind.name.lowercase()} ignored pkg=${sbn.packageName} key=${sbn.key}")
        return true
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Android can bind this listener twice (seen on a Pixel 10 during an app update: two "binding" lines from
        // NotificationListeners.onPackagesChanged, then two connects), so every callback arrives twice. A second
        // connect while already connected means a duplicate: unbind once; onListenerDisconnected then asks for a
        // single rebind. Tried once per process so a repeat of the bug can't loop. DuplicateEventFilter covers the gap.
        if (_connected.value && !collapseAttempted) {
            collapseAttempted = true
            Log.w(TAG, "second onListenerConnected: collapsing duplicate listener connection")
            try { requestUnbind() } catch (e: RuntimeException) { Log.w(TAG, "requestUnbind failed", e) }
            return
        }
        Log.d(TAG, "onListenerConnected")
        _connected.value = true
        instance = this
        judgeActiveNotifications()
    }

    /**
     * After a (re)connect or the end of a pause, notifications already in the shade got no posted
     * callback (or were dropped while paused). Cache them and hand them to the judge now so "Let us
     * decide" has a verdict for them too. If the game is still loading, the judge waits for the first
     * config (TaptationApplication forwards it).
     */
    private fun judgeActiveNotifications() {
        if (skipWhileDisabled()) return
        val active = try { activeNotifications.orEmpty() } catch (e: RuntimeException) {
            Log.w(TAG, "activeNotifications failed", e); return
        }
        val ranking = currentRanking
        val countable = active.filter(::isCountable)
        countable.forEach { sbn ->
            firstSeen.putIfAbsent(sbn.key, sbn.postTime)
            val context = contextFor(sbn, ranking)
            contexts[sbn.key] = context
            TapDispatcher.handler.onNotificationPosted(context)
            judges().onPosted(context, currentConfig())
        }
        Log.d(TAG, "judging ${countable.size} active notifications")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        duplicates.clear()
        judges().clear()
        contexts.clear()
        firstSeen.clear()
        simulations.clear()
        TapDispatcher.handler.onListenerReset()
        _observations.value = emptyList()
        Log.d(TAG, "onListenerDisconnected")
        _connected.value = false
        if (instance === this) instance = null
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
        judges().clear()
        contexts.clear()
        firstSeen.clear()
        simulations.clear()
        TapDispatcher.handler.onListenerReset()
        _observations.value = emptyList()
        _connected.value = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (skipWhileDisabled()) return
        if (isDuplicate(DuplicateEventFilter.Kind.Posted, sbn)) return
        // update=true: same key already active, so the app replaced it (e.g. a new chat message).
        val update = contexts.containsKey(sbn.key)
        firstSeen.putIfAbsent(sbn.key, sbn.postTime)
        val context = contextFor(sbn, rankingMap)
        contexts[sbn.key] = context
        TapDispatcher.handler.onNotificationPosted(context)
        // "Let us decide": ask the AI judge now (off this thread) so the verdict is ready at tap time.
        // Only notifications that could ever count as a tap: no ongoing/summary/own alerts.
        if (isCountable(sbn)) judges().onPosted(context, currentConfig())
        if (isDebugBuild()) {
            _observations.value = (listOf(context) + _observations.value.filter { it.key != context.key }).take(10)
        }
        val signals = NotificationSignals.from(sbn, rankingMap)
        val simulated = if (context.packageName != sbn.packageName) " simulatedPkg=${context.packageName}" else ""
        Log.d(TAG, "posted pkg=${sbn.packageName}$simulated update=$update " +
            "${signals.describe(System.currentTimeMillis())} key=${sbn.key}")
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        if (skipWhileDisabled()) return
        // Conversation/channel information can arrive after the original posted callback.
        contexts.entries.forEach { entry ->
            val updated = withRanking(entry.value, rankingMap)
            if (updated != entry.value) {
                entry.setValue(updated)
                TapDispatcher.handler.onNotificationPosted(updated)
                // Channel/conversation are sent to the model: re-judge (only keys already judged).
                judges().onUpdated(updated)
            }
        }
        if (isDebugBuild()) {
            _observations.value = _observations.value.map { contexts[it.key] ?: it }
        }
    }

    // Only the three-argument overload is overridden. The base class's three-argument version
    // forwards to the one-argument one, so overriding both would handle each removal twice.
    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (skipWhileDisabled()) return
        if (isDuplicate(DuplicateEventFilter.Kind.Removed, sbn)) return
        val now = System.currentTimeMillis()
        val firstSeenAt = firstSeen.remove(sbn.key) ?: sbn.postTime
        val removed = withRanking(contexts.remove(sbn.key) ?: contextFor(sbn, rankingMap), rankingMap)
        val context = removed.copy(
            ageMillis = now - sbn.postTime,
            firstSeenAgeMillis = now - minOf(firstSeenAt, sbn.postTime),
        )
        simulations.remove(sbn.key)
        // Simulated package for our debug notifications, the real one otherwise.
        val pkg = context.packageName
        val flags = sbn.notification?.flags ?: 0
        val signals = NotificationSignals.from(sbn, rankingMap)
        Log.d(TAG, "removed pkg=$pkg reason=${TapDetection.reasonName(reason)}($reason) " +
            "firstSeenAge=${context.firstSeenAgeMillis}ms " +
            "${signals.describe(System.currentTimeMillis())} key=${sbn.key}")

        val kind = TapDetection.removalKind(
            reason = reason,
            flags = flags,
            channelId = sbn.notification?.channelId,
            packageName = sbn.packageName,
            ownPackageName = packageName,
            includeOwnPackage = isDebugBuild(),
        )
        try {
            when (kind) {
                TapDetection.RemovalKind.Tap -> {
                    TapLog.record(pkg)
                    // contextForTap holds on to the in-flight request, so this key's caches can be
                    // dropped right away (finally below): a re-post with the same key during the
                    // grace period then starts fresh instead of being wiped when the tap resolves.
                    judges().contextForTap(context).whenComplete { resolved, failure ->
                        try {
                            if (failure != null) Log.w(TAG, "AI tap resolution failed for $pkg", failure)
                            val ready = resolved ?: context.copy(
                                judged = Classification.Unknown,
                                judgingReason = "ai_request_failed",
                            )
                            TapDispatcher.dispatch(ready)
                        } catch (e: Exception) {
                            Log.e(TAG, "TapHandler threw for $pkg (Tap)", e)
                        }
                    }
                }
                // Swipe or clear-all: no game effect, only recorded for stats.
                TapDetection.RemovalKind.Dismiss ->
                    TapDispatcher.dispatchDismiss(judges().contextWithCurrentResult(context))
                TapDetection.RemovalKind.Ignored -> Unit
            }
        } catch (e: Exception) {
            // A bug in the handler must not take down the listener.
            Log.e(TAG, "TapHandler threw for $pkg ($kind)", e)
        } finally {
            finishRemoval(sbn.key)
        }
    }

    private fun finishRemoval(key: String) {
        judges().onRemoved(key)
        TapDispatcher.handler.onNotificationRemoved(key)
    }

    private fun contextFor(sbn: StatusBarNotification, rankingMap: RankingMap): NotificationContext {
        debugSimulationFor(sbn)?.let { simulations[sbn.key] = it }
        val notification = sbn.notification
        val extras = notification.extras
        val conversation = ConversationDetection.from(notification)
        return withRanking(NotificationContext(
            packageName = sbn.packageName,
            key = sbn.key,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = (extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras?.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            category = notification.category,
            media = MediaDetection.from(notification),
            sender = conversation.sender,
            isGroup = conversation.isGroup,
            actions = notification.actions.orEmpty()
                .mapNotNull { it?.title?.toString()?.takeIf(String::isNotBlank) },
            ageMillis = System.currentTimeMillis() - sbn.postTime,
            openOriginal = notification.contentIntent?.let { pending -> {
                // Only invoked by an explicit button tap on the caught UI.
                val options = if (Build.VERSION.SDK_INT >= 34) ActivityOptions.makeBasic().apply {
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }.toBundle() else null
                pending.send(this, 0, null, null, null, null, options)
            } },
        ), rankingMap)
    }

    /**
     * True (and caches cleared) only when a loaded config says the app is disabled. While the game is
     * still loading the config is null: keep going, GameRepository queues taps behind its load.
     */
    private fun skipWhileDisabled(): Boolean {
        val config = currentConfig()
        if (config == null || config.isActive(System.currentTimeMillis())) return false
        contexts.clear(); firstSeen.clear(); simulations.clear(); _observations.value = emptyList()
        judges().clear()
        TapDispatcher.handler.onListenerReset()
        return true
    }

    private fun withRanking(context: NotificationContext, rankingMap: RankingMap): NotificationContext {
        val ranking = Ranking()
        val key = context.key ?: return context
        val ranked = if (!rankingMap.getRanking(key, ranking)) context else context.copy(
            channelName = ranking.channel?.name?.toString(),
            isConversation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ranking.isConversation else null,
        )
        return simulations[key]?.applyTo(ranked) ?: ranked
    }

    /** Overrides from our own test notification; null for everything else and in release builds. */
    private fun debugSimulationFor(sbn: StatusBarNotification): DebugSimulation? {
        if (sbn.packageName != packageName || !isDebugBuild()) return null
        if (sbn.notification?.channelId != TapDetection.OWN_TEST_CHANNEL_ID) return null
        val extras = sbn.notification?.extras ?: return null
        return DebugSimulation.from(extras::getString)
    }

    private fun judges() = (application as TaptationApplication).judges

    private fun currentConfig(): GameConfig? = (application as TaptationApplication).game.state.value?.config

    private fun isCountable(sbn: StatusBarNotification): Boolean = TapDetection.isCountable(
        flags = sbn.notification?.flags ?: 0,
        channelId = sbn.notification?.channelId,
        packageName = sbn.packageName,
        ownPackageName = packageName,
        includeOwnPackage = isDebugBuild(),
    )

    private fun isDebugBuild(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    companion object {
        private const val TAG = "FakeNotifs"

        /** Asks Android to bind the listener again after it was stopped. Safe to call when access is off. */
        fun requestReconnect(context: android.content.Context) {
            if (!PermissionHelper.isNotificationAccessGranted(context)) return
            try {
                requestRebind(ComponentName(context, TapListenerService::class.java))
            } catch (e: RuntimeException) {
                Log.w(TAG, "requestRebind failed", e)
            }
        }

        /** The connected listener, so the app can ask it to re-judge the shade after a pause ends. */
        @Volatile private var instance: TapListenerService? = null

        /**
         * The game became active again (manual resume or a timed pause ending): notifications posted
         * while paused were never cached or judged, so scan the shade now. No-op when not connected.
         */
        fun onGameResumed() {
            val service = instance ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (instance === service) service.judgeActiveNotifications()
            }
        }

        private val _connected = MutableStateFlow(false)
        /** Duplicate-connection collapse already tried in this process (see onListenerConnected). */
        @Volatile private var collapseAttempted = false

        /** True while the system has this listener bound. For the debug screen. */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()
        private val _observations = MutableStateFlow<List<NotificationContext>>(emptyList())
        /** Debug-only, in-memory samples. Never persist or send these outside the app. */
        val observations: StateFlow<List<NotificationContext>> = _observations.asStateFlow()
    }
}
