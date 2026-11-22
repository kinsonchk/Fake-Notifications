package com.codejam.taptation.logic

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.codejam.taptation.stats.StatsEvent
import com.codejam.taptation.stats.StatsEventType
import com.codejam.taptation.stats.StatsRepository
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
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
    private val worker = Executors.newSingleThreadExecutor()
    private val store = PreferencesGameStore(context.applicationContext)
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val disableGate = DisableGate(android.os.SystemClock::elapsedRealtime)
    private lateinit var engine: GameEngine
    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _lastResult = MutableStateFlow<TapResult?>(null)
    val lastResult: StateFlow<TapResult?> = _lastResult.asStateFlow()

    private val ownPackage = context.packageName
    private val observedPrefs = context.applicationContext.getSharedPreferences("observed_types", Context.MODE_PRIVATE)
    private val _observed = MutableStateFlow(ObservedTypes.decode(observedPrefs.getString(OBSERVED_KEY, null)))
    /** Notification types seen per app (names only, never titles or text); drives the rule picker. */
    val observed: StateFlow<ObservedTypes> = _observed.asStateFlow()
    private var observedSinceSave = 0
    private val catchChannels = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Why and how much the pending catch cost, from the saved tap record. */
    fun catchDetails(pending: PendingCatch): CatchDetails? =
        state.value?.let { CatchDetails.from(it, pending, catchChannels[pending.id]) }

    /** Applies a friendly rule choice to the saved settings. */
    fun applyRule(target: RuleTarget, behavior: RuleBehavior): CompletableFuture<GameState> =
        updateConfig { RuleChoices.apply(it, target, behavior) }

    private fun noteObserved(context: NotificationContext) {
        if (context.packageName == ownPackage) return
        val before = _observed.value
        val isNew = context.channelName?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { name -> before.typesFor(context.packageName, System.currentTimeMillis(), ObservedTypes.KEEP_DAYS).none { it.channel == name } }
            ?: !before.hasSeen(context.packageName)
        _observed.value = before.record(context.packageName, context.channelName, System.currentTimeMillis())
        if (isNew || ++observedSinceSave >= 10) {
            observedSinceSave = 0
            observedPrefs.edit().putString(OBSERVED_KEY, _observed.value.encode()).apply()
        }
    }

    init {
        worker.execute {
            try {
                engine = GameEngine(store, allowDebugOverrides = debug)
                _state.value = engine.refresh()
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
            noteObserved(context)
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
            val assessment = NotificationClassifier.assess(withAppCategory(context), state.config)
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
            if (catchChannels.size >= 16) catchChannels.clear()
            context.channelName?.takeIf { name -> name.isNotBlank() }?.let { name -> catchChannels[pending.id] = name }
            onCaught(pending, context.openOriginal)
        }
        Log.d("FakeNotifs", "tap pkg=${context.packageName} outcome=${it.outcome} " +
            "reason=${it.reason} rule=${it.rule} health=${it.state.health}")
    }

    /** Snapshot read; observe state and call refresh() when the UI becomes active. */
    fun getHealth(): Int? = state.value?.health
    fun isWhitelisted(packageName: String): Boolean =
        state.value?.config?.whitelistedPackages?.contains(packageName) == true
    fun refresh(): CompletableFuture<GameState> = submit { refresh() }
    fun applyCatch(level: CatchLevel): CompletableFuture<GameState> = submit {
        val before = state.pendingCatch?.id
        applyCatch(level).also { next ->
            next.pendingCatch?.takeIf { it.id != before }?.let { onCaught(it, null) }
        }
    }
    fun reset(): CompletableFuture<GameState> = submit { reset() }

    /** Transform runs on the queue so simultaneous settings changes cannot lose updates. */
    fun updateConfig(change: (GameConfig) -> GameConfig): CompletableFuture<GameState> = submit {
        val changed = change(state.config)
        require(changed.active == state.config.active && changed.disabledUntilMillis == state.config.disabledUntilMillis) {
            "Use the confirmed disable flow to change activation"
        }
        updateConfig(if (debug) changed else changed.copy(debugCatchProbability = null))
    }

    fun beginDisable(): CompletableFuture<DisableGate.Challenge> = submit { disableGate.begin() }
    fun cancelDisable(): CompletableFuture<Unit> = submit { disableGate.cancel() }
    fun confirmDisable(id: String, minutes: Int?): CompletableFuture<GameState> = submit {
        require(minutes == null || minutes in setOf(15, 60))
        disableGate.confirm(id)
        setActive(false, minutes?.let { System.currentTimeMillis() + it * 60_000L })
    }
    fun enable(): CompletableFuture<GameState> = submit { disableGate.cancel(); setActive(true) }
    fun dismissCatch(id: String): CompletableFuture<GameState> = submit { dismissCatch(id) }
    fun setDebugCatchProbability(value: Double?): CompletableFuture<GameState> = submit {
        check(debug) { "Debug overrides are disabled in release builds" }
        updateConfig(state.config.copy(debugCatchProbability = value))
    }

    fun setWhitelisted(packageName: String, enabled: Boolean): CompletableFuture<GameState> {
        require(packageName.isNotBlank())
        return updateConfig { it.copy(whitelistedPackages = if (enabled)
            it.whitelistedPackages + packageName else it.whitelistedPackages - packageName) }
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
                future.complete(result)
            } catch (error: Exception) {
                report(error)
                future.completeExceptionally(error)
            }
        }
        return future
    }

    private fun report(error: Exception) {
        _error.value = error.message ?: "Game operation failed"
        Log.e("FakeNotifs", "Game operation failed", error)
    }

    private companion object { const val OBSERVED_KEY = "v1" }
}


