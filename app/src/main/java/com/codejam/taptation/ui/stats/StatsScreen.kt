package com.codejam.taptation.ui.stats

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.TapRecordRow
import com.codejam.taptation.ui.common.formatClock
import com.codejam.taptation.ui.common.formatDuration
import com.codejam.taptation.ui.common.rememberGameRepository
import com.codejam.taptation.ui.common.rememberNow

private const val RECENT_TAPS = 10
private const val RECENT_HEALTH = 10

/** "What have I done?" Everything here comes from the saved GameState. */
@Composable
fun StatsScreen(contentPadding: PaddingValues) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val now = rememberNow(repository)

    TabScreen(contentPadding, "Stats") {
        val state = snapshot
        if (state == null) {
            item { Text("Loading saved game…") }
            return@TabScreen
        }
        item {
            SectionCard("Totals") {
                val taps = state.tapsPerApp.values.sum()
                LabelValue("Notification taps", taps.toString())
                LabelValue("Catches", state.totalCatches.toString())
                LabelValue("Bait taps in history", state.taps.count { it.classification == Classification.Bait }.toString())
                LabelValue("Important taps in history", state.taps.count { it.classification == Classification.Important }.toString())
                LabelValue("Since last catch", state.timeSinceLastCatchMillis(now)?.let(::formatDuration) ?: "never caught")
            }
        }
        item {
            SectionCard("Taps per app") {
                if (state.tapsPerApp.isEmpty()) Text("No taps yet.")
                state.tapsPerApp.entries.sortedByDescending { it.value }.forEach { (pkg, count) ->
                    LabelValue(pkg, count.toString())
                }
            }
        }
        item { Text("Recent taps", style = MaterialTheme.typography.titleMedium) }
        val recent = state.taps.takeLast(RECENT_TAPS).asReversed()
        if (recent.isEmpty()) item { Text("No taps yet.") }
        items(recent) { TapRecordRow(it) }
        item {
            SectionCard("Health history") {
                val history = state.healthHistory.takeLast(RECENT_HEALTH).asReversed()
                if (history.isEmpty()) Text("No changes yet.")
                history.forEach {
                    val level = it.level?.let { level -> ", ${level.name}" }.orEmpty()
                    Text("${formatClock(it.timestampMillis)}  ${it.before} → ${it.after}  (${it.reason}$level)",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
