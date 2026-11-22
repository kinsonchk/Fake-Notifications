package com.codejam.taptation.ui.settings

import androidx.compose.ui.platform.LocalContext
import com.codejam.taptation.TaptationApplication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.BuildConfig
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.Strictness
import com.codejam.taptation.ui.common.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Native counterpart of notification-settings.html: app choices, preference fields, then strictness. */
@Composable
fun NotificationRulesScreen(contentPadding: PaddingValues, repository: GameRepository, onBack: () -> Unit) {
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as TaptationApplication
    // Which apps are listed, with their rule and the orphaned rules, is decided by the backend.
    val picker by app.rulePicker.collectAsStateWithLifecycle()
    val labels = rememberAppLabels()
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(setOf<String>()) }
    var saved by remember { mutableStateOf(false) }
    val config = snapshot?.config
    val matches = picker.search(search)
    TabScreen(contentPadding, "Notification rules", itemSpacing = 10.dp,
        subtitle = "Choose which apps are always important or always bait. Let us decide uses your answers below.") {
        item { BackLink("Settings", onBack) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        item {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Your apps", style = MaterialTheme.typography.titleLarge)
                Text(if (picker.notificationFilterSupported) "Only apps allowed to send notifications are shown."
                    else "This Android version can’t check other apps’ notification permission, so all apps are shown.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Always important: never penalized. Always bait: a quick tap can trigger a catch. Every app starts on Let us decide.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Find an app") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp))
            }
        }
        when {
            picker.apps == null && picker.error != null -> item {
                Text("Couldn’t load installed apps.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = app.apps::reload) { Text("Try again") }
            }
            picker.apps == null -> item { Text("Loading installed apps…") }
            matches.isEmpty() -> item { Text(if (search.isBlank()) "No apps with notification permission found."
                else "No matching apps with notification permission. Try a different name.",
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(matches, key = { it.app.packageName }) { row ->
            val pkg = row.app.packageName
            AppRuleRow(row.app, row.rule, labels, pkg !in saving) { choice ->
                saving = saving + pkg
                repository.setAppRule(pkg, choice).whenComplete { _, failure -> scope.launch {
                    saving = saving - pkg
                    if (failure == null) saved = true
                } }
            }
        }
        val missing = if (search.isNotBlank()) emptyList() else picker.missingRules
        if (missing.isNotEmpty()) item {
            Text("Apps no longer installed", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleSmall)
            Text("Saved rules stay until you remove them.", style = MaterialTheme.typography.bodySmall)
        }
        items(missing, key = { "missing-$it" }) { pkg ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(labels.label(pkg), Modifier.weight(1f))
                TextButton(enabled = pkg !in saving, onClick = {
                    saving = saving + pkg
                    repository.clearAppRule(pkg).whenComplete { _, failure -> scope.launch {
                        saving = saving - pkg
                        if (failure == null) saved = true
                    } }
                }) { Text("Remove rule") }
            }
        }
        item { CriteriaSection(config, repository) }
        item { StrictnessSection(config, repository) }
        item {
            Text(if (error != null) "Some changes could not be saved. Please try again."
                else if (saving.isNotEmpty()) "Saving changes…" else if (saved) "Changes saved" else "Changes save automatically",
                Modifier.padding(top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun AppRule.label() = when (this) {
    AppRule.AlwaysBait -> "Always bait"
    AppRule.AlwaysImportant -> "Always important"
    AppRule.LetUsDecide -> "Let us decide"
}

@Composable
private fun AppRuleRow(app: InstalledApp, rule: AppRule, labels: AppLabels, enabled: Boolean, onChange: (AppRule) -> Unit) {
    val stripe = when (rule) {
        AppRule.AlwaysBait -> MaterialTheme.colorScheme.tertiary
        AppRule.AlwaysImportant -> MaterialTheme.colorScheme.primary
        AppRule.LetUsDecide -> MaterialTheme.colorScheme.outline
    }
    BoxWithConstraints {
    val compact = maxWidth < 480.dp
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
            Box(Modifier.weight(1f).padding(14.dp)) {
                val appName: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.packageName, labels, 36.dp)
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    appName()
                    RuleSelector(app.label, rule, enabled, Modifier.fillMaxWidth(), onChange)
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { appName() }
                    RuleSelector(app.label, rule, enabled, Modifier.width(190.dp), onChange)
                }
            }
        }
    }
}
}

@Composable
private fun RuleSelector(app: String, rule: AppRule, enabled: Boolean, modifier: Modifier, onChange: (AppRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$app notification rule" }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (enabled) rule.label() else "Saving…", style = MaterialTheme.typography.labelLarge)
                Text("▾")
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            AppRule.entries.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label(), fontWeight = if (choice == rule) FontWeight.Medium else FontWeight.Normal) },
                    modifier = Modifier.semantics { selected = choice == rule },
                    onClick = { expanded = false; if (choice != rule) onChange(choice) })
            }
        }
    }
}

@Composable
private fun CriteriaSection(config: GameConfig, repository: GameRepository) {
    // Whether the AI judge works in this build (API keys) is the backend's answer, not BuildConfig's.
    val aiConfigured = (LocalContext.current.applicationContext as TaptationApplication).judges.isConfigured
    Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How should we judge the rest?", style = MaterialTheme.typography.titleLarge)
        Text("These answers guide the AI for apps set to Let us decide.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        CriteriaField("What makes a message essential for you?",
            "For example: family messages, shifts, delivery codes and meeting changes.", config.essentialCriteria) { text ->
            repository.setCriteria(essential = text)
        }
        CriteriaField("What makes a message non-essential for you?",
            "For example: likes, follows, game rewards, sales and group chatter.", config.nonEssentialCriteria) { text ->
            repository.setCriteria(nonEssential = text)
        }
        Text(if (aiConfigured) "For automatic decisions, notification content is sent to Cloudflare. Taptation doesn’t save the notification text. Uncertain or unavailable AI decisions are not penalized."
            else "Automatic decisions aren’t configured in this build. Let us decide currently treats notifications as bait. Choose Always important for apps you don’t want penalized.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StrictnessSection(config: GameConfig, repository: GameRepository) {
    var index by remember(config.strictness) { mutableFloatStateOf(config.strictness.ordinal.toFloat()) }
    val current = Strictness.entries[index.roundToInt().coerceIn(0, Strictness.entries.lastIndex)]
    Column(Modifier.padding(top = 26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How strict should the AI be?", style = MaterialTheme.typography.titleLarge)
        Text("Only applies to apps set to Let us decide.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Slider(index, onValueChange = { index = it }, valueRange = 0f..2f, steps = 1,
                    modifier = Modifier.semantics { contentDescription = "AI strictness" },
                    onValueChangeFinished = { repository.setStrictness(current) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Strictness.entries.forEach { level -> Text(level.name, style = MaterialTheme.typography.bodySmall,
                        color = if (level == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (level == current) FontWeight.Medium else FontWeight.Normal) }
                }
                Text(when (current) {
                    Strictness.Relaxed -> "Only clear bait is marked as bait. Uncertain notifications are not penalized."
                    Strictness.Balanced -> "Likely bait can trigger catches; important and uncertain notifications are not penalized."
                    Strictness.Strict -> "More notifications count as bait. Uncertain AI decisions are still not penalized."
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
