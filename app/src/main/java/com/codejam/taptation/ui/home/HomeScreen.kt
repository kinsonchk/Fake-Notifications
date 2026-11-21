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

/** "How is my tree?" Status only for now. TODO(Person 3): tree/pet visual. */
@Composable
fun HomeScreen(contentPadding: PaddingValues) {
    val repository = rememberGameRepository()
    val snapshot by repository.state.collectAsStateWithLifecycle()
    val error by repository.error.collectAsStateWithLifecycle()
    val lastResult by repository.lastResult.collectAsStateWithLifecycle()
    val now = rememberNow(repository)

    TabScreen(contentPadding, "Your tree") {
        item { SetupBanner() }
        error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        val state = snapshot
        if (state == null) {
            item { Text(if (error == null) "Loading saved game…" else "Saved game unavailable") }
        } else {
            item { TreeStatusCard(state, now, onReset = { repository.reset() }) }
            lastResult?.let { result -> item { LastTapCard(result) } }
        }
    }
}

@Composable
private fun TreeStatusCard(state: GameState, now: Long, onReset: () -> Unit) {
    SectionCard(title = "Health ${state.health}/100", subtitle = mood(state.health)) {
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
                "Locked out: ${(lockoutMillis + 999) / 1000} s left",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (state.isDead) {
            Text("Your tree died. Reset to start growing again.")
            Button(onClick = onReset) { Text("Reset tree") }
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (!accessGranted) "Notification access is off, so taps aren't detected."
                else "The notification listener isn't connected yet.",
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
