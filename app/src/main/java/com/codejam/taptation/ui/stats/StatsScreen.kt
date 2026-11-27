package com.codejam.taptation.ui.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.stats.DailyCatches
import com.codejam.taptation.stats.StatsSummary
import com.codejam.taptation.stats.StatsText
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.SegmentedChoice
import com.codejam.taptation.ui.common.TabScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.selected
import com.codejam.taptation.R
import com.codejam.taptation.ui.common.LineIcon
import com.codejam.taptation.ui.common.ListGroup
import com.codejam.taptation.ui.common.RowDivider
import com.codejam.taptation.ui.common.SectionLabel
import com.codejam.taptation.ui.theme.ControlShape
import com.codejam.taptation.ui.theme.MetaText
import com.codejam.taptation.ui.theme.Taptation

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val shortDay = DateTimeFormatter.ofPattern("EEE")
private val rangeFormat = DateTimeFormatter.ofPattern("MMM d")

/** Real SQLite history. The scope (distracting or all) and the period are stated above the numbers. */
@Composable
fun StatsScreen(contentPadding: PaddingValues) {
    val stats = (LocalContext.current.applicationContext as TaptationApplication).stats
    val changes by stats.changes.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(false) }
    var includeImportant by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(changes, month, includeImportant) {
        try {
            summary = if (month) stats.monthly(includeImportant) else stats.weekly(includeImportant)
            error = null
        } catch (failure: Exception) { error = failure.localizedMessage ?: "Couldn't load your history" }
    }
    TabScreen(contentPadding, "Stats") {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SegmentedChoice(listOf("Last 7 days", "Last 30 days"), if (month) 1 else 0, { month = it == 1 }, Modifier.weight(1.4f))
                ScopeMenu(includeImportant, { includeImportant = it }, Modifier.weight(1f))
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val current = summary
        if (current == null) item { Text("Loading history…") }
        else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(periodRange(current))
                    FigureStrip(current)
                }
            }
            when (StatsText.emptiness(current)) {
                StatsText.Emptiness.NoActivity -> item {
                    SectionCard("No activity recorded", "Taptation hasn't seen a notification tap or swipe in this period. " +
                        "Keep monitoring on and check back.") {}
                }
                StatsText.Emptiness.NoCatches -> item {
                    SectionCard("No catches this period", "You opened ${current.taps} ${if (includeImportant) "" else "distracting "}notification${if (current.taps == 1) "" else "s"} " +
                        "and none caught you.") {
                        StatusChip("0 caught · ${current.taps} opened")
                    }
                }
                StatsText.Emptiness.HasCatches -> Unit
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Daily catches", meta = "avg ${"%.2f".format(current.averageCatchesPerDay)} a day")
                    ListGroup { Column(Modifier.padding(16.dp)) {
                        DailyChart(current.dailyCatches, current.caught == 0 && current.taps == 0, month)
                    } }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("By notification type", meta = if (current.categories.isEmpty()) null else "opened · swiped")
                    ListGroup {
                        if (current.categories.isEmpty()) Text("Nothing detected yet. Your first notification tap or swipe will appear here",
                            Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        current.categories.forEachIndexed { index, category ->
                            if (index > 0) RowDivider()
                            val name = StatsText.categoryName(category.category)
                            val total = category.taps + category.dismissals
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {
                                contentDescription = "$name: ${category.taps} opened, ${category.dismissals} swiped away"
                            }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${category.taps} · ${category.dismissals}", style = MetaText, color = Taptation.palette.muted)
                                }
                                ThinBar(if (total > 0) category.taps.toFloat() / total else 0f)
                            }
                        }
                    }
                }
            }
            item { Text(if (includeImportant) "Every notification Taptation detected, including important ones and checks with no result. Bars show the share you opened"
                else "Only distracting notifications, the ones that can catch you. Bars show the share you opened",
                Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

internal fun periodLabel(summary: StatsSummary, month: Boolean): String {
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(summary.fromMillis).atZone(zone).toLocalDate()
    val to = Instant.ofEpochMilli(summary.toMillis - 1).atZone(zone).toLocalDate()
    return "${if (month) "Last 30 days" else "Last 7 days"} · ${from.format(rangeFormat)} – ${to.format(rangeFormat)}"
}

internal fun periodRange(summary: StatsSummary): String {
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(summary.fromMillis).atZone(zone).toLocalDate()
    val to = Instant.ofEpochMilli(summary.toMillis - 1).atZone(zone).toLocalDate()
    return "${from.format(rangeFormat)} – ${to.format(rangeFormat)}"
}

/** Distracting only, or everything detected. A menu keeps the period control and the scope on one line. */
@Composable
private fun ScopeMenu(includeImportant: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { contentDescription = "Showing " + if (includeImportant) "all detected" else "distracting" },
            shape = ControlShape, color = Taptation.palette.raised, contentColor = MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (includeImportant) "All detected" else "Distracting", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1)
                LineIcon(R.drawable.ic_chevron_down, size = 16.dp)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            listOf(false to "Distracting", true to "All detected").forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, modifier = Modifier.semantics { selected = value == includeImportant },
                    onClick = { open = false; onChange(value) })
            }
        }
    }
}

