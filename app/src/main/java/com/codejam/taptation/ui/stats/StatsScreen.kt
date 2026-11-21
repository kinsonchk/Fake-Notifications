package com.codejam.taptation.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.stats.DailyCatches
import com.codejam.taptation.stats.StatsSummary
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen

/** Real SQLite history, including zero-catch days; no invented home-visual metric. */
@Composable
fun StatsScreen(contentPadding: PaddingValues) {
    val stats = (LocalContext.current.applicationContext as TaptationApplication).stats
    val changes by stats.changes.collectAsStateWithLifecycle()
    var month by remember { mutableStateOf(false) }
    var includeImportant by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(changes, month, includeImportant) {
        try {
            summary = if (month) stats.monthly(includeImportant) else stats.weekly(includeImportant)
            error = null
        } catch (failure: Exception) { error = failure.localizedMessage ?: "Could not load history" }
    }
    TabScreen(contentPadding, "Your activity") {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !month, onClick = { month = false }, label = { Text("Last 7 days") })
                FilterChip(selected = month, onClick = { month = true }, label = { Text("Last 30 days") })
                FilterChip(selected = includeImportant, onClick = { includeImportant = !includeImportant },
                    label = { Text("Include important / unsure") })
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val current = summary
        if (current == null) item { Text("Loading history…") }
        else {
            item {
                SectionCard("At a glance") {
                    LabelValue("Taps / dismissals", "${current.taps} / ${current.dismissals}")
                    LabelValue("Tap-to-dismiss ratio", current.tapToDismissPercent.percent())
                    LabelValue("Catches per tap", current.caughtPerTapPercent.percent())
                    LabelValue("Catches", current.caught.toString())
                    LabelValue("Average catches / day", "%.2f".format(current.averageCatchesPerDay))
                    Text("Tap-to-dismiss = taps ÷ dismissals. Catches per tap = catches ÷ taps.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard("Tap vs. dismiss by category") {
                    if (current.categories.isEmpty()) Text("No activity yet. Your first detected tap or dismissal will appear here.")
                    current.categories.forEach { category ->
                        LabelValue(category.category, "${category.taps} taps / ${category.dismissals} dismissals")
                        Text("Tap/dismiss: ${category.tapToDismissPercent.percent()} · Catches/tap: ${category.caughtPerTapPercent.percent()}",
                            style = MaterialTheme.typography.bodySmall)
                        val total = category.taps + category.dismissals
                        if (total > 0) LinearProgressIndicator(progress = { category.taps.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "${category.category}: ${category.taps} tapped, ${category.dismissals} dismissed"
                            })
                    }
                    Text("Filled portion: tapped. Remaining portion: dismissed. Older records without a category appear as unknown.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                SectionCard("Catches over time") {
                    CatchBars(current.dailyCatches)
                    Text("Daily average includes days with zero catches.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("Based on detected notification actions. ${if (includeImportant) "Includes important and unsure notifications." else "Bait only; important and unsure notifications are excluded."}",
                style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CatchBars(days: List<DailyCatches>) {
    val color = MaterialTheme.colorScheme.primary
    val max = (days.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Text("Highest day: ${days.maxOfOrNull { it.count } ?: 0} catches", style = MaterialTheme.typography.bodySmall)
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics {
        contentDescription = days.joinToString { "${it.date}: ${it.count} catches" }
    }) {
        if (days.isNotEmpty()) {
            val width = size.width / days.size
            days.forEachIndexed { index, day ->
                val height = size.height * day.count / max
                if (day.count > 0) drawRoundRect(color,
                    topLeft = Offset(index * width + width * 0.15f, size.height - height),
                    size = Size(width * 0.7f, height), cornerRadius = CornerRadius(3.dp.toPx()))
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(days.firstOrNull()?.date.orEmpty(), style = MaterialTheme.typography.bodySmall)
        Text(days.lastOrNull()?.date.orEmpty(), style = MaterialTheme.typography.bodySmall)
    }
}

private fun Double?.percent(): String = this?.let { "%.1f%%".format(it) } ?: "—"
