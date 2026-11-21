package com.codejam.taptation.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.Strictness
import com.codejam.taptation.ui.common.InstalledApp
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.rememberInstalledApps
import kotlinx.coroutines.delay

/**
 * Settings > Notification rules: one choice per installed app, plus the answers and strictness used by
 * "Let us decide". Layout follows notification-settings.html; the design is still to be polished.
 */
@Composable
fun NotificationRulesScreen(contentPadding: PaddingValues, repository: GameRepository, onBack: () -> Unit) {
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val installed = rememberInstalledApps()
    var search by rememberSaveable { mutableStateOf("") }

    TabScreen(
        contentPadding, "Notification rules", eyebrow = "Your rules, your pace",
        subtitle = "Tell the app which notifications are bait and which ones always get through. " +
            "Anything you leave on “Let us decide” is judged by your answers below.",
    ) {
        item { TextButton(onClick = onBack) { Text("←  Back to settings") } }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val config = snapshot?.config
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        item {
            SectionCard(
                "Your apps",
                "“Always important” notifications are never penalized. " +
                    "“Always bait” notifications can get you caught if you tap them quickly. " +
                    "Only apps allowed to send notifications are listed.",
            ) {
                OutlinedTextField(
                    value = search, onValueChange = { search = it }, label = { Text("Find an app") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                AppRuleList(installed.apps, installed.error, installed.reload, search, config) { pkg, rule ->
                    repository.setAppRule(pkg, rule)
                }
            }
        }
        item { CriteriaCard(config, repository) }
        item { StrictnessCard(config, repository) }
    }
}

@Composable
private fun AppRuleList(
    apps: List<InstalledApp>?, loadError: String?, reload: () -> Unit, search: String, config: GameConfig,
    onChange: (String, AppRule) -> Unit,
) {
    when {
        loadError != null -> {
            Text("Could not load apps: $loadError", color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = reload) { Text("Try again") }
            return
        }
        apps == null -> {
            Text("Loading installed apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
    }
    val query = search.trim()
    // Only apps allowed to send notifications.
    val matches = apps.filter {
        it.notificationsEnabled &&
            (query.isEmpty() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (matches.isEmpty()) Text("No matching apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        matches.forEach { app ->
            AppRuleRow(app.label, app.packageName, config.ruleFor(app.packageName)) { onChange(app.packageName, it) }
        }
        // Choices saved for apps that are no longer installed stay until reset here.
        val installedPackages = apps.mapTo(HashSet()) { it.packageName }
        val missing = config.appRules.keys.filter { it !in installedPackages }.sorted()
        if (missing.isNotEmpty() && query.isEmpty()) {
            Text("Saved for apps not installed", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp))
            missing.forEach { pkg -> AppRuleRow(pkg, null, config.ruleFor(pkg)) { onChange(pkg, it) } }
        }
    }
}

private fun AppRule.label(): String = when (this) {
    AppRule.AlwaysBait -> "Always bait"
    AppRule.AlwaysImportant -> "Always important"
    AppRule.LetUsDecide -> "Let us decide"
}

@Composable
private fun AppRule.color(): Color = when (this) {
    AppRule.AlwaysBait -> MaterialTheme.colorScheme.tertiary
    AppRule.AlwaysImportant -> MaterialTheme.colorScheme.primary
    AppRule.LetUsDecide -> MaterialTheme.colorScheme.outlineVariant
}

/** One app: the left bar colour shows the current choice, the button opens the three choices. */
@Composable
private fun AppRuleRow(name: String, packageName: String?, rule: AppRule, onChange: (AppRule) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(rule.color()))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                packageName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(Modifier.padding(end = 10.dp)) {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }) { Text("${rule.label()}  ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    AppRule.entries.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.label(), fontWeight = if (choice == rule) FontWeight.Medium else null) },
                            onClick = { expanded = false; if (choice != rule) onChange(choice) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CriteriaCard(config: GameConfig, repository: GameRepository) {
    SectionCard("How should we judge the rest?", "These answers guide the AI for apps set to “Let us decide.”") {
        CriteriaField(
            "What makes a message essential for you?",
            "Example: messages from my family, anything about my shifts, delivery codes, and meeting changes.",
            config.essentialCriteria,
        ) { text -> repository.updateConfig { it.copy(essentialCriteria = text) } }
        CriteriaField(
            "What makes a message non-essential for you?",
            "Example: likes, follows, game rewards, sales, and group chats I’m not mentioned in.",
            config.nonEssentialCriteria,
        ) { text -> repository.updateConfig { it.copy(nonEssentialCriteria = text) } }
        Text(
            "For now, “Let us decide” counts every notification as bait. Your answers are saved for when it learns to judge.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Saves half a second after typing stops, and when the page closes, so there is no save button. */
@Composable
private fun CriteriaField(label: String, placeholder: String, saved: String, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(saved) }
    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(saved)
    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(text) {
        if (text == saved) return@LaunchedEffect
        delay(500)
        onSave(text)
    }
    DisposableEffect(Unit) {
        onDispose { if (latestText != latestSaved) latestOnSave(latestText) }
    }
    Text(label, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(GameConfig.MAX_CRITERIA_LENGTH) },
        placeholder = { Text(placeholder) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val strictnessText = mapOf(
    Strictness.Relaxed to "Relaxed: only clear bait is marked as bait. When unsure, the message is treated as important, so few messages count as bait.",
    Strictness.Balanced to "Balanced: obvious bait is marked as bait, obvious important messages get through, and unclear ones are left alone.",
    Strictness.Strict to "Strict: anything that isn’t clearly important is marked as bait, so more messages count as bait.",
)

@Composable
private fun StrictnessCard(config: GameConfig, repository: GameRepository) {
    SectionCard("How strict should the AI be?", "This sets how readily a message is marked as bait when it isn’t clear.") {
        var index by remember(config.strictness) { mutableFloatStateOf(config.strictness.ordinal.toFloat()) }
        val current = Strictness.entries[index.toInt()]
        Slider(
            value = index,
            onValueChange = { index = it },
            valueRange = 0f..(Strictness.entries.size - 1).toFloat(),
            steps = Strictness.entries.size - 2,
            onValueChangeFinished = {
                repository.updateConfig { it.copy(strictness = Strictness.entries[index.toInt()]) }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Strictness.entries.forEach { level ->
                Text(
                    level.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (level == current) FontWeight.Medium else null,
                    color = if (level == current) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(strictnessText.getValue(current), style = MaterialTheme.typography.bodyMedium)
    }
}
