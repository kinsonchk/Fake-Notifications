package com.codejam.taptation.debug

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class InstalledApp(val packageName: String, val label: String)

/** Small app-wide whitelist picker backed by GameRepository's existing saved configuration. */
@Composable
fun WhitelistAppsPanel(repository: GameRepository, selectedPackages: Set<String>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var refreshVersion by remember { mutableStateOf(0) }
    var savingPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(context.packageManager, refreshVersion) {
        apps = null
        loadError = null
        try {
            apps = withContext(Dispatchers.IO) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val matches = queryLauncherApps(context.packageManager, intent)
                matches.asSequence()
                    .mapNotNull { info ->
                        val app = info.activityInfo?.applicationInfo ?: return@mapNotNull null
                        if (app.packageName == context.packageName) return@mapNotNull null
                        InstalledApp(app.packageName, app.loadLabel(context.packageManager).toString())
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
                    .toList()
            }
        } catch (failure: Exception) {
            loadError = failure.localizedMessage ?: "Could not read installed apps."
        }
    }

    val filteredApps = apps.orEmpty().filter {
        it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true)
    }
    val missingSelections = selectedPackages - apps.orEmpty().map { it.packageName }.toSet()

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Whitelisted apps", style = MaterialTheme.typography.titleMedium)
            Text("Every notification tap from a selected app is safe from catches.")
            Text("This applies to all notifications from that app.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Find an app") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(onClick = { refreshVersion++ }) { Text("Refresh apps") }
            when {
                apps == null && loadError == null -> Text("Loading installed apps…")
                loadError != null -> Text("Could not load apps: $loadError", color = MaterialTheme.colorScheme.error)
                filteredApps.isEmpty() -> Text("No matching apps found.")
                else -> Column(
                    Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    filteredApps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = app.packageName in selectedPackages,
                                enabled = app.packageName !in savingPackages,
                                onCheckedChange = { enabled ->
                                    savingPackages = savingPackages + app.packageName
                                    saveError = null
                                    repository.setWhitelisted(app.packageName, enabled).whenComplete { _, failure ->
                                        scope.launch {
                                            savingPackages = savingPackages - app.packageName
                                            if (failure != null) {
                                                saveError = failure.cause?.localizedMessage
                                                    ?: failure.localizedMessage
                                                    ?: "Could not save this choice."
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (apps != null && missingSelections.isNotEmpty()) {
                Text("Saved apps not currently installed:", style = MaterialTheme.typography.titleSmall)
                missingSelections.sorted().forEach { packageName ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(packageName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            repository.setWhitelisted(packageName, false)
                        }) { Text("Remove") }
                    }
                }
            }
            saveError?.let { Text("Could not save whitelist: $it", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Suppress("DEPRECATION")
private fun queryLauncherApps(packageManager: PackageManager, intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        packageManager.queryIntentActivities(intent, 0)
    }




