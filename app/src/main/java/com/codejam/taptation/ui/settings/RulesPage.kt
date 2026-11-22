package com.codejam.taptation.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.logic.ObservedTypes
import com.codejam.taptation.logic.RuleBehavior
import com.codejam.taptation.logic.RuleChoices
import com.codejam.taptation.logic.RuleTarget
import com.codejam.taptation.logic.SavedRule
import com.codejam.taptation.ui.common.AppIcon
import com.codejam.taptation.ui.common.AppLabels
import com.codejam.taptation.ui.common.AppListState
import com.codejam.taptation.ui.common.BackLink
import com.codejam.taptation.ui.common.InstalledApp
import com.codejam.taptation.ui.common.RadioOption
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.rememberAppLabels
import com.codejam.taptation.ui.common.rememberInstalledApps
import kotlinx.coroutines.delay

internal fun behaviorLabel(behavior: RuleBehavior) = when (behavior) {
    RuleBehavior.Distracting -> "Distracting"
    RuleBehavior.AlwaysSafe -> "Always safe"
    RuleBehavior.Default -> "Let Taptation decide"
}

private fun behaviorHelp(behavior: RuleBehavior) = when (behavior) {
    RuleBehavior.Distracting -> "Opening one may trigger a catch reminder."
    RuleBehavior.AlwaysSafe -> "Never triggers a catch."
    RuleBehavior.Default -> "Use the built-in rules. This is the default."
}

/**
 * Notification rules without package or channel names: choose an app, choose one of the
 * notification types it has sent, choose how it should count. The rule is written through
 * [RuleChoices] into the same settings the classifier already reads.
 */
@Composable
fun RulesPage(contentPadding: PaddingValues, repository: GameRepository, config: GameConfig, onBack: () -> Unit) {
    NotificationRulesScreen(contentPadding, repository, onBack)
}

internal fun ruleName(appName: String, channel: String?): String = if (channel == null) appName else "$appName · $channel"

private fun androidx.compose.foundation.lazy.LazyListScope.chooseAppStep(
    labels: AppLabels, observed: ObservedTypes, apps: com.codejam.taptation.ui.common.AppListController,
    query: String, onQuery: (String) -> Unit, onPick: (String) -> Unit, onCancel: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val counts = observed.appCounts(now)
    val installed = (apps.state as? AppListState.Loaded)?.apps.orEmpty()
    val recent = AppLabels.filter(counts.keys.map { InstalledApp(it, labels.label(it)) }, query)
        .sortedByDescending { counts[it.packageName] ?: 0 }
    val others = AppLabels.filter(installed, query).filter { it.packageName !in counts }
    item { BackLink("Cancel", onCancel) }
    item {
        OutlinedTextField(value = query, onValueChange = onQuery, label = { Text("Find an app") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
    }
    if (counts.isEmpty()) item {
        Text("Apps appear under “Sent notifications recently” after they send one. You can also pick any installed app.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (recent.isNotEmpty()) {
        item { ListHeader("Sent notifications recently") }
        items(recent, key = { "recent-" + it.packageName }) { app ->
            val n = counts[app.packageName] ?: 0
            PickRow(app, labels, "$n notification${if (n == 1) "" else "s"} this week") { onPick(app.packageName) }
        }
    }
    when (val state = apps.state) {
        AppListState.Loading -> item { Text("Loading apps…", Modifier.padding(top = 12.dp)) }
        is AppListState.Failed -> item {
            SectionCard("Couldn't load your apps", state.message) {
                Button(onClick = apps::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
            }
        }
        is AppListState.Loaded -> if (others.isNotEmpty()) {
            item { ListHeader(if (recent.isEmpty()) "All apps" else "Other apps") }
            items(others, key = { "other-" + it.packageName }) { app ->
                PickRow(app, labels, "No notifications seen yet") { onPick(app.packageName) }
            }
        }
    }
    if (query.isNotBlank() && recent.isEmpty() && others.isEmpty() && apps.state is AppListState.Loaded) {
        item { Text("No apps match “${query.trim()}”.", Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp))
}
@Composable
private fun PickRow(app: InstalledApp, labels: AppLabels, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(app.packageName, labels, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SelectedTarget(pkg: String, labels: AppLabels, name: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(pkg, labels, 40.dp)
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        TextButton(onClick = onAction) { Text(action) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.chooseTypeStep(
    labels: AppLabels, observed: ObservedTypes, now: Long, pkg: String, appName: String,
    wholeApp: Boolean, channel: String, onWhole: () -> Unit, onChannel: (String) -> Unit,
    onChangeApp: () -> Unit, onUseWholeApp: () -> Unit,
) {
    val types = observed.typesFor(pkg, now)
    item { SelectedTarget(pkg, labels, appName, "Change app", onChangeApp) }
    if (types.isEmpty()) {
        item {
            SectionCard("No types yet", "Taptation learns an app's notification types when it receives one. " +
                "Keep monitoring on and check back after $appName sends a notification.") {
                Button(onClick = onUseWholeApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Use for all $appName notifications") }
                OutlinedButton(onClick = onChangeApp, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) { Text("Choose another app") }
            }
        }
    } else {
        item { RadioOption(wholeApp, "All $appName notifications", "Applies to every type from this app", onWhole) }
        item { Text("Types seen on this phone", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(types, key = { "type-" + it.channel }) { type ->
            RadioOption(!wholeApp && channel == type.channel, type.channel,
                "${type.count} in the last 7 days") { onChannel(type.channel) }
        }
        item { Text("New types appear here after $appName sends one.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
internal fun StickyAction(label: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) { Text(label) }
        }
    }
}

@Composable
internal fun UndoBar(message: String, onUndo: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onUndo) { Text("Undo", color = MaterialTheme.colorScheme.inversePrimary) }
        }
    }
}
