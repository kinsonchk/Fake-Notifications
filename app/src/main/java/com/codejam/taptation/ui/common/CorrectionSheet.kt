package com.codejam.taptation.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.RuleBehavior
import com.codejam.taptation.logic.RuleTarget
import com.codejam.taptation.service.CorrectionRequest

/**
 * "This should be important" after a catch: pick how far the rule reaches (this notification type,
 * or the whole app). It only changes future notifications; the catch stays in the history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(request: CorrectionRequest, onDone: () -> Unit) {
    val repository = rememberGameRepository()
    val labels = rememberAppLabels()
    val app = labels.label(request.packageName)
    val channel = request.channel
    var wholeApp by remember { mutableStateOf(channel == null) }
    ModalBottomSheet(onDismissRequest = onDone) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Treat this as important?", style = MaterialTheme.typography.headlineSmall)
            Text("Pick how far the rule should reach.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (channel != null) RadioOption(!wholeApp, "Only “$channel”", "Other $app notifications still count.") { wholeApp = false }
            RadioOption(wholeApp, "Everything from $app", "Adds $app to your always-safe apps.") { wholeApp = true }
            Text("Applies to future notifications. This catch stays in your history.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                repository.applyRule(RuleTarget(request.packageName, if (wholeApp) null else channel), RuleBehavior.AlwaysSafe)
                onDone()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp)) { Text("Save rule") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}
