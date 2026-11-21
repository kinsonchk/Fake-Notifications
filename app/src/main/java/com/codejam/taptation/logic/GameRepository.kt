package com.codejam.taptation.logic

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide queue: no disk I/O on notification callbacks and no competing state writes. */
class GameRepository(context: Context) : ResultTapHandler {
    private val packageManager = context.applicationContext.packageManager
    private val appCategories = mutableMapOf<String, Int?>()
    private val notificationContexts = linkedMapOf<String, NotificationContext>()
    private val worker = Executors.newSingleThreadExecutor()
    private val store = PreferencesGameStore(context.applicationContext)
    private val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
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
                engine = GameEngine(store)
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
        recordResult(onTap(context.copy(ageMillis = ageMillis)), packageName)
    }

    fun processTap(context: NotificationContext): CompletableFuture<TapResult> = submit {
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
        context.key?.let(notificationContexts::remove)
        recordResult(onTap(context.copy(appCategory = category)), context.packageName)
    }

    private fun recordResult(result: TapResult, packageName: String): TapResult = result.also {
        _lastResult.value = it
        Log.d("FakeNotifs", "tap pkg=$packageName outcome=${it.outcome} " +
            "reason=${it.reason} health=${it.state.health}")
    }

    /** Snapshot read; observe state and call refresh() when the UI becomes active. */
    fun getHealth(): Int? = state.value?.health
    fun isWhitelisted(packageName: String): Boolean =
        state.value?.config?.whitelistedPackages?.contains(packageName) == true
    fun refresh(): CompletableFuture<GameState> = submit { refresh() }
    fun applyCatch(level: CatchLevel): CompletableFuture<GameState> = submit { applyCatch(level) }
    fun reset(): CompletableFuture<GameState> = submit { reset() }

    /** Transform runs on the queue so simultaneous settings changes cannot lose updates. */
    fun updateConfig(change: (GameConfig) -> GameConfig): CompletableFuture<GameState> = submit {
        updateConfig(change(state.config))
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
}
