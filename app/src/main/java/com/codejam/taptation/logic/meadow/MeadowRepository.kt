package com.codejam.taptation.logic.meadow

import android.content.Context
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.MonitoringStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Additive meadow backend. Observes the existing game; never changes health or catch decisions. */
class MeadowRepository(
    context: Context,
    private val game: StateFlow<GameState?>,
    private val monitoring: StateFlow<MonitoringStatus?>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    preferencesName: String = "meadow_world_v1",
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _state = MutableStateFlow<MeadowState?>(null)
    val state = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val catchScenes = LinkedHashMap<String,MeadowCameo>()

    /** One eligible, cosmetic scene per catch; overlay and foreground use the same selection. */
    @Synchronized
    fun sceneFor(catchId:String):MeadowCameo = catchScenes.getOrPut(catchId) {
        if(catchScenes.size>=32)catchScenes.remove(catchScenes.keys.first())
        val pool=(_state.value?:MeadowState()).catchCameos
        pool[Math.floorMod(catchId.hashCode(),pool.size)]
    }

    init {
        scope.launch(Dispatchers.IO) {
            mutex.withLock { load() }
            combine(game, monitoring) { state, status -> state to status }.collect { (state, status) ->
                if (state != null && status != null) update(state, status.isWorking)
            }
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(MeadowState.MINUTE)
                refresh()
            }
        }
    }

    fun refresh() { scope.launch(Dispatchers.IO) {
        val snapshot = game.value ?: return@launch
        val status = monitoring.value ?: return@launch
        update(snapshot, status.isWorking)
    } }

    fun retry() { scope.launch(Dispatchers.IO) {
        mutex.withLock { if (_state.value == null) load() }
        refresh()
    } }

    private fun load() {
        try {
            _state.value = MeadowState(
                healthyMillis = preferences.getLong("healthy", 0).coerceAtLeast(0),
                checkpointMillis = preferences.getLong("checkpoint", 0).coerceAtLeast(0),
                checkpointFull = preferences.getBoolean("full", false),
                checkpointMonitoring = preferences.getBoolean("monitoring", false),
                discoveries = preferences.getStringSet("discoveries", emptySet()).orEmpty()
                    .mapNotNull { name -> MeadowDiscovery.entries.find { it.name == name } }.toSet())
            _error.value = null
        } catch (_: Exception) { _error.value = "Your meadow could not be loaded. Try again." }
    }

    fun discover(discovery: MeadowDiscovery) { scope.launch(Dispatchers.IO) {
        mutex.withLock {
            val previous = _state.value ?: return@withLock
            if (discovery !in previous.discoveries) save(previous.copy(discoveries = previous.discoveries + discovery))
        }
    } }

    private suspend fun update(game: GameState, active: Boolean) = mutex.withLock {
        _state.value?.let { save(MeadowProgression.advance(it, game, active, clock())) }
    }

    private fun save(next: MeadowState) {
        if (next == _state.value) return
        try {
            check(preferences.edit().putLong("healthy", next.healthyMillis).putLong("checkpoint", next.checkpointMillis)
                .putBoolean("full", next.checkpointFull).putBoolean("monitoring", next.checkpointMonitoring)
                .putStringSet("discoveries", next.discoveries.map { it.name }.toSet()).commit())
            _state.value = next
            _error.value = null
        } catch (_: Exception) { _error.value = "Your meadow could not be saved. Your previous discoveries are safe." }
    }
}
