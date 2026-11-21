package com.codejam.taptation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.codejam.taptation.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.debug.WhitelistAppsPanel
import com.codejam.taptation.logic.AppProfile
import com.codejam.taptation.logic.Difficulty
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.rememberGameRepository

/**
 * "How does the game behave?" Every control writes through GameRepository.updateConfig, the single
 * saved source of settings.
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("main") }
    BackHandler(enabled = page != "main") { page = "main" }

    val title = when (page) {
        "apps" -> "Always safe."
        "rules" -> "Every app,\nyour way."
        "words" -> "Words that matter."
        "feedback" -> "A moment\nto choose."
        else -> "Make it intentional."
    }
    key(page) {
    TabScreen(contentPadding, title, eyebrow = "Your rules, your pace") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val config = snapshot?.config
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        if (page != "main") item { TextButton(onClick = { page = "main" }) { Text("←  Back to settings") } }
        if (page == "apps") {
            item { WhitelistAppsPanel(repository, config.whitelistedPackages) }
            return@TabScreen
        }
        if (page == "rules") {
            item { PerAppRulesCard(config, repository) }
            return@TabScreen
        }
        if (page == "feedback") {
            item { CatchPermissionCard() }
            return@TabScreen
        }
        if (page == "words") {
            item {
                SectionCard("Important words", "Whole words and phrases in notification titles and text.") {
                    TermSetEditor("Important keyword", config.importantKeywords) { term ->
                        repository.updateConfig { it.copy(importantKeywords = it.importantKeywords.toggle(term)) }
                    }
                }
            }
            item {
                SectionCard("Distracting words") {
                    TermSetEditor("Bait keyword", config.baitKeywords) { term ->
                        repository.updateConfig { it.copy(baitKeywords = it.baitKeywords.toggle(term)) }
                    }
                }
            }
            return@TabScreen
        }
        item { ActivationControls(repository, config) }
        item { GameRulesCard(config, repository) }
        item { FreshnessControls(config, repository) }
        item {
            SectionCard("People who matter", "VIP names matched in notification titles.") {
                TermSetEditor("VIP name", config.vipNames) { name ->
                    repository.updateConfig { it.copy(vipNames = it.vipNames.toggle(name)) }
                }
            }
        }
        item { SettingsLink("Always-safe apps", "${config.whitelistedPackages.size} apps protected", R.drawable.ic_tab_home) { page = "apps" } }
        item { SettingsLink("Notification rules", "Conversations and community updates", R.drawable.ic_tab_notifications) { page = "rules" } }
        item { SettingsLink("Important & distracting words", "Fine-tune what gets your attention", R.drawable.ic_tab_settings) { page = "words" } }
        item { SettingsLink("Caught feedback", "Overlay and notification access", R.drawable.ic_tab_notifications) { page = "feedback" } }
    }
    }
}

@Composable
private fun GameRulesCard(config: GameConfig, repository: GameRepository) {
    SectionCard("Difficulty", "Fixed chance of a catch on an eligible bait tap.") {
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
    }
}

/** Freshness choices: off, then short windows for demos, then the usual minutes. */
private val freshnessSteps = listOf(0, 30, 60, 2 * 60, 5 * 60, 10 * 60, 15 * 60, 30 * 60, 60 * 60)

private fun freshnessLabel(seconds: Int): String = when {
    seconds == 0 -> "off"
    seconds < 60 -> "$seconds s"
    else -> "${seconds / 60} min"
}

