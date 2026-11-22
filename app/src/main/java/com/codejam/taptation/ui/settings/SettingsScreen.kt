package com.codejam.taptation.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.R
import com.codejam.taptation.logic.Difficulty
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.RuleChoices
import com.codejam.taptation.ui.common.AppLabels
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.StatusPill
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.TermListCard
import com.codejam.taptation.ui.common.rememberAppLabels
import com.codejam.taptation.ui.common.rememberGameRepository

/** Pages reachable from Settings; other screens can open one directly (e.g. Home → "apps"). */
object SettingsPages {
    const val Main = "main"
    const val SafeApps = "apps"
    const val Rules = "rules"
    const val People = "people"
    const val Words = "words"
    const val Reminders = "reminders"
}

/**
 * "How does the game behave?" Every control writes through GameRepository.updateConfig, the single
 * saved source of settings.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, openPage: String? = null, onPageOpened: () -> Unit = {}) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SettingsPages.Main) }
    LaunchedEffect(openPage) { if (openPage != null) { page = openPage; onPageOpened() } }
    BackHandler(enabled = page != SettingsPages.Main) { page = SettingsPages.Main }
    val back = { page = SettingsPages.Main }

    val config = snapshot?.config
    if (config == null) {
        TabScreen(contentPadding, "Make it intentional.", eyebrow = "Your rules, your pace") {
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            item { Text("Loading saved settings…") }
        }
        return
    }
    when (page) {
        SettingsPages.SafeApps -> SafeAppsPage(contentPadding, repository, config, back)
        SettingsPages.Rules -> RulesPage(contentPadding, repository, config, back)
        SettingsPages.Reminders -> RemindersPage(contentPadding, back)
        SettingsPages.People -> TabScreen(contentPadding, "Always get\nthrough.", eyebrow = "Your rules, your pace") {
            item { com.codejam.taptation.ui.common.BackLink("Settings", back) }
            item {
                TermListCard("People who should always get through",
                    "Enter their name as it appears in notifications, for example “Mom” or “Alex Chen”.",
                    "Matching names are treated as important. This doesn't use your contacts or verify who sent them.",
                    "Name", "No one added yet.", config.vipNames) { names -> repository.updateConfig { it.copy(vipNames = names) } }
            }
        }
        SettingsPages.Words -> TabScreen(contentPadding, "Words that matter.", eyebrow = "Your rules, your pace") {
            item { com.codejam.taptation.ui.common.BackLink("Settings", back) }
            item {
                TermListCard("Urgent words", "Whole words and phrases in a notification's title or text that make it important.",
                    null, "Urgent word", "None yet.", config.importantKeywords) { words -> repository.updateConfig { it.copy(importantKeywords = words) } }
            }
            item {
                TermListCard("Distracting words", "Whole words and phrases that make a notification count as distracting.",
                    "Urgent words and people who always get through still win.",
                    "Distracting word", "None yet.", config.baitKeywords) { words -> repository.updateConfig { it.copy(baitKeywords = words) } }
            }
        }
        else -> SettingsMain(contentPadding, repository, config, error) { page = it }
    }
}

@Composable
private fun SettingsMain(contentPadding: PaddingValues, repository: GameRepository, config: GameConfig,
    error: String?, open: (String) -> Unit,
) {
    val labels = rememberAppLabels()
    val reminders = rememberReminderPermissions()
    TabScreen(contentPadding, "Make it intentional.", eyebrow = "Your rules, your pace") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item { ActivationControls(repository, config) }
        item { DifficultyCard(config, repository) }
        item { FreshnessCard(config, repository) }
        item { SettingsLink("Always-safe apps", safeAppsSummary(config, labels), R.drawable.ic_tab_home) { open(SettingsPages.SafeApps) } }
        item { SettingsLink("Notification rules", rulesSummary(config, labels), R.drawable.ic_tab_notifications) { open(SettingsPages.Rules) } }
        item { SettingsLink("People who always get through", listSummary(config.vipNames, "No one added yet"), R.drawable.ic_tab_home) { open(SettingsPages.People) } }
        item { SettingsLink("Urgent and distracting words", listSummary(config.importantKeywords, "None yet"), R.drawable.ic_tab_settings) { open(SettingsPages.Words) } }
        item {
            SettingsLink("Catch reminders",
                if (reminders.issues == 0) "All set" else "${reminders.issues} permission${if (reminders.issues == 1) "" else "s"} needed",
                R.drawable.ic_tab_notifications, badge = if (reminders.issues > 0) "Action" else null) { open(SettingsPages.Reminders) }
        }
    }
}

internal fun listSummary(values: Set<String>, empty: String): String {
    val sorted = values.sortedBy { it.lowercase() }
    return when {
        sorted.isEmpty() -> empty
        sorted.size <= 3 -> sorted.joinToString(", ")
        else -> sorted.take(3).joinToString(", ") + " +${sorted.size - 3}"
    }
}

private fun safeAppsSummary(config: GameConfig, labels: AppLabels): String =
    listSummary(config.whitelistedPackages.map { labels.label(it) }.toSet(), "None chosen yet")

private fun rulesSummary(config: GameConfig, labels: AppLabels): String {
    val rules = RuleChoices.savedRules(config)
    return if (rules.isEmpty()) "Taptation's built-in rules"
    else "${rules.size} rule${if (rules.size == 1) "" else "s"}: " +
        listSummary(rules.map { ruleName(labels.label(it.target.packageName), it.target.channel) }.toSet(), "")
}

/** "About 1 in 5 distracting taps" for the fixed difficulties. */
internal fun chanceSummary(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.Easy -> "About 1 in 5 distracting taps."
    Difficulty.Medium -> "About 2 in 5 distracting taps."
    Difficulty.Hard -> "About 7 in 10 distracting taps."
} + " Important notifications are never caught."

