package com.codejam.taptation.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codejam.taptation.R

/** Choosing a destination settles an already-applied catch; only continuing adds its saved extra damage. */
@Composable
fun CatchChoiceDialog(continueExtraDamage: Int, onTaptation: () -> Unit, onOriginal: () -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.caught_overlay_label), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(stringResource(R.string.caught_title), style = MaterialTheme.typography.headlineLarge)
                Text(if (continueExtraDamage > 0) stringResource(R.string.caught_message, continueExtraDamage)
                    else stringResource(R.string.caught_message_no_extra), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onTaptation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.caught_our_app)) }
                OutlinedButton(onClick = onOriginal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (continueExtraDamage > 0) stringResource(R.string.caught_original_app_with_penalty, continueExtraDamage)
                        else stringResource(R.string.caught_original_app))
                }
            }
        }
    }
}
