package com.codejam.taptation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.codejam.taptation.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.AppRule
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

    if (page == "rules") {
        NotificationRulesScreen(contentPadding, repository, onBack = { page = "main" })
        return
    }
    val title = when (page) {
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
        if (page == "feedback") {
            item { CatchPermissionCard() }
            return@TabScreen
        }
        item { ActivationControls(repository, config) }
        item { GameRulesCard(config, repository) }
        item { FreshnessControls(config, repository) }
        item {
            val bait = config.appRules.values.count { it == AppRule.AlwaysBait }
            val important = config.appRules.values.count { it == AppRule.AlwaysImportant }
            SettingsLink("Notification rules", "$bait always bait · $important always important", R.drawable.ic_tab_notifications) { page = "rules" }
        }
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