@Composable
private fun DifficultyCard(config: GameConfig, repository: GameRepository) {
    SectionCard("Difficulty", "Chance of a catch each time you open a distracting notification.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                val select = { repository.updateConfig { it.copy(difficulty = difficulty, debugCatchProbability = null) }; Unit }
                val isSelected = difficulty == config.difficulty
                Surface(onClick = select, modifier = Modifier.weight(1f).semantics { selected = isSelected },
                    shape = RoundedCornerShape(16.dp), border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(difficulty.name, style = MaterialTheme.typography.labelLarge)
                        Text("${(difficulty.probability * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Text(chanceSummary(config.difficulty), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Everyday choices; short demo windows are in the Debug tab. */
private val timeLimitMinutes = listOf(5, 10, 15, 30, 60)

internal fun timeLimitText(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds == 60 -> "1 minute"
    seconds < 3600 -> "${seconds / 60} minutes"
    seconds == 3600 -> "1 hour"
    else -> "${seconds / 3600} hours"
}

@Composable
private fun FreshnessCard(config: GameConfig, repository: GameRepository) {
    var changing by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val on = config.freshnessSeconds > 0
    SectionCard("Ignore older notifications") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (on) "Opening a notification more than ${timeLimitText(config.freshnessSeconds)} after " +
                (if (config.freshnessFromFirstSeen) "Taptation first saw it" else "its latest update") + " won't trigger a catch."
                else "Every distracting notification can catch you, however old it is.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = on, modifier = Modifier.semantics { contentDescription = "Ignore older notifications" },
                onCheckedChange = { enabled -> repository.updateConfig { it.copy(freshnessSeconds = if (enabled) 600 else 0) } })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = on, onClick = { changing = !changing }) { Text(if (changing) "Done" else "Change time") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Advanced  ⌃" else "Advanced  ⌄") }
        }
        if (changing && on) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            timeLimitMinutes.forEach { minutes ->
                FilterChip(selected = config.freshnessSeconds == minutes * 60,
                    onClick = { repository.updateConfig { it.copy(freshnessSeconds = minutes * 60) } },
                    label = { Text(timeLimitText(minutes * 60)) })
            }
        }
        if (advanced) {
            Text("Count age from", style = MaterialTheme.typography.titleSmall)
            com.codejam.taptation.ui.common.RadioOption(!config.freshnessFromFirstSeen, "Latest update",
                "A new message in the same chat restarts the clock.") {
                repository.updateConfig { it.copy(freshnessFromFirstSeen = false) }
            }
            com.codejam.taptation.ui.common.RadioOption(config.freshnessFromFirstSeen, "First time Taptation saw it",
                "The clock starts once, even if the app updates the notification.") {
                repository.updateConfig { it.copy(freshnessFromFirstSeen = true) }
            }
        }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: Int, badge: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            badge?.let { StatusPill(it, good = false) }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
