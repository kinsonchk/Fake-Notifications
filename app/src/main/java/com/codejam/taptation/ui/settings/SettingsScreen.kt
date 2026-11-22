package com.codejam.taptation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.contentDescription
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
fun SettingsScreen(contentPadding: PaddingValues, openPage: String? = null, onPageOpened: () -> Unit = {}, onShowTutorial: () -> Unit = {}) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("main") }
    BackHandler(enabled = page != "main") { page = "main" }

    androidx.compose.runtime.LaunchedEffect(openPage) {
        if (openPage != null) { page = if (openPage == SettingsPages.SafeApps) "rules" else openPage; onPageOpened() }
    }
    if (page == "feedback" || page == SettingsPages.Reminders) {
        RemindersPage(contentPadding) { page = "main" }
        return
    }
    if (page == "rules") {
        NotificationRulesScreen(contentPadding, repository, onBack = { page = "main" })
        return
    }

    val title = when (page) {
        "feedback" -> "Catch reminders"
        else -> "Settings"
    }
    key(page) {
    TabScreen(contentPadding, title) {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val config = snapshot?.config
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        if (page != "main") item { TextButton(onClick = { page = "main" }) { Text("←  Back to settings") } }
        if (page == "feedback") {
            item { Text("Manage catch reminders below.") }
            return@TabScreen
        }
        item { ActivationControls(repository, config) }
        item { GameRulesCard(config, repository) }
        item { FreshnessCard(config, repository) }
        item { AppearanceCard() }
        item {
            val bait = config.countOf(AppRule.AlwaysBait)
            val important = config.countOf(AppRule.AlwaysImportant)
            SettingsLink("Notification rules", "$bait always bait · $important always important", R.drawable.ic_tab_notifications) { page = "rules" }
        }
        item { SettingsLink("Catch reminders", "Permissions and delivery", R.drawable.ic_tab_notifications) { page = "feedback" } }
        item { SettingsLink("Learn Taptation again", "Review the main features and setup steps", R.drawable.ic_tab_home, onShowTutorial) }
    }
    }
}

@Composable
private fun AppearanceCard() {
    val appearance = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.codejam.taptation.TaptationApplication).appearance
    val mode by appearance.mode.collectAsStateWithLifecycle()
    val error by appearance.error.collectAsStateWithLifecycle()
    SectionCard("Appearance", "Dark is the default. Choose the look you prefer.") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.codejam.taptation.logic.Appearance.entries.forEach { choice ->
                FilterChip(selected = mode == choice, enabled = mode != null,
                    onClick = { appearance.setAppearance(choice) },
                    modifier = Modifier.semantics { contentDescription = "${choice.name} theme" },
                    label = { Text(choice.name) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun GameRulesCard(config: GameConfig, repository: GameRepository) {
    SectionCard("Difficulty", "Chance of a catch when you open a distracting notification within your time limit.") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                val select = { repository.setDifficulty(difficulty); Unit }
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

/** Everyday choices (allowed values owned by the backend); short demo windows are in the Debug tab. */
private val timeLimitMinutes = GameConfig.FRESHNESS_CHOICES_MINUTES



@Composable
private fun FreshnessCard(config: GameConfig, repository: GameRepository) {
    var changing by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val on = config.freshnessEnabled
    SectionCard("Ignore older notifications") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (on) "Opening a notification more than ${timeLimitText(config.freshnessSeconds)} after " +
                (if (config.freshnessFromFirstSeen) "Taptation first saw it" else "its latest update") + " won't trigger a catch."
                else "Every distracting notification can catch you, however old it is.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Switch(checked = on, modifier = Modifier.semantics { contentDescription = "Ignore older notifications" },
                onCheckedChange = { enabled -> repository.setFreshnessEnabled(enabled) })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = on, onClick = { changing = !changing }) { Text(if (changing) "Done" else "Change time") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Advanced  ⌃" else "Advanced  ⌄") }
        }
        if (changing && on) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            timeLimitMinutes.forEach { minutes ->
                FilterChip(selected = config.freshnessSeconds == minutes * 60,
                    onClick = { repository.setFreshnessMinutes(minutes) },
                    label = { Text(timeLimitText(minutes * 60)) })
            }
        }
        if (advanced) {
            Text("Count age from", style = MaterialTheme.typography.titleSmall)
            com.codejam.taptation.ui.common.RadioOption(!config.freshnessFromFirstSeen, "Latest update",
                "A new message in the same chat restarts the clock.") {
                repository.setFreshnessFromFirstSeen(false)
            }
            com.codejam.taptation.ui.common.RadioOption(config.freshnessFromFirstSeen, "First time Taptation saw it",
                "The clock starts once, even if the app updates the notification.") {
                repository.setFreshnessFromFirstSeen(true)
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

object SettingsPages {
    const val Main = "main"
    const val SafeApps = "apps"
    const val Rules = "rules"
    const val Reminders = "reminders"
}
