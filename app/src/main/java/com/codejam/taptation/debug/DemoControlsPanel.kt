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
                    "Not connected yet. These controls need Person 2's health and consequence logic.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                ConnectedDemoControls(controls)
            }
        }
    }
}

@Composable
private fun ConnectedDemoControls(controls: DemoControls) {
    val state by controls.state.collectAsStateWithLifecycle()
    var healthInput by remember { mutableStateOf(state.health.toString()) }
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

