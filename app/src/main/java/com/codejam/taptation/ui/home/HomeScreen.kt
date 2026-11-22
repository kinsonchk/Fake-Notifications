package com.codejam.taptation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codejam.taptation.logic.GameState
import com.codejam.taptation.logic.TapResult
import com.codejam.taptation.service.PermissionHelper
import com.codejam.taptation.service.TapListenerService
import com.codejam.taptation.ui.common.LabelValue
import com.codejam.taptation.ui.common.SectionCard
import com.codejam.taptation.ui.common.TabScreen
import com.codejam.taptation.ui.common.TapExplanations
import com.codejam.taptation.ui.common.formatDuration
import com.codejam.taptation.ui.common.rememberGameRepository
import com.codejam.taptation.ui.common.rememberNow
import com.codejam.taptation.ui.settings.ActivationControls
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment

/** The tree is decorative until the team chooses the metric that should drive its appearance. */
@Composable
fun HomeScreen(contentPadding: PaddingValues, onSettings: () -> Unit = {}, onTutorial: () -> Unit = {}) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val lastResult by repository.lastResult.collectAsStateWithLifecycle()
    val now = rememberNow(repository)

    TabScreen(contentPadding, "Room to grow.", eyebrow = "Your little corner") {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(50)) {
                    Text(if (snapshot?.config?.isActive(now) == true) "●  Taptation is active" else "●  Taking a break",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            TreeIllustration()
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("A little more room for you.", style = MaterialTheme.typography.titleMedium)
                Text("Notice the urge. Choose what comes next.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val state = snapshot
        if (state == null) {
            item { Text(if (error == null) "Loading saved game…" else "Saved game unavailable") }
        } else {
            item { ActivationControls(repository, state.config, now) }
            item { SetupBanner() }
            item {
                SectionCard("Want a refresher?", "Review how Taptation works and revisit the access settings.") {
                    Button(onClick = onTutorial, modifier = Modifier.fillMaxWidth()) {
                        Text("View the tutorial")
                    }
                }
            }
            if (state.taps.isEmpty()) item {
                SectionCard("Keep the important things close.", "Choose the apps and people that should always be safe.") {
                    TextButton(onClick = onSettings) { Text("Choose my safe apps  →") }
                }
            }
            else item {
                SectionCard("Latest activity") {
                    state.taps.takeLast(2).asReversed().forEach { tap ->
                        Text(tap.packageName, style = MaterialTheme.typography.titleSmall)
                        Text(TapExplanations.outcome(tap.outcome, tap.reason, tap.damage),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { TreeStatusCard(state, now) }
            lastResult?.let { result -> item { LastTapCard(result) } }
        }
    }
}

@Composable
private fun TreeStatusCard(state: GameState, now: Long) {
    SectionCard(title = "Your progress", subtitle = "${mood(state.health)} · ${state.health}/100 health") {
        LinearProgressIndicator(
            progress = { state.health / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = if (state.health < 30) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        LabelValue("Catches", state.totalCatches.toString())
        LabelValue("Notification taps", state.tapsPerApp.values.sum().toString())
        state.timeSinceLastCatchMillis(now)?.let { LabelValue("Since last catch", formatDuration(it)) }
        val lockoutMillis = (state.lockoutUntilMillis - now).coerceAtLeast(0)
        if (lockoutMillis > 0) {
            Text(
                "Catch cooldown: ${(lockoutMillis + 999) / 1000} s left",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (state.isDead) {
            Text("Your tree has died.")
        }
    }
}

@Composable
private fun LastTapCard(result: TapResult) {
    SectionCard(title = "Last tap") {
        Text("${TapExplanations.classification(result.classification)}: ${TapExplanations.rule(result.rule)}")
        Text(TapExplanations.outcome(result.outcome, result.reason), style = MaterialTheme.typography.bodySmall)
    }
}

/** Shown only when taps can't be detected: notification access off or listener not bound. */
@Composable
private fun SetupBanner() {
    val context = LocalContext.current
    var accessGranted by remember { mutableStateOf(PermissionHelper.isNotificationAccessGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessGranted = PermissionHelper.isNotificationAccessGranted(context)
    }
    val connected by TapListenerService.connected.collectAsStateWithLifecycle()
    if (accessGranted && connected) return
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (!accessGranted) "Let your tree notice notification taps."
                else "Waiting for notification access to connect.",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            if (!accessGranted) {
                Button(onClick = { PermissionHelper.openNotificationAccessSettings(context) }) {
                    Text("Turn on notification access")
                }
            }
        }
    }
}

private fun mood(health: Int): String = when {
    health == 0 -> "Dead"
    health < 30 -> "Struggling"
    health < 70 -> "Wilting"
    else -> "Healthy"
}
