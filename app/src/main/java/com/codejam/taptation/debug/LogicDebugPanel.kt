package com.codejam.taptation.debug

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.codejam.taptation.TaptationApplication
import com.codejam.taptation.logic.CatchLevel
import com.codejam.taptation.logic.AppProfile
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.service.TapListenerService
import kotlinx.coroutines.delay

/** Integration surface for Persons 2/4. Person 3 owns the final home/settings/lockout UI. */
@Composable
fun LogicDebugPanel() {
    val context = LocalContext.current
    val repository = (context.applicationContext as TaptationApplication).game
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val result by repository.lastResult.collectAsStateWithLifecycle()
    val observations by TapListenerService.observations.collectAsStateWithLifecycle()
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { repository.refresh() }
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            now = System.currentTimeMillis()
            while (true) {
                delay(1000)
                val updated = System.currentTimeMillis()
                if (updated / 60_000 != now / 60_000) repository.refresh()
                now = updated
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Consequence logic", style = MaterialTheme.typography.titleLarge)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val state = snapshot
        if (state == null) {
            Text(if (error == null) "Loading saved game…" else "Saved game unavailable")
        } else {
            Text("Health: ${state.health}/100 • Catches: ${state.totalCatches}")
            Text("Total taps: ${state.tapsPerApp.values.sum()}")
            if (state.isDead) {
                Text("Tree is dead. Reset to start growing again.")
                Button(onClick = { repository.reset() }) { Text("Reset tree") }
            }
            val seconds = ((state.lockoutUntilMillis - now).coerceAtLeast(0) + 999) / 1000
            if (seconds > 0) Text("Lockout remaining: ${seconds}s", color = MaterialTheme.colorScheme.error)
            result?.let { Text("Last tap: ${it.outcome} (${it.reason})") }

            var probability by remember(state.config.catchProbability) {
                mutableFloatStateOf(state.config.catchProbability.toFloat())
            }
            Text("Catch chance: ${"%.1f".format(probability * 100)}%")
            Slider(
                value = probability,
                onValueChange = { probability = it },
                onValueChangeFinished = {
                    repository.updateConfig { it.copy(catchProbability = probability.toDouble()) }
                },
            )
            Text("Level: ${state.config.level}")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                CatchLevel.entries.forEach { level ->
                    OutlinedButton(onClick = { repository.updateConfig { it.copy(level = level) } }) {
                        Text(level.name)
                    }
                }
            }
            var packageName by remember { mutableStateOf("") }
            OutlinedTextField(value = packageName, onValueChange = { packageName = it },
                label = { Text("App package name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val pkg = packageName.trim()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = pkg.isNotEmpty(), onClick = {
                    repository.setWhitelisted(pkg, pkg !in state.config.whitelistedPackages)
                }) { Text(if (pkg in state.config.whitelistedPackages) "Unwhitelist" else "Whitelist") }
                OutlinedButton(enabled = pkg.isNotEmpty(), onClick = {
                    repository.updateConfig { it.copy(baitPackages = if (pkg in it.baitPackages)
                        it.baitPackages - pkg else it.baitPackages + pkg) }
                }) { Text(if (pkg in state.config.baitPackages) "Remove bait" else "Mark bait") }
            }
            Text("Whitelist: ${state.config.whitelistedPackages.sorted().joinToString().ifEmpty { "none" }}")
            Text("Bait apps: ${state.config.baitPackages.sorted().joinToString().ifEmpty { "none" }}")
            Text("App profile: ${state.config.appProfiles[pkg]?.name ?: "unconfigured"}")
            AppProfile.entries.forEach { profile ->
                OutlinedButton(enabled = pkg.isNotEmpty(), onClick = {
                    repository.updateConfig { it.copy(appProfiles = if (it.appProfiles[pkg] == profile)
                        it.appProfiles - pkg else it.appProfiles + (pkg to profile)) }
                }) { Text("Toggle ${profile.name} profile for entered package") }
            }
            Text("Use only package/channel names observed on the demo phone.",
                style = MaterialTheme.typography.bodySmall)
            var vipName by remember { mutableStateOf("") }
            OutlinedTextField(value = vipName, onValueChange = { vipName = it },
                label = { Text("VIP name (matches notification title)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            val vip = vipName.trim()
            OutlinedButton(enabled = vip.isNotEmpty(), onClick = {
                repository.updateConfig { it.copy(vipNames = if (vip in it.vipNames)
                    it.vipNames - vip else it.vipNames + vip) }
            }) { Text(if (vip in state.config.vipNames) "Remove VIP" else "Add VIP") }
            Text("VIPs: ${state.config.vipNames.sorted().joinToString().ifEmpty { "none" }}")
            var channelName by remember { mutableStateOf("") }
            OutlinedTextField(value = channelName, onValueChange = { channelName = it },
                label = { Text("Observed channel name for entered package") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            val channel = channelName.trim()
            OutlinedButton(enabled = pkg.isNotEmpty() && channel.isNotEmpty(), onClick = {
                repository.updateConfig {
                    val names = it.importantChannels[pkg].orEmpty()
                    it.copy(importantChannels = it.importantChannels + (pkg to
                        if (channel in names) names - channel else names + channel))
                }
            }) { Text("Toggle important channel") }
            OutlinedButton(enabled = pkg.isNotEmpty() && channel.isNotEmpty(), onClick = {
                repository.updateConfig {
                    val names = it.baitChannels[pkg].orEmpty()
                    it.copy(baitChannels = it.baitChannels + (pkg to
                        if (channel in names) names - channel else names + channel))
                }
            }) { Text("Toggle bait channel") }
            Text("Important channels: ${state.config.importantChannels[pkg].orEmpty().joinToString().ifEmpty { "none" }}")
            Text("Bait channels: ${state.config.baitChannels[pkg].orEmpty().joinToString().ifEmpty { "none" }}")
            var keyword by remember { mutableStateOf("") }
            OutlinedTextField(value = keyword, onValueChange = { keyword = it },
                label = { Text("Bait keyword") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val term = keyword.trim()
            OutlinedButton(enabled = term.isNotEmpty(), onClick = {
                repository.updateConfig { it.copy(baitKeywords = if (term in it.baitKeywords)
                    it.baitKeywords - term else it.baitKeywords + term) }
            }) { Text(if (term in state.config.baitKeywords) "Remove keyword" else "Add keyword") }
            Text("Keywords: ${state.config.baitKeywords.sorted().joinToString().ifEmpty { "none" }}")
            if (debug) {
                OutlinedButton(onClick = {
                    repository.updateConfig { it.copy(baitPackages = it.baitPackages + "com.test.fake") }
                    repository.processTap(NotificationContext("com.test.fake", title = "Demo notification",
                        text = "Routine update", category = "promo", isConversation = false, ageMillis = 0))
                }) { Text("Simulate bait tap") }
                OutlinedButton(onClick = { repository.applyCatch(state.config.level) },
                    enabled = !state.isDead && seconds == 0L) { Text("Force catch") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { repository.setHealthForDebug(100) }) { Text("Health 100") }
                    OutlinedButton(onClick = { repository.setHealthForDebug(10) }) { Text("Health 10") }
                }
                OutlinedButton(onClick = { repository.setHealthForDebug(0) }) { Text("Health 0") }
                Text("Observed notifications (memory only)", style = MaterialTheme.typography.titleMedium)
                observations.forEach {
                    Text("${it.packageName} • category=${it.category ?: "missing"} • " +
                        "conversation=${it.isConversation ?: "unknown"}\n" +
                        "channel=${it.channelName ?: "missing"}\ntitle=${it.title ?: "missing"}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            state.taps.takeLast(5).asReversed().forEach {
                Text("${it.packageName}: ${it.outcome} • ${it.healthBefore} → ${it.healthAfter}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
