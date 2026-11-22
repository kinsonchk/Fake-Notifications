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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.codejam.taptation.R
import com.codejam.taptation.logic.CatchDetails
import com.codejam.taptation.logic.meadow.MeadowCameo
import com.codejam.taptation.ui.caught.FishingCatchScene

/**
 * Choosing a destination acknowledges an already-applied catch; it never applies damage.
 * [details] and [appLabel] explain why it counted and what it cost; both are optional because a
 * manual force-catch has no notification behind it. [onCorrect] adds "This should be important".
 */
@Composable
fun CatchChoiceDialog(details: CatchDetails?, appLabel: String?, onTaptation: () -> Unit, onOriginal: () -> Unit,
    onCorrect: (() -> Unit)? = null, continueExtraDamage: Int = 0, animateDamage: Boolean = true,
    onLater: () -> Unit = {},
    cameo: MeadowCameo = MeadowCameo.Fish,
    sceneKey: String = "preview",
) {
    Dialog(onDismissRequest = onLater) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.caught_title), style = MaterialTheme.typography.headlineLarge)
                if (details != null && appLabel != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(TapExplanations.catchReason(details.rule, appLabel, details.channel),
                            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    }
                } else {
                    Text(stringResource(R.string.caught_message_no_extra), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FishingCatchScene(cameo,appLabel,sceneKey,animateDamage)
                if (details != null && details.damage > 0) HealthLossBlock(details.healthBefore, details.healthAfter, animateDamage, compact = true)
                Text(stringResource(R.string.caught_destination_prompt),style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onTaptation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.caught_our_app)) }
                val destination = appLabel ?: stringResource(R.string.caught_original_destination)
                val continueLabel = if (continueExtraDamage > 0) stringResource(R.string.caught_original_app_with_penalty, destination, continueExtraDamage)
                    else stringResource(R.string.caught_original_app, destination)
                OutlinedButton(onClick = onOriginal, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription=continueLabel },
                    shape = RoundedCornerShape(16.dp)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.caught_original_app,destination))
                        if(continueExtraDamage>0)Text(stringResource(R.string.caught_continue_cost,continueExtraDamage),
                            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if (onCorrect != null) TextButton(onClick = onCorrect, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.caught_correct),style=MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = onLater, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.caught_later),style=MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
