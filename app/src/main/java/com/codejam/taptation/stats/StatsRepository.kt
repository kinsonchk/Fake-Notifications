package com.codejam.taptation.stats

import android.util.Log
import com.codejam.taptation.logic.Classification
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single home for stats (docs/decision-stats-handler.md). Nothing else touches the database:
 * the game logic records events here, the stats UI only calls [weekly] / [monthly] / [summary].
 *
 * All store access runs on one background thread ([dispatcher]), never on the main thread.
 * Built once in TaptationApplication and shared.
 */
class StatsRepository(
    private val store: StatsStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, error ->
        Log.e("FakeNotifs", "Stats write failed", error)
    })

    private val _changes = MutableStateFlow(0L)
    /** Increments after every write; collect it to re-query a summary. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    suspend fun recordTap(pkg: String, ageMillis: Long?, classification: Classification, wasCaught: Boolean) =
        record(StatsEvent(clock(), pkg, StatsEventType.TAP, ageMillis, classification, wasCaught))

    suspend fun recordDismiss(pkg: String, ageMillis: Long?, classification: Classification) =
        record(StatsEvent(clock(), pkg, StatsEventType.DISMISS, ageMillis, classification))

    /** Fire-and-forget write for callers that aren't coroutines (the game worker thread). */
    fun submit(event: StatsEvent) {
        scope.launch { record(event) }
    }

    /** Last 7 days up to now. */
    suspend fun weekly(includeImportant: Boolean = false): StatsSummary =
        lastDays(WEEK_DAYS, includeImportant)

    /** Last 30 days up to now. */
    suspend fun monthly(includeImportant: Boolean = false): StatsSummary =
        lastDays(MONTH_DAYS, includeImportant)

    suspend fun summary(fromMillis: Long, toMillis: Long, includeImportant: Boolean = false): StatsSummary =
        withContext(dispatcher) {
            StatsCalculator.summarize(store.eventsBetween(fromMillis, toMillis), fromMillis, toMillis, includeImportant)
        }

    suspend fun count(): Int = withContext(dispatcher) { store.count() }

    /** Debug: write generated history through the same store (see [FakeStatsHistory]). */
    suspend fun insertForDebug(events: List<StatsEvent>) = withContext(dispatcher) {
        store.insertAll(events)
        _changes.value++
    }

    suspend fun clearForDebug() = withContext(dispatcher) {
        store.clear()
        _changes.value++
    }

    private suspend fun lastDays(days: Int, includeImportant: Boolean): StatsSummary {
        val now = clock()
        // +1 so an event recorded in this same millisecond is included (the range end is exclusive).
        return summary(now - days * DAY_MILLIS, now + 1, includeImportant)
    }

    private suspend fun record(event: StatsEvent) = withContext(dispatcher) {
        store.insert(event)
        _changes.value++
    }

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30
    }
}