@Composable
private fun FreshnessControls(config: GameConfig, repository: GameRepository) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard("Fresh notifications only", if (config.freshnessSeconds == 0) "Every eligible bait tap enters the draw."
        else "Older taps skip bait checks and catches.") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Window: ${freshnessLabel(config.freshnessSeconds)}", style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Done" else "Adjust") }
    }
    if (expanded) {
    // Saved values not on the list (set elsewhere) snap to the nearest step on the slider.
    val savedIndex = freshnessSteps.indices.minBy { kotlin.math.abs(freshnessSteps[it] - config.freshnessSeconds) }
    var index by remember(config.freshnessSeconds) { mutableFloatStateOf(savedIndex.toFloat()) }
    val seconds = freshnessSteps[index.toInt()]
    Text("Freshness window: ${freshnessLabel(seconds)}")
    Slider(
        value = index,
        onValueChange = { index = it },
        valueRange = 0f..(freshnessSteps.size - 1).toFloat(),
        steps = freshnessSteps.size - 2,
        onValueChangeFinished = {
            repository.updateConfig { it.copy(freshnessSeconds = freshnessSteps[index.toInt()]) }
        },
    )
    Text(
        if (seconds == 0) "Every bait tap can get you caught, however old the notification."
        else "Only taps within ${freshnessLabel(seconds)} of the notification can get you caught.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("Count from the first post")
            Text(
                "Off: each update (a new chat message) restarts the clock. On: the clock starts at the first post.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = config.freshnessFromFirstSeen,
            enabled = seconds != 0,
            onCheckedChange = { on -> repository.updateConfig { it.copy(freshnessFromFirstSeen = on) } },
        )
    }
    }
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rules that need a package name: bait app, app profile, important/bait channels. */
@Composable
private fun PerAppRulesCard(config: GameConfig, repository: GameRepository) {
    SectionCard(
        "Per-app rules",
        "Use package and channel names observed on the demo phone (Notifications tab).",
    ) {
        var packageName by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = packageName, onValueChange = { packageName = it },
            label = { Text("Package name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        val pkg = packageName.trim()
        OutlinedButton(enabled = pkg.isNotEmpty(), onClick = {
            repository.updateConfig { it.copy(baitPackages = it.baitPackages.toggle(pkg)) }
        }) { Text(if (pkg in config.baitPackages) "Remove bait app" else "Mark as bait app") }
        Text("Bait apps: ${config.baitPackages.sorted().joinToString().ifEmpty { "none" }}",
            style = MaterialTheme.typography.bodySmall)

        Text("App profile: ${config.appProfiles[pkg]?.name ?: "none"}", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppProfile.entries.forEach { profile ->
                OutlinedButton(enabled = pkg.isNotEmpty(), onClick = {
                    repository.updateConfig {
                        it.copy(appProfiles = if (it.appProfiles[pkg] == profile) it.appProfiles - pkg
                        else it.appProfiles + (pkg to profile))
                    }
                }) { Text(profile.name) }
            }
        }

        var channelName by rememberSaveable { mutableStateOf("") }
        OutlinedTextField(
            value = channelName, onValueChange = { channelName = it },
            label = { Text("Channel name for this package") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val channel = channelName.trim()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = pkg.isNotEmpty() && channel.isNotEmpty(), onClick = {
                repository.updateConfig { it.copy(importantChannels = it.importantChannels.toggle(pkg, channel)) }
            }) { Text("Important channel") }
            OutlinedButton(enabled = pkg.isNotEmpty() && channel.isNotEmpty(), onClick = {
                repository.updateConfig { it.copy(baitChannels = it.baitChannels.toggle(pkg, channel)) }
            }) { Text("Bait channel") }
        }
        Text("Important channels: ${config.importantChannels[pkg].orEmpty().joinToString().ifEmpty { "none" }}",
            style = MaterialTheme.typography.bodySmall)
        Text("Bait channels: ${config.baitChannels[pkg].orEmpty().joinToString().ifEmpty { "none" }}",
            style = MaterialTheme.typography.bodySmall)
    }
}

/** Text field + Add, and the current values as chips; tapping a chip removes it. */
@Composable
private fun TermSetEditor(label: String, values: Set<String>, onToggle: (String) -> Unit) {
    var input by rememberSaveable(label) { mutableStateOf("") }
    val term = input.trim()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = input, onValueChange = { input = it }, label = { Text(label) },
            singleLine = true, modifier = Modifier.weight(1f),
        )
        Button(enabled = term.isNotEmpty() && term !in values, onClick = {
            onToggle(term)
            input = ""
        }) { Text("Add") }
    }
    if (values.isEmpty()) {
        Text("None yet.", style = MaterialTheme.typography.bodySmall)
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.sorted().forEach { value ->
                InputChip(selected = false, onClick = { onToggle(value) }, label = { Text("$value  ✕") })
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private fun Map<String, Set<String>>.toggle(pkg: String, name: String): Map<String, Set<String>> {
    val names = this[pkg].orEmpty().toggle(name)
    return if (names.isEmpty()) this - pkg else this + (pkg to names)
}
