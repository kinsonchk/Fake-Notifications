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
 * Home: one status area, a health-driven meadow, health, one
 * recent-activity list and all-time totals. Incomplete setup always comes first.
 */
@Composable
fun HomeScreen(contentPadding: PaddingValues, onOpenSettings: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val now = rememberNow(repository)
    val labels = rememberAppLabels()
    val pause = rememberPauseFlow(repository)
    val meadow = (context.applicationContext as com.codejam.taptation.TaptationApplication).meadow
    val meadowState by meadow.state.collectAsStateWithLifecycle()
    val meadowError by meadow.error.collectAsStateWithLifecycle()
    // Setup / paused / interrupted / monitoring is decided by the backend (service/SystemStatus).
    val monitoring by (context.applicationContext as com.codejam.taptation.TaptationApplication).system.monitoring
        .collectAsStateWithLifecycle()

    TabScreen(contentPadding, "Home") {
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val state = snapshot
        if (state == null) {
            item { Text(if (error == null) "Loading saved game…" else "Saved game unavailable") }
            return@TabScreen
        }
        monitoring?.let { status -> item { StatusArea(status, pause) } }
        item(key = "living-meadow") {
            val world = meadowState
            if (world == null) {
                GrassMeadow(health = state.health)
                if (meadowError == null) Text("Loading your meadow…",style=MaterialTheme.typography.bodySmall)
            }
            else LivingMeadow(state.health,world,meadow::discover)
            meadowError?.let {
                Text(it,color=MaterialTheme.colorScheme.error)
                TextButton(onClick=meadow::retry) { Text("Try again") }
            }
        }
        item { HealthCard(state, now, repository.recoveryIntervalMinutes(state.config)) }
        meadowState?.let { world -> item { MeadowGrowthCard(world) } }
        if (state.taps.isEmpty()) item { FirstUseCard(state.config.difficulty, onOpenSettings) }
        if (state.taps.isNotEmpty()) item { RecentActivity(state, labels) }
        if (state.totalCatches > 0 || state.tapsPerApp.isNotEmpty()) item { AllTimeCard(state, now) }
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
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
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
private fun FirstUseCard(difficulty: com.codejam.taptation.logic.Difficulty, onOpenSettings: (String) -> Unit) {
    SectionCard("Notification rules", "Choose apps that should always get through, then tell us what matters to you.") {
        Text("${difficulty.name}: ${(difficulty.probability * 100).toInt()}% catch chance on an eligible distracting tap.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onOpenSettings(SettingsPages.Rules) }) { Text("Choose apps and preferences") }
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
private fun HealthCard(state: GameState, now: Long, recoveryIntervalMinutes: Int) {
    val config = state.config
    androidx.compose.material3.Surface(shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("💚 Health", style = MaterialTheme.typography.titleMedium)
                Text("${state.health} / 100", style = MaterialTheme.typography.headlineSmall)
            }
            LinearProgressIndicator(progress = { state.health / 100f },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Health ${state.health} out of 100" },
                color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Catch: −${config.currentDamage}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Recovery: +${config.recoveryAmount} / $recoveryIntervalMinutes clean min",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End)
            }
            val cooldown = (state.lockoutUntilMillis - now).coerceAtLeast(0)
            if (cooldown > 0) Text("No extra catch for ${(cooldown + 999) / 1000} s.", style = MaterialTheme.typography.bodySmall)
            if (state.isDead) {
                Text("Health is at zero.", style = MaterialTheme.typography.bodySmall)
            }
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
