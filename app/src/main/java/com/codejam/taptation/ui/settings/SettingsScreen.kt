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
import com.codejam.taptation.ui.common.GroupRow
import com.codejam.taptation.ui.common.LineIcon
import com.codejam.taptation.ui.common.ListGroup
import com.codejam.taptation.ui.common.RowDivider
import com.codejam.taptation.ui.common.SectionLabel
import com.codejam.taptation.ui.common.SegmentedChoice

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
        if (page != "main") item { com.codejam.taptation.ui.common.BackLink("Settings") { page = "main" } }
        if (page == "feedback") {
            item { Text("Manage catch reminders below.") }
            return@TabScreen
        }
        item { ActivationControls(repository, config) }
        item { GameRulesCard(config, repository) }
        item { FreshnessCard(config, repository) }
        item { AppearanceCard() }
        item {
            val distracting = config.countOf(AppRule.AlwaysBait)
            val important = config.countOf(AppRule.AlwaysImportant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("More")
                ListGroup {
                    GroupRow("Notification rules", "$distracting always distracting · $important always important",
                        icon = R.drawable.ic_shield, onClick = { page = "rules" })
                    RowDivider()
                    GroupRow("Catch reminders", "Permissions and delivery", icon = R.drawable.ic_bell, onClick = { page = "feedback" })
                    RowDivider()
                    GroupRow("Learn Taptation again", "The main features and setup steps", icon = R.drawable.ic_book, onClick = onShowTutorial)
                }
            }
        }
    }
    }
}

@Composable
private fun AppearanceCard() {
    val appearance = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.codejam.taptation.TaptationApplication).appearance
    val mode by appearance.mode.collectAsStateWithLifecycle()
    val error by appearance.error.collectAsStateWithLifecycle()
    val choices = com.codejam.taptation.logic.Appearance.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Appearance")
        SegmentedChoice(choices.map { it.name }, choices.indexOf(mode),
            { index -> if (mode != null) appearance.setAppearance(choices[index]) },
            descriptions = choices.map { "${it.name} theme" })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun GameRulesCard(config: GameConfig, repository: GameRepository) {
    val levels = Difficulty.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Difficulty")
        SegmentedChoice(levels.map { it.name }, levels.indexOf(config.difficulty), { repository.setDifficulty(levels[it]) },
            captions = levels.map { "${(it.probability * 100).toInt()}%" })
        Text("Catch chance once AI judges a notification distracting. Always distracting apps skip the roll",
            Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Everyday choices (allowed values owned by the backend); short demo windows are in the Debug tab. */
private val timeLimitMinutes = GameConfig.FRESHNESS_CHOICES_MINUTES



@Composable
private fun FreshnessCard(config: GameConfig, repository: GameRepository) {
    var changing by rememberSaveable { mutableStateOf(false) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val on = config.freshnessEnabled
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Older notifications")
        ListGroup {
            GroupRow("Ignore older notifications",
                if (on) "Opened more than ${timeLimitText(config.freshnessSeconds)} after " +
                    (if (config.freshnessFromFirstSeen) "Taptation first saw it" else "its latest update") + ", it can't catch you"
                else "Every distracting notification can catch you, however old it is") {
                Switch(checked = on, modifier = Modifier.semantics { contentDescription = "Ignore older notifications" },
                    onCheckedChange = { enabled -> repository.setFreshnessEnabled(enabled) })
            }
            if (on) {
                RowDivider()
                GroupRow("Time limit", value = timeLimitText(config.freshnessSeconds), onClick = { changing = !changing }) {
                    LineIcon(if (changing) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
                }
                if (changing) FlowRow(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeLimitMinutes.forEach { minutes ->
                        FilterChip(selected = config.freshnessSeconds == minutes * 60,
                            onClick = { repository.setFreshnessMinutes(minutes) },
                            label = { Text(timeLimitText(minutes * 60)) })
                    }
                }
                RowDivider()
                GroupRow("Count age from", value = if (config.freshnessFromFirstSeen) "First seen" else "Latest update",
                    onClick = { advanced = !advanced }) {
                    LineIcon(if (advanced) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
                }
                if (advanced) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.codejam.taptation.ui.common.RadioOption(!config.freshnessFromFirstSeen, "Latest update",
                        "A new message in the same chat restarts the clock") {
                        repository.setFreshnessFromFirstSeen(false)
                    }
                    com.codejam.taptation.ui.common.RadioOption(config.freshnessFromFirstSeen, "First time Taptation saw it",
                        "The clock starts once, even if the app updates the notification") {
                        repository.setFreshnessFromFirstSeen(true)
                    }
                }
            }
        }
    }
}

object SettingsPages {
    const val Main = "main"
    const val SafeApps = "apps"
    const val Rules = "rules"
    const val Reminders = "reminders"
}
