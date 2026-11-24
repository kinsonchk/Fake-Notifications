package com.codejam.taptation.logic

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.codejam.taptation.stats.StatsEvent
import com.codejam.taptation.stats.StatsEventType
import com.codejam.taptation.stats.StatsRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide queue: no disk I/O on notification callbacks and no competing state writes. */
class GameRepository(context: Context, private val stats: StatsRepository? = null,
    private val onCaught: (PendingCatch, (() -> Unit)?) -> Unit = { _, _ -> },
) : ResultTapHandler {
    private val packageManager = context.applicationContext.packageManager
    private val appCategories = mutableMapOf<String, Int?>()
    private val notificationContexts = linkedMapOf<String, NotificationContext>()
    private val worker = Executors.newSingleThreadScheduledExecutor()
    // Worker-confined: the pending self-refresh for the next recovery step (see scheduleNextRefresh).
    private var scheduledRefresh: ScheduledFuture<*>? = null
    private val store = PreferencesGameStore(context.applicationContext)
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** True in debuggable builds, where debug overrides (catch chance, recovery interval) apply. */
    val allowsDebugOverrides: Boolean get() = debug
    private val disableGate = DisableGate(android.os.SystemClock::elapsedRealtime)
    private lateinit var engine: GameEngine
    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _lastResult = MutableStateFlow<TapResult?>(null)
    val lastResult: StateFlow<TapResult?> = _lastResult.asStateFlow()

    init {
        worker.execute {
            try {
                engine = GameEngine(store, allowDebugOverrides = debug)
                _state.value = engine.refresh()
                scheduleNextRefresh()
            } catch (error: Exception) {
                report(error)
            }
        }
    }

    override fun onTap(packageName: String) { processTap(NotificationContext(packageName)) }
    override fun onTap(context: NotificationContext) { processTap(context) }

    override fun onNotificationPosted(context: NotificationContext) {
        worker.execute {
            if (::engine.isInitialized && !engine.state.config.isActive(System.currentTimeMillis())) return@execute
            context.key?.let { notificationContexts[it] = context }
            if (notificationContexts.size > 500) notificationContexts.remove(notificationContexts.keys.first())
        }
    }

    override fun onNotificationRemoved(key: String) { worker.execute { notificationContexts.remove(key) } }
    override fun onListenerReset() { worker.execute { notificationContexts.clear() } }

    /** Async Android adapter. Multiple candidates are ambiguous and deliberately fail safe. */
    override fun onTap(packageName: String, ageMillis: Long): CompletableFuture<TapResult> = submit {
        val candidates = notificationContexts.values.filter { it.packageName == packageName }
        val context = candidates.singleOrNull()?.also { it.key?.let(notificationContexts::remove) }
            ?: NotificationContext(packageName)
        recordResult(onTap(context.copy(ageMillis = ageMillis)), context.copy(ageMillis = ageMillis))
    }

    fun processTap(context: NotificationContext): CompletableFuture<TapResult> = submit {
        context.key?.let(notificationContexts::remove)
        recordResult(onTap(withAppCategory(context)), context)
    }

    /**
     * Swipe or clear-all: no game effect. Classified with the same rules (no roll) and recorded
     * for stats, so dismissals can be split into bait / important like taps.
     */
    override fun onDismiss(context: NotificationContext) {
        if (stats == null) return
        submit {
            refresh()
            if (!state.config.isActive(System.currentTimeMillis())) return@submit
            val assessment = NotificationClassifier.assessTap(withAppCategory(context), state.config)
            stats.submit(StatsEvent(System.currentTimeMillis(), context.packageName, StatsEventType.DISMISS,
                context.ageMillis, assessment.classification, category = context.category))
        }
    }

    /** Runs on the worker: resolves ApplicationInfo.category once per package. */
    private fun withAppCategory(context: NotificationContext): NotificationContext {
        val category = context.appCategory ?: appCategories.getOrPut(context.packageName) {
            try {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(context.packageName, 0).category
                    .takeUnless { it == ApplicationInfo.CATEGORY_UNDEFINED }
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }
        return context.copy(appCategory = category)
    }

    private fun recordResult(result: TapResult, context: NotificationContext): TapResult = result.also {
        _lastResult.value = it
        if (it.reason != "disabled") stats?.submit(StatsEvent(System.currentTimeMillis(), context.packageName,
            StatsEventType.TAP, context.ageMillis, it.classification,
            caught = it.outcome == TapOutcome.Caught, category = context.category))
        if (it.outcome == TapOutcome.Caught) it.state.pendingCatch?.let { pending ->
            onCaught(pending, context.openOriginal)
        }
        Log.d("FakeNotifs", "tap pkg=${context.packageName} outcome=${it.outcome} " +
            "reason=${it.reason} rule=${it.rule} health=${it.state.health}")
    }

    /** Snapshot read; observe state and call refresh() when the UI becomes active. */
    fun getHealth(): Int? = state.value?.health
    fun isWhitelisted(packageName: String): Boolean =
        state.value?.config?.ruleFor(packageName) == AppRule.AlwaysImportant
    fun refresh(): CompletableFuture<GameState> = submit { refresh() }
    fun applyCatch(level: CatchLevel): CompletableFuture<GameState> = submit {
        val before = state.pendingCatch?.id
        applyCatch(level).also { next ->
            next.pendingCatch?.takeIf { it.id != before }?.let { onCaught(it, null) }
        }
    }
    fun reset(): CompletableFuture<GameState> = submit { reset() }

    /**
     * Transform runs on the queue so simultaneous settings changes cannot lose updates. Screens should
     * use the typed actions below (setDifficulty, setFreshness…, setCriteria…) so the rules stay here;
     * this open form is for debug panels and the actions themselves.
     */
    fun updateConfig(change: (GameConfig) -> GameConfig): CompletableFuture<GameState> = submit {
        val changed = change(state.config)
        require(changed.active == state.config.active && changed.disabledUntilMillis == state.config.disabledUntilMillis) {
            "Use the confirmed disable flow to change activation"
        }
        updateConfig(if (debug) changed else changed.copy(debugCatchProbability = null, debugRecoveryIntervalMinutes = null))
    }

    // Typed settings actions: screens call these instead of editing GameConfig, so every game rule
    // (allowed values, defaults, what a choice also resets) lives here.

    /** Picking a public difficulty also drops any debug catch-chance override. */
    fun setDifficulty(difficulty: Difficulty) = updateConfig { it.copy(difficulty = difficulty, debugCatchProbability = null) }

    /** Turning "ignore older notifications" back on restores the default window. */
    fun setFreshnessEnabled(enabled: Boolean) =
        updateConfig { it.copy(freshnessSeconds = if (enabled) GameConfig.DEFAULT_FRESHNESS_SECONDS else 0) }

    fun setFreshnessMinutes(minutes: Int): CompletableFuture<GameState> {
        require(minutes in GameConfig.FRESHNESS_CHOICES_MINUTES) { "Unsupported time limit: $minutes min" }
        return updateConfig { it.copy(freshnessSeconds = minutes * 60) }
    }

    fun setFreshnessFromFirstSeen(fromFirstSeen: Boolean) = updateConfig { it.copy(freshnessFromFirstSeen = fromFirstSeen) }

    /** User answers for "Let us decide"; cut to [GameConfig.MAX_CRITERIA_LENGTH]. Null leaves one unchanged. */
    fun setCriteria(essential: String? = null, nonEssential: String? = null) = updateConfig {
        it.copy(
            essentialCriteria = essential?.take(GameConfig.MAX_CRITERIA_LENGTH) ?: it.essentialCriteria,
            nonEssentialCriteria = nonEssential?.take(GameConfig.MAX_CRITERIA_LENGTH) ?: it.nonEssentialCriteria,
        )
    }

    fun setStrictness(strictness: Strictness) = updateConfig { it.copy(strictness = strictness) }

    /** Removes an app's rule, back to "Let us decide" (also for apps that are no longer installed). */
    fun clearAppRule(packageName: String) = setAppRule(packageName, AppRule.LetUsDecide)

    /** "This should be important" after a catch: the whole app becomes Always important. */
    fun markAppImportant(packageName: String) = setAppRule(packageName, AppRule.AlwaysImportant)

    /** The recovery interval in force (the debug override only applies in debuggable builds). */
    fun recoveryIntervalMinutes(config: GameConfig): Int = config.effectiveRecoveryIntervalMinutes(debug)

    /** Time left before a disable [challenge] can be confirmed, on the gate's own clock. */
    fun disableWaitRemainingMillis(challenge: DisableGate.Challenge): Long =
        (challenge.readyAtMillis - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)

    fun beginDisable(): CompletableFuture<DisableGate.Challenge> = submit { disableGate.begin() }
    fun cancelDisable(): CompletableFuture<Unit> = submit { disableGate.cancel() }
    fun confirmDisable(id: String, minutes: Int?): CompletableFuture<GameState> = submit {
        require(minutes in DisableGate.DURATION_CHOICES_MINUTES)
        disableGate.confirm(id)
        setActive(false, minutes?.let { System.currentTimeMillis() + it * 60_000L })
    }
    fun enable(): CompletableFuture<GameState> = submit { disableGate.cancel(); setActive(true) }
    fun settleCatchChoice(id: String, continueOriginalApp: Boolean): CompletableFuture<GameState> =
        submit { settleCatchChoice(id, continueOriginalApp) }
    fun dismissCatch(id: String): CompletableFuture<GameState> = submit { dismissCatch(id) }
    fun setDebugCatchProbability(value: Double?): CompletableFuture<GameState> = submit {
        check(debug) { "Debug overrides are disabled in release builds" }
        updateConfig(state.config.copy(debugCatchProbability = value))
    }
    fun setDebugRecoveryInterval(minutes: Int?): CompletableFuture<GameState> = submit {
        check(debug) { "Debug overrides are disabled in release builds" }
        this.setDebugRecoveryInterval(minutes)
    }

    /**
     * Debug only: catch lockout in seconds (1–3600); null restores the default. Applies to the next
     * catch; a lockout already running keeps its deadline.
     */
    fun setLockoutForDebug(seconds: Int?): CompletableFuture<GameState> = submit {
        check(debug) { "Debug controls are disabled in release builds" }
        updateConfig(state.config.copy(lockoutSeconds = (seconds ?: GameConfig().lockoutSeconds).coerceIn(1, 3600)))
    }

    /**
     * Debug only: health lost per catch at the selected level (1–100); null restores that level's
     * default. Applies to tap catches and forced catches alike; the continue penalty is unchanged.
     */
    fun setCatchDamageForDebug(damage: Int?): CompletableFuture<GameState> = submit {
        check(debug) { "Debug controls are disabled in release builds" }
        val config = state.config
        val value = (damage ?: GameConfig().damage(config.level)).coerceIn(1, 100)
        updateConfig(when (config.level) {
            CatchLevel.Mild -> config.copy(mildDamage = value)
            CatchLevel.Medium -> config.copy(mediumDamage = value)
            CatchLevel.Savage -> config.copy(savageDamage = value)
        })
    }

    /** Kept for callers of the old whitelist API: on = Always important, off = Let us decide. */
    fun setWhitelisted(packageName: String, enabled: Boolean): CompletableFuture<GameState> =
        setAppRule(packageName, if (enabled) AppRule.AlwaysImportant else AppRule.LetUsDecide)

    /** Saves the user's choice for one app; LetUsDecide is the default, so it removes the entry. */
    fun setAppRule(packageName: String, rule: AppRule): CompletableFuture<GameState> {
        require(packageName.isNotBlank())
        return updateConfig {
            it.copy(appRules = if (rule == AppRule.LetUsDecide) it.appRules - packageName
            else it.appRules + (packageName to rule))
        }
    }

    fun setHealthForDebug(health: Int): CompletableFuture<GameState> = submit {
        check(debug) { "Debug controls are disabled in release builds" }
        setHealthForDebug(health)
    }

    fun advanceTimeForDebug(minutes: Long): CompletableFuture<GameState> = submit {
        check(debug) { "Debug controls are disabled in release builds" }
        advanceTimeForDebug(minutes * 60_000)
    }

    fun resetDemoDataForDebug(): CompletableFuture<GameState> = submit {
        check(debug) { "Debug controls are disabled in release builds" }
        resetDemoDataForDebug()
    }

    private fun <T> submit(action: GameEngine.() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        worker.execute {
            try {
                check(::engine.isInitialized) { "Game store unavailable; see initialization error" }
                val result = engine.action()
                _state.value = engine.state
                _error.value = null
                scheduleNextRefresh()
                future.complete(result)
            } catch (error: Exception) {
                report(error)
                future.completeExceptionally(error)
            }
        }
        return future
    }

    /**
     * Keeps [state] current on its own: recovery is computed lazily by refresh(), so without this
     * health only moved when a screen happened to call refresh(). Re-armed after every operation,
     * so it always targets the latest state. Runs on the worker; late after deep sleep, which the
     * screens' on-resume refresh covers.
     */
    private fun scheduleNextRefresh() {
        scheduledRefresh?.cancel(false)
        val at = engine.nextScheduledChangeMillis() ?: return
        val delay = (at - System.currentTimeMillis()).coerceAtLeast(0) + REFRESH_SLACK_MILLIS
        scheduledRefresh = worker.schedule({ submit { refresh() } }, delay, TimeUnit.MILLISECONDS)
    }

    private fun report(error: Exception) {
        _error.value = error.message ?: "Game operation failed"
        Log.e("FakeNotifs", "Game operation failed", error)
    }

    private companion object {
        /** Lands the self-refresh just after the recovery step, not a hair before it. */
        const val REFRESH_SLACK_MILLIS = 50L
    }
}


