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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.CorrectionRequest

/**
 * "This should be important" after a catch: confirms the backend's correction for the app in
 * [request] (GameRepository.markAppImportant). It only changes future notifications; the catch stays
 * in the history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSheet(request: CorrectionRequest, onDone: () -> Unit) {
    val repository = rememberGameRepository()
    val app = request.appLabel
    var saving by remember { mutableStateOf(false) }
    val error by repository.error.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { if (!saving) onDone() }) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Treat this as important?", style = MaterialTheme.typography.headlineSmall)
            Text("Set notifications from $app to Always important?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Applies to future notifications. This catch stays in your history.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !saving, onClick = {
                saving = true
                repository.markAppImportant(request.packageName).whenComplete { _, failure ->
                    scope.launch {
                        saving = false
                        if (failure == null) onDone()
                    }
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text(if (saving) "Saving…" else "Save rule") }
            TextButton(enabled = !saving, onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel") }
        }
    }
}
