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
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SegmentedChoice(listOf("Last 7 days", "Last 30 days"), if (month) 1 else 0, { month = it == 1 })
                Text("Showing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedChoice(listOf("Distracting", "All detected"), if (includeImportant) 1 else 0, { includeImportant = it == 1 })
                Text(if (includeImportant) "Every notification Taptation detected, including important and unsure ones."
                    else "Distracting notifications are the ones that can trigger a catch. Important and unsure ones are left out.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        val current = summary
        if (current == null) item { Text("Loading history…") }
        else {
            item { Text(periodLabel(current, month), style = MaterialTheme.typography.titleSmall) }
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("Opened vs. swiped", StatsText.ratioHeadline(current.taps, current.dismissals),
                        "${current.taps} opened · ${current.dismissals} swiped", Modifier.weight(1f)) {
                        OpenedSplitBar(current.taps, current.dismissals)
                    }
                    MetricCard("Catches", current.caught.toString(),
                        if (current.taps == 0) "No notifications opened" else
                            "in ${current.taps} opened" + (current.caughtPerTapPercent?.let { " (${it.roundToInt()}%)" } ?: ""),
                        Modifier.weight(1f))
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
                SectionCard("By notification type", if (current.categories.isEmpty()) null else "Bar shows the share you opened.") {
                    if (current.categories.isEmpty()) Text("Nothing detected yet. Your first notification tap or swipe will appear here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    current.categories.forEach { category ->
                        val name = StatsText.categoryName(category.category)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${category.taps} opened · ${category.dismissals} swiped away", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val total = category.taps + category.dismissals
                        if (total > 0) LinearProgressIndicator(progress = { category.taps.toFloat() / total },
                            modifier = Modifier.fillMaxWidth().semantics {
                                contentDescription = "$name: ${category.taps} opened, ${category.dismissals} swiped away"
                            })
                    }
                }
            }
            item {
                SectionCard("Daily catches") {
                    DailyChart(current.dailyCatches, current.caught == 0 && current.taps == 0, month)
                    Text("Average ${"%.2f".format(current.averageCatchesPerDay)} a day, counting days with none.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { Text("Based on notification taps and swipes Taptation detected.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

internal fun periodLabel(summary: StatsSummary, month: Boolean): String {
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(summary.fromMillis).atZone(zone).toLocalDate()
    val to = Instant.ofEpochMilli(summary.toMillis - 1).atZone(zone).toLocalDate()
    return "${if (month) "Last 30 days" else "Last 7 days"} · ${from.format(rangeFormat)} – ${to.format(rangeFormat)}"
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
    val color = MaterialTheme.colorScheme.primary
    val faded = MaterialTheme.colorScheme.primary.copy(alpha = .45f)
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
                if (weekly || i == index) Text(day.count.toString(), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.fillMaxWidth(if (weekly) .7f else .85f).height((86.dp * day.count / max).coerceAtLeast(2.dp))
                    .background(if (i == index) color else if (day.count == 0) MaterialTheme.colorScheme.outlineVariant else faded,
                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)))
                if (weekly) Text(LocalDate.parse(day.date).format(shortDay), style = MaterialTheme.typography.bodySmall,
                    color = if (i == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    val day = days[index]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = index > 0, onClick = { selected = index - 1 }) { Text("‹ Earlier") }
        Text("${LocalDate.parse(day.date).format(dayFormat)} · ${day.count} catch${if (day.count == 1) "" else "es"}" +
            if (noActivity) " · no activity" else "",
            Modifier.weight(1f).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(enabled = index < days.lastIndex, onClick = { selected = index + 1 }) { Text("Later ›") }
    }
}
