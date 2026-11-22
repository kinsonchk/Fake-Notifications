package com.codejam.taptation.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codejam.taptation.R
import com.codejam.taptation.logic.CatchDetails

/**
 * Choosing a destination acknowledges an already-applied catch; it never applies damage.
 * [details] and [appLabel] explain why it counted and what it cost; both are optional because a
 * manual force-catch has no notification behind it. [onCorrect] adds "This should be important".
 */
@Composable
fun CatchChoiceDialog(details: CatchDetails?, appLabel: String?, onTaptation: () -> Unit, onOriginal: () -> Unit,
    onCorrect: (() -> Unit)? = null,
) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            var fall by remember { mutableFloatStateOf(0f) }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.caught_overlay_label), Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (details != null) LeafBadge(fall)
                }
                Text(stringResource(R.string.caught_title), style = MaterialTheme.typography.headlineLarge)
                if (details != null && appLabel != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(TapExplanations.catchReason(details.rule, appLabel, details.channel),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    Text(stringResource(R.string.caught_message), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (details != null && details.damage > 0) HealthLossBlock(details.healthBefore, details.healthAfter) { fall = it }
                Button(onClick = onTaptation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.caught_our_app)) }
                OutlinedButton(onClick = onOriginal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) {
                    Text(if (appLabel != null) stringResource(R.string.caught_original_app, appLabel)
                        else stringResource(R.string.caught_original_fallback))
                }
                if (onCorrect != null) TextButton(onClick = onCorrect, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.caught_correct))
                }
            }
        }
    }
}
