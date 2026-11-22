package com.codejam.taptation.debug

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.NotificationContext
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.service.TapLog
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.formatClock
import com.codejam.taptation.ui.common.rememberGameRepository

/** "Is the system OK?" plus demo cheats. Debug builds only; not the real UI. */
@Composable
fun DebugScreen(contentPadding: PaddingValues = PaddingValues(), controls: DemoControls? = null) {
    val context = LocalContext.current
    val repository = rememberGameRepository()
    val scope = rememberCoroutineScope()
    val demoControls = controls ?: remember(repository, scope) { RepositoryDemoControls(repository, scope) }
    val error by repository.error.collectAsStateWithLifecycle()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    var override by remember(snapshot?.config?.catchProbability) {
        mutableFloatStateOf(((snapshot?.config?.catchProbability ?: 0.2) * 100).toFloat())
    }

    var accessGranted by remember { mutableStateOf(PermissionHelper.isNotificationAccessGranted(context)) }
    var batteryUnrestricted by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    // The user returns from system settings here, so refresh both on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessGranted = PermissionHelper.isNotificationAccessGranted(context)
        batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(context)
    }
    val connected by TapListenerService.connected.collectAsStateWithLifecycle()
    val listenerTaps by TapLog.entries.collectAsStateWithLifecycle()

    TabScreen(contentPadding, "Debug") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        item {
            SectionCard("System") {
                LabelValue("Notification access", if (accessGranted) "granted" else "NOT granted")
                LabelValue("Service connected", if (connected) "yes" else "no")
                LabelValue("Battery", if (batteryUnrestricted) "unrestricted" else "optimized")
                // Raw listener check, independent of the game logic.
                LabelValue(
                    "Last tap seen by listener",
                    listenerTaps.firstOrNull()?.let { "${it.packageName} at ${formatClock(it.timestampMillis)}" }
                        ?: "none",
                )
                Button(
                    onClick = { PermissionHelper.openNotificationAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open notification access settings") }
                OutlinedButton(
                    onClick = { PermissionHelper.openBatteryOptimizationSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open battery settings") }
                Text(
                    "Set this app to Unrestricted (App info > App battery usage) so the " +
                        "system doesn't kill the listener in the background.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { DemoControlsPanel(demoControls) }
        item {
            SectionCard("Debug probability override", "Release builds always use the chosen difficulty.") {
                Text("Catch chance: ${override.toInt()}%")
                Slider(value = override, valueRange = 0f..100f, onValueChange = { override = it },
                    onValueChangeFinished = { repository.setDebugCatchProbability(override / 100.0) })
                TextButton(onClick = { repository.setDebugCatchProbability(null) }) { Text("Use difficulty value") }
            }
        }

        item {
            SectionCard("Demo time limit", "Short windows for demos. Everyday choices live in Settings.") {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 seconds", 60 to "1 minute", 120 to "2 minutes").forEach { (seconds, label) ->
                        androidx.compose.material3.FilterChip(selected = snapshot?.config?.freshnessSeconds == seconds,
                            onClick = { repository.updateConfig { it.copy(freshnessSeconds = seconds) } },
                            label = { Text(label) })
                    }
                }
            }
        }

        item { StatsHarnessPanel() }

        item {
            SectionCard("Shortcuts", "Skip the notification shade. Custom taps are on the Notifications tab.") {
                OutlinedButton(
                    onClick = {
                        repository.updateConfig { it.copy(baitPackages = it.baitPackages + "com.test.fake") }
                        repository.processTap(NotificationContext("com.test.fake", title = "Demo notification",
                            text = "Routine update", category = "promo", isConversation = false, ageMillis = 0))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Simulate bait tap (com.test.fake)") }
                OutlinedButton(enabled = snapshot?.config?.freshnessSeconds != 0, onClick = {
                    repository.processTap(NotificationContext("com.test.fake", title = "Old demo notification",
                        text = "Routine update", category = "promo", isConversation = false,
                        ageMillis = (snapshot?.config?.freshnessWindowMillis ?: 600_000) + 1))
                }, modifier = Modifier.fillMaxWidth()) { Text("Simulate stale bait (skip catch logic)") }
            }
        }
    }
}
