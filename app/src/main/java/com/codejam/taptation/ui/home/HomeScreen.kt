package com.codejam.taptation.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.MonitoringStatus
import com.codejam.taptation.logic.TapRecord
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.ui.common.AppIcon
import com.codejam.taptation.ui.common.AppLabels
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.StatusPill
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.TapExplanations
import com.codejam.taptation.ui.common.formatDuration
import com.codejam.taptation.ui.common.rememberAppLabels
import com.codejam.taptation.ui.common.rememberGameRepository
import com.codejam.taptation.ui.common.rememberNow
import com.codejam.taptation.ui.settings.PauseFlow
import com.codejam.taptation.ui.settings.SettingsPages
import com.codejam.taptation.ui.settings.rememberPauseFlow

/**
 * Home: one status area, the tree (decorative until the team picks its metric), health, one
 * recent-activity list and all-time totals. Incomplete setup always comes first.
 */
@Composable
fun HomeScreen(contentPadding: PaddingValues, onOpenSettings: (String) -> Unit = {}, onTutorial: () -> Unit = {}) {
    val context = LocalContext.current
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val now = rememberNow(repository)
    val labels = rememberAppLabels()
    val pause = rememberPauseFlow(repository)
    val connected by TapListenerService.connected.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(PermissionHelper.isNotificationAccessGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access = PermissionHelper.isNotificationAccessGranted(context) }

    TabScreen(contentPadding, "Room to grow.", eyebrow = "Your little corner") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val state = snapshot
        if (state == null) {
            item { Text(if (error == null) "Loading saved game…" else "Saved game unavailable") }
            return@TabScreen
        }
        val status = MonitoringStatus.from(access, connected, state.config, now)
        item { StatusArea(status, pause) }
        if (state.taps.isEmpty()) item { FirstUseCard(access, state.config.whitelistedPackages.isEmpty(), state.config.difficulty, onOpenSettings) }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                TreeIllustration(height = if (status == MonitoringStatus.SetupNeeded) 130.dp else 170.dp)
                Text("Illustration only. It doesn't track health yet.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        if (status != MonitoringStatus.SetupNeeded || state.isDead) item { HealthCard(state, now, onReset = { repository.reset() }) }
        if (state.taps.isNotEmpty()) item { RecentActivity(state, labels) }
        if (state.totalCatches > 0 || state.tapsPerApp.isNotEmpty()) item { AllTimeCard(state, now) }
        item {
            SectionCard("Want a refresher?", "Review how Taptation works and revisit the access settings.") {
                androidx.compose.material3.OutlinedButton(
                    onClick = onTutorial,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("View the tutorial") }
            }
        }
    }
}

@Composable
private fun StatusArea(status: MonitoringStatus, pause: PauseFlow) {
    val context = LocalContext.current
    when (status) {
        MonitoringStatus.SetupNeeded -> NoticeCard("Setup needed", "Notification access is off. Nothing is being checked.") {
            Button(onClick = { PermissionHelper.openNotificationAccessSettings(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Turn on notification access")
            }
        }
        MonitoringStatus.ConnectionInterrupted -> NoticeCard("Connection interrupted",
            "Android stopped Taptation's notification listener. Taps aren't being checked.") {
            Text("Tip: set battery use to Unrestricted to stop this from happening.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Button(onClick = { TapListenerService.requestReconnect(context) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Reconnect") }
            TextButton(onClick = { PermissionHelper.openBatteryOptimizationSettings(context) }) { Text("Open battery settings") }
        }
        is MonitoringStatus.Paused -> SectionCard(
            if (status.untilMillis != null) "Paused until ${java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(status.untilMillis))}"
            else "Paused until you turn it back on",
            "No catches or health changes while paused. Your history is kept.") {
            Button(enabled = !pause.busy, onClick = pause.resume, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp)) { Text("Resume now") }
        }
        is MonitoringStatus.Monitoring -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            StatusPill("●  Monitoring notifications", good = true)
            TextButton(enabled = !pause.busy, onClick = pause.pause) { Text("Pause") }
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String, actions: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            actions()
        }
    }
}

@Composable
private fun FirstUseCard(access: Boolean, noSafeApps: Boolean, difficulty: com.codejam.taptation.logic.Difficulty, onOpenSettings: (String) -> Unit) {
    val context = LocalContext.current
    SectionCard("How Taptation works") {
        Step("1", "Some distracting notification taps trigger a reminder and lower your tree's health.")
        Step("2", "Important notifications are excluded: calls, alarms, always-safe apps and people you choose.")
        Step("3", "${difficulty.name} means a ${(difficulty.probability * 100).toInt()}% chance on a distracting tap, " +
            "so it won't happen on every notification.")
        if (!access || noSafeApps) {
            Text("Finish setup", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            if (!access) SetupRow("Allow notification access", "Required") { PermissionHelper.openNotificationAccessSettings(context) }
            if (noSafeApps) SetupRow("Choose always-safe apps", "Recommended") { onOpenSettings(SettingsPages.SafeApps) }
        }
    }
}

@Composable
private fun Step(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Text(number, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupRow(title: String, hint: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HealthCard(state: GameState, now: Long, onReset: () -> Unit) {
    val config = state.config
    SectionCard("Health") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(mood(state.health), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.health} / 100", style = MaterialTheme.typography.headlineSmall)
        }
        LinearProgressIndicator(
            progress = { state.health / 100f },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Health ${state.health} out of 100" },
            color = if (state.health < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        LabelValue("Each catch", "−${config.damage(config.level)} health")
        LabelValue("Recovery", "+${config.recoveryPerHour} per hour with no distracting taps")
        LabelValue("At 0 health", "Start over at 100; history and settings stay")
        val cooldown = (state.lockoutUntilMillis - now).coerceAtLeast(0)
        if (cooldown > 0) Text("No additional penalty for ${(cooldown + 999) / 1000} s (cooldown after a catch).",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        if (state.isDead) {
            Text("Your tree died. Starting over keeps your history and settings.")
            Button(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) { Text("Reset tree") }
        }
    }
}

@Composable
private fun RecentActivity(state: GameState, labels: AppLabels) {
    SectionCard("Recent activity") {
        state.taps.takeLast(4).asReversed().forEach { tap -> ActivityRow(tap, labels) }
    }
}

@Composable
private fun ActivityRow(tap: TapRecord, labels: AppLabels) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(tap.packageName, labels, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(labels.label(tap.packageName), style = MaterialTheme.typography.titleSmall)
            Text(TapExplanations.outcome(tap.outcome, tap.reason, tap.damage), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(tap.timestampMillis)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AllTimeCard(state: GameState, now: Long) {
    SectionCard("All time") {
        LabelValue("Catches", state.totalCatches.toString())
        LabelValue("Notifications opened", state.tapsPerApp.values.sum().toString())
        state.timeSinceLastCatchMillis(now)?.let { LabelValue("Since last catch", formatDuration(it)) }
    }
}

private fun mood(health: Int): String = when {
    health == 0 -> "Dead"
    health < 30 -> "Struggling"
    health < 70 -> "Wilting"
    else -> "Healthy"
}