/** Catches, opened, swiped: three figures on one card. Only catches take the accent. A missing denominator is a dash. */
@Composable
private fun FigureStrip(s: StatsSummary) {
    val all = s.taps + s.dismissals
    fun share(part: Int) = if (all == 0) "—" else "${(part * 100f / all).roundToInt()}% of all"
    ListGroup {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Figure("Catches", s.caught.toString(), s.caughtPerTapPercent?.let { "${it.roundToInt()}% caught" } ?: "—",
                Modifier.weight(1f), Taptation.palette.catchText)
            androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Figure("Opened", s.taps.toString(), share(s.taps), Modifier.weight(1f))
            androidx.compose.material3.VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Figure("Swiped", s.dismissals.toString(), share(s.dismissals), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, context: String, modifier: Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier.padding(16.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MetaText, color = Taptation.palette.muted)
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color, maxLines = 1)
        Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ThinBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Taptation.palette.raised)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MetricCard(label: String, value: String, context: String, modifier: Modifier,
    chart: @Composable () -> Unit = {},
) {
    Surface(modifier.fillMaxHeight().heightIn(min = 148.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            chart()
            Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun OpenedSplitBar(opened: Int, swiped: Int) {
    val total = opened.toLong() + swiped
    val fraction = if (total > 0) opened.toFloat() / total else 0f
    val description = if (total == 0L) "No opens or swipes recorded"
        else "$opened opened, $swiped swiped. ${(fraction * 100).roundToInt()} percent opened."
    Row(Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = description }
        .then(Modifier.background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
        .then(Modifier.clip(RoundedCornerShape(4.dp)))) {
        if (opened > 0) Box(Modifier.weight(opened.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        if (swiped > 0) Box(Modifier.weight(swiped.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
    }
}

/** Bars with counts. Tap a bar (or use the arrows) to see that day's catches and notifications opened. */
@Composable
internal fun DailyChart(days: List<DailyCatches>, noActivity: Boolean, month: Boolean) {
    if (days.isEmpty()) return
    var selected by rememberSaveable(month, days.size) { mutableIntStateOf(days.lastIndex) }
    val index = selected.coerceIn(0, days.lastIndex)
    val max = days.maxOf { it.count }.coerceAtLeast(1)
    val weekly = days.size <= 7
    val color = Taptation.palette.catch
    val faded = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)
    val summary = days.joinToString { "${LocalDate.parse(it.date).format(dayFormat)}: ${it.count} catches" }
    Row(Modifier.fillMaxWidth().height(140.dp).then(Modifier.testTag("daily-chart")).semantics { contentDescription = summary }
        .pointerInput(days.size, weekly) {
            detectTapGestures { offset ->
                val gap = (if (weekly) 6.dp else 2.dp).toPx()
                val cell = (size.width - gap * (days.size - 1)) / days.size
                selected = ((offset.x + gap / 2) / (cell + gap)).toInt().coerceIn(0, days.lastIndex)
            }
        }, horizontalArrangement = Arrangement.spacedBy(if (weekly) 6.dp else 2.dp)) {
        days.forEachIndexed { i, day ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                if (weekly || i == index) Text(day.count.toString(), style = MetaText,
                    color = if (i == index) MaterialTheme.colorScheme.onSurface else Taptation.palette.muted)
                Box(Modifier.fillMaxWidth(if (weekly) .7f else .85f).height((86.dp * day.count / max).coerceAtLeast(2.dp))
                    .background(if (i == index) color else faded,
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp)))
                if (weekly) Text(LocalDate.parse(day.date).format(shortDay), style = MetaText,
                    color = if (i == index) MaterialTheme.colorScheme.onSurface else Taptation.palette.muted)
            }
        }
    }
    val day = days[index]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = index > 0, onClick = { selected = index - 1 }) {
            LineIcon(R.drawable.ic_chevron_left, size = 20.dp, description = "Earlier day") }
        Text("${LocalDate.parse(day.date).format(dayFormat)} · ${day.count} catch${if (day.count == 1) "" else "es"}" +
            if (noActivity) " · no activity" else "",
            Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(enabled = index < days.lastIndex, onClick = { selected = index + 1 }) {
            LineIcon(R.drawable.ic_chevron_right, size = 20.dp, description = "Later day") }
    }
}
