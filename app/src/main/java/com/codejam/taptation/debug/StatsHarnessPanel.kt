package com.codejam.taptation.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.stats.FakeStatsHistory
import com.codejam.taptation.stats.StatsSummary
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.formatDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Debug view of StatsRepository: week/month summary, fake history, clear.
 * TODO(Person 3): the real stats screen calls only weekly()/monthly() the same way.
 */
@Composable
fun StatsHarnessPanel() {
    val context = LocalContext.current
    val stats = remember(context) { (context.applicationContext as TaptationApplication).stats }
    val scope = rememberCoroutineScope()
    val changes by stats.changes.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(false) }
    var includeImportant by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    var rows by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Debug buttons: a database error is shown, never thrown out of the click's coroutine.
    fun launchReporting(action: suspend () -> Unit) = scope.launch {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) { error = failure.localizedMessage ?: "Stats action failed" }
    }

    LaunchedEffect(changes, month, includeImportant) {
        try {
            summary = if (month) stats.monthly(includeImportant) else stats.weekly(includeImportant)
            rows = stats.count()
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) { error = failure.localizedMessage ?: "Could not load history" }
    }

    SectionCard("Stats harness", "StatsRepository over SQLite: one row per tap or dismissal ($rows rows).") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !month, onClick = { month = false }, label = { Text("Week") })
            FilterChip(selected = month, onClick = { month = true }, label = { Text("Month") })
            FilterChip(
                selected = includeImportant,
                onClick = { includeImportant = !includeImportant },
                label = { Text("Include important") },
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        summary?.let { s ->
            LabelValue("Taps / dismissals", "${s.taps} / ${s.dismissals}")
            LabelValue("Tap rate", s.tapRatePercent?.let { "$it%" } ?: "no data")
            LabelValue("Caught", s.caught.toString())
            LabelValue("Median time to tap", s.medianTapMillis.duration())
            LabelValue("Median time to dismiss", s.medianDismissMillis.duration())
            LabelValue("Avg tap / dismiss (≤ 1 h)",
                "${s.averageTapMillis.duration()} / ${s.averageDismissMillis.duration()}")
        }
        OutlinedButton(
            onClick = {
                launchReporting {
                    stats.insertForDebug(FakeStatsHistory.generate(System.currentTimeMillis(), 30, Random.Default))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Generate 30 days of fake history") }
        TextButton(onClick = { launchReporting { stats.clearForDebug() } }) { Text("Clear stats") }
    }
}

private fun Long?.duration(): String = this?.let(::formatDuration) ?: "–"
