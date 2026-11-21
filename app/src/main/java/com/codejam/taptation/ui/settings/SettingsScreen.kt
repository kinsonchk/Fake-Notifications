package com.codejam.taptation.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 * saved source of settings. TODO(Person 3): final settings design (brief Section B).
 */
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()

    TabScreen(contentPadding, "Settings") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val config = snapshot?.config
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        item { GameRulesCard(config, repository) }
        item { ActivationControls(repository, config) }
        item { CatchPermissionCard() }
        item { WhitelistAppsPanel(repository, config.whitelistedPackages) }
        item {
            SectionCard("People", "A notification whose title matches a VIP name is always safe.") {
                TermSetEditor("VIP name", config.vipNames) { name ->
                    repository.updateConfig { it.copy(vipNames = it.vipNames.toggle(name)) }
                }
            }
        }
        item {
            SectionCard("Words", "Matched as whole words or phrases in the title and text, ignoring case.") {
                Text("Important keywords (always safe)", style = MaterialTheme.typography.titleSmall)
                TermSetEditor("Important keyword", config.importantKeywords) { term ->
                    repository.updateConfig { it.copy(importantKeywords = it.importantKeywords.toggle(term)) }
                }
                Text("Bait keywords", style = MaterialTheme.typography.titleSmall)
                TermSetEditor("Bait keyword", config.baitKeywords) { term ->
                    repository.updateConfig { it.copy(baitKeywords = it.baitKeywords.toggle(term)) }
                }
            }
        }
        item { PerAppRulesCard(config, repository) }
    }
}

@Composable
private fun GameRulesCard(config: GameConfig, repository: GameRepository) {
    SectionCard("Difficulty", "Fixed chance of a catch on an eligible bait tap.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                val select = { repository.updateConfig { it.copy(difficulty = difficulty, debugCatchProbability = null) }; Unit }
                val label = "${difficulty.name} ${(difficulty.probability * 100).toInt()}%"
                if (difficulty == config.difficulty) Button(onClick = select) { Text(label) }
                else OutlinedButton(onClick = select) { Text(label) }
            }
        }
        FreshnessControls(config, repository)
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

