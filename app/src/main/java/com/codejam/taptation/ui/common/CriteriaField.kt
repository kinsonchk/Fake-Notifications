package com.codejam.taptation.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.GameConfig
import kotlinx.coroutines.delay

/** Shared, autosaving input for the user's important/non-essential AI criteria. */
@Composable
fun CriteriaField(label: String, placeholder: String, saved: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(label) { mutableStateOf(saved) }
    val latestText by rememberUpdatedState(text)
    val latestSaved by rememberUpdatedState(saved)
    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedEffect(text) {
        if (text != saved) {
            delay(500)
            onSave(text)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (latestText != latestSaved) latestOnSave(latestText)
        }
    }
    Text(label, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.take(GameConfig.MAX_CRITERIA_LENGTH) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        minLines = 3,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(10.dp),
    )
}
