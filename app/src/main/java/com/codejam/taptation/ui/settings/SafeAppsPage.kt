package com.codejam.taptation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.GameConfig
import com.codejam.taptation.logic.GameRepository
import com.codejam.taptation.ui.common.AppIcon
import com.codejam.taptation.ui.common.AppLabels
import com.codejam.taptation.ui.common.AppListState
import com.codejam.taptation.ui.common.BackLink
import com.codejam.taptation.ui.common.InstalledApp
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.rememberAppLabels
import com.codejam.taptation.ui.common.rememberInstalledApps
import kotlinx.coroutines.launch

/**
 * Always-safe apps on a screen of their own: one scrolling list with search, selected apps first,
 * names and icons only. The list reloads each time the screen opens; a failed load offers Try again.
 */
@Composable
fun SafeAppsPage(contentPadding: PaddingValues, repository: GameRepository, config: GameConfig, onBack: () -> Unit) {
    val labels = rememberAppLabels()
    val apps = rememberInstalledApps()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }

    val installed = (apps.state as? AppListState.Loaded)?.apps.orEmpty()
    val selected = AppLabels.filter(
        config.whitelistedPackages.map { InstalledApp(it, labels.label(it)) }.sortedBy { it.label.lowercase() }, query)
    val others = AppLabels.filter(installed, query).filter { it.packageName !in config.whitelistedPackages }

    fun toggle(app: InstalledApp, safe: Boolean) {
        saveError = null
        repository.setWhitelisted(app.packageName, safe).whenComplete { _, failure ->
            if (failure != null) scope.launch {
                saveError = failure.cause?.localizedMessage ?: failure.localizedMessage ?: "Couldn't save this choice."
            }
        }
    }

    TabScreen(contentPadding, "Always-safe apps.", eyebrow = "Your rules, your pace", itemSpacing = 4.dp) {
        item { BackLink("Settings", onBack) }
        item {
            Text("You won't be caught when opening notifications from these apps.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        }
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Find an app") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }
        saveError?.let { message -> item { Text("Couldn't save: $message", color = MaterialTheme.colorScheme.error) } }
        when (val state = apps.state) {
            AppListState.Loading -> if (selected.isEmpty()) item { Text("Loading apps…", Modifier.padding(vertical = 12.dp)) }
            is AppListState.Failed -> item {
                SectionCard("Couldn't load your apps", "Check that Taptation can see your installed apps, then try again.") {
                    Button(onClick = apps::retry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Try again") }
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is AppListState.Loaded -> Unit
        }
        if (selected.isNotEmpty()) {
            item { SectionLabel("Selected · ${selected.size}") }
            items(selected, key = { "selected-" + it.packageName }) { AppChoiceRow(it, labels, true) { toggle(it, false) } }
        } else if (config.whitelistedPackages.isEmpty()) {
            item { Text("No apps selected yet.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp)) }
        }
        if (others.isNotEmpty()) {
            item { SectionLabel("All apps") }
            items(others, key = { "all-" + it.packageName }) { AppChoiceRow(it, labels, false) { toggle(it, true) } }
        }
        if (query.isNotBlank() && selected.isEmpty() && others.isEmpty() && apps.state is AppListState.Loaded) {
            item { Text("No apps match “${query.trim()}”.", Modifier.padding(vertical = 12.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun AppChoiceRow(app: InstalledApp, labels: AppLabels, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(app.packageName, labels, 40.dp)
        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Checkbox(checked = checked, onCheckedChange = null)
    }
}
