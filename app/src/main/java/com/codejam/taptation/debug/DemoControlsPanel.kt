package com.codejam.taptation.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DemoControlsPanel(controls: DemoControls?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Demo controls", style = MaterialTheme.typography.titleMedium)
            if (controls == null) {
                Text(
                    "Not connected yet. These controls need the game repository.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ConnectedDemoControls(controls)
            }
        }
    }
}

@Composable
private fun RecoveryIntervalChoice(minutes: Int, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = if (minutes == 60) "60 min · normal" else "$minutes min"
    if (selected) {
        Button(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(label) }
    }
}

@Composable
private fun ConnectedDemoControls(controls: DemoControls) {
    val state by controls.state.collectAsStateWithLifecycle()
    // Re-keyed on health so the field follows catches, recovery and resets.
    var healthInput by remember(state.health) { mutableStateOf(state.health.toString()) }
    var damageInput by remember(state.catchDamage) { mutableStateOf(state.catchDamage.toString()) }
    var lockoutInput by remember(state.lockoutSeconds) { mutableStateOf(state.lockoutSeconds.toString()) }
    var confirmReset by remember { mutableStateOf(false) }

    Text("Health: ${state.health}   Catch chance: ${state.catchProbabilityPercent}%   Catches: ${state.catches}")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { controls.setCatchProbability(0) }) { Text("Set 0%") }
        Button(onClick = { controls.setCatchProbability(100) }) { Text("Set 100%") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = healthInput,
            onValueChange = { input -> if (input.all(Char::isDigit)) healthInput = input },
            label = { Text("Health") },
            modifier = Modifier.weight(1f).testTag("health-input"),
            singleLine = true,
        )
        Button(
            onClick = { healthInput.toIntOrNull()?.let(controls::setHealth) },
            enabled = healthInput.toIntOrNull() != null,
        ) { Text("Set health") }
    }
    Text("Damage per catch: -${state.catchDamage}",
        style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = damageInput,
            onValueChange = { input -> if (input.length <= 3 && input.all(Char::isDigit)) damageInput = input },
            label = { Text("Damage (1-100)") },
            modifier = Modifier.weight(1f).testTag("damage-input"),
            singleLine = true,
        )
        Button(
            onClick = { damageInput.toIntOrNull()?.let(controls::setCatchDamage) },
            enabled = damageInput.toIntOrNull() in 1..100,
        ) { Text("Set damage") }
    }
    OutlinedButton(onClick = { controls.setCatchDamage(null) }, modifier = Modifier.fillMaxWidth()) {
        Text("Default damage")
    }
    Text("Catch lockout: ${state.lockoutSeconds} s", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lockoutInput,
            onValueChange = { input -> if (input.length <= 4 && input.all(Char::isDigit)) lockoutInput = input },
            label = { Text("Seconds (1-3600)") },
            modifier = Modifier.weight(1f).testTag("lockout-input"),
            singleLine = true,
        )
        Button(
            onClick = { lockoutInput.toIntOrNull()?.let(controls::setLockoutSeconds) },
            enabled = lockoutInput.toIntOrNull() in 1..3600,
        ) { Text("Set lockout") }
    }
    OutlinedButton(onClick = { controls.setLockoutSeconds(null) }, modifier = Modifier.fillMaxWidth()) {
        Text("Default lockout")
    }
    Text("Applies to the next catch; a running lockout keeps its end time.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Health recovery: +${state.recoveryAmount} every ${state.recoveryIntervalMinutes} clean minutes",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Debug recovery interval", style = MaterialTheme.typography.titleSmall)
    listOf(1, 5, 15, 60).chunked(2).forEach { rowIntervals ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowIntervals.forEach { minutes ->
                val selected = state.recoveryIntervalMinutes == minutes
                RecoveryIntervalChoice(minutes, selected, Modifier.weight(1f)) {
                    controls.setRecoveryIntervalForDebug(if (minutes == 60) null else minutes)
                }
            }
        }
    }
    Button(onClick = controls::forceCatch, modifier = Modifier.fillMaxWidth()) {
        Text("Force catch")
    }
    Button(
        onClick = { controls.advanceCleanTime(60) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Fast-forward 1 clean hour") }
    OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Reset demo data")
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset demo data?") },
            text = { Text("Health, catch history, and stats will return to their starting state.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    controls.resetDemoData()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

