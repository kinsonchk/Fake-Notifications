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

/** Notification rules: apps grouped by rule (changed in a bottom sheet), preference fields, then strictness. */
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
    var appsExpanded by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(setOf<String>()) }
    var saved by remember { mutableStateOf(false) }
    val config = snapshot?.config
    val matches = picker.search(search)
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    fun choose(pkg: String, choice: AppRule) {
        saving = saving + pkg
        repository.setAppRule(pkg, choice).whenComplete { _, failure -> scope.launch {
            saving = saving - pkg
            if (failure == null) saved = true
        } }
    }
    TabScreen(contentPadding, "Notification rules", itemSpacing = 16.dp,
        subtitle = "Every app starts on Let us decide. Set the ones that should always get through or always count as distracting",
        navigation = { BackLink("Settings", onBack) }) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (config == null) {
            item { Text("Loading saved settings…") }
            return@TabScreen
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, onValueChange = { search = it }, label = { Text("Find an app") },
                    leadingIcon = { LineIcon(com.codejam.taptation.R.drawable.ic_search) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = com.codejam.taptation.ui.theme.ControlShape)
                Text(if (picker.notificationFilterSupported) "Only apps allowed to send notifications are shown"
                    else "This Android version can’t check other apps’ notification permission, so all apps are shown",
                    Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            picker.apps == null && picker.error != null -> item {
                Text("Couldn’t load installed apps", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = app.apps::reload) { Text("Try again") }
            }
            picker.apps == null -> item { Text("Loading installed apps…") }
            matches.isEmpty() -> item { Text(if (search.isBlank()) "No apps with notification permission found."
                else "No matching apps with notification permission. Try a different name.",
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            search.isNotBlank() -> item {
                ListGroup {
                    matches.forEachIndexed { index, row ->
                        if (index > 0) RowDivider()
                        AppRuleListRow(row.app, labels, row.rule.label(), row.app.packageName !in saving) { sheetFor = row.app.packageName }
                    }
                }
            }
            else -> listOf(AppRule.AlwaysImportant, AppRule.AlwaysBait, AppRule.LetUsDecide).forEach { rule ->
                val rows = matches.filter { it.rule == rule }
                item(key = "group-" + rule.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(rule.label(), meta = rows.size.toString())
                        val collapsed = rule == AppRule.LetUsDecide && !appsExpanded
                        if (rows.isEmpty()) Text(if (rule == AppRule.AlwaysImportant) "None yet. Family, work, rides and banking usually belong here"
                            else "None yet", Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else if (!collapsed) ListGroup {
                            rows.forEachIndexed { index, row ->
                                if (index > 0) RowDivider()
                                AppRuleListRow(row.app, labels, null, row.app.packageName !in saving) { sheetFor = row.app.packageName }
                            }
                        }
                        if (rule == AppRule.LetUsDecide && rows.isNotEmpty()) TextButton(onClick = { appsExpanded = !appsExpanded },
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (appsExpanded) "Hide apps" else "Show apps")
                        }
                    }
                }
            }
        }
        val missing = if (search.isNotBlank()) emptyList() else picker.missingRules
        if (missing.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Apps no longer installed")
                ListGroup {
                    missing.forEachIndexed { index, pkg ->
                        if (index > 0) RowDivider()
                        GroupRow(labels.label(pkg), "Saved rules stay until you remove them") {
                            TextButton(enabled = pkg !in saving, onClick = {
                                saving = saving + pkg
                                repository.clearAppRule(pkg).whenComplete { _, failure -> scope.launch {
                                    saving = saving - pkg
                                    if (failure == null) saved = true
                                } }
                            }) { Text("Remove rule") }
                        }
                    }
                }
            }
        }
        item { CriteriaSection(config, repository) }
        item { StrictnessSection(config, repository) }
        item {
            Text(if (error != null) "Some changes could not be saved. Please try again."
                else if (saving.isNotEmpty()) "Saving changes…" else if (saved) "Changes saved" else "Changes save automatically",
                Modifier.padding(start = 4.dp, top = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    sheetFor?.let { pkg ->
        val row = picker.apps?.firstOrNull { it.app.packageName == pkg }
        if (row == null) sheetFor = null
        else AppRuleSheet(row.app, row.rule, labels, onDismiss = { sheetFor = null }) { choice -> choose(pkg, choice) }
    }
}

@Composable
private fun CriteriaSection(config: GameConfig, repository: GameRepository) {
    // Whether the AI judge works in this build (API keys) is the backend's answer, not BuildConfig's.
    val aiConfigured = (LocalContext.current.applicationContext as TaptationApplication).judges.isConfigured
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Text(if (aiConfigured) "For automatic decisions, notification content is sent to Cloudflare. Taptation doesn’t save the notification text. If a check fails or times out, there is no penalty."
            else "Automatic checking isn’t available in this build. Apps set to Let us decide won’t be penalized automatically. Choose Always distracting if you want an app to count as distracting, or Always important to keep it safe.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StrictnessSection(config: GameConfig, repository: GameRepository) {
    var index by remember(config.strictness) { mutableFloatStateOf(config.strictness.ordinal.toFloat()) }
    val current = Strictness.entries[index.roundToInt().coerceIn(0, Strictness.entries.lastIndex)]
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How strict should the AI be?", style = MaterialTheme.typography.titleLarge)
        Text("Only applies to apps set to Let us decide.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = com.codejam.taptation.ui.theme.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                    Strictness.Relaxed -> "Only clearly distracting notifications count as distracting. Uncertain ones are not penalized."
                    Strictness.Balanced -> "Likely distracting notifications can trigger catches. Important and uncertain ones are not penalized."
                    Strictness.Strict -> "More notifications count as distracting. Uncertain AI decisions are still not penalized."
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
