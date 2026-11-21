package com.codejam.taptation.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.Classification
import com.codejam.taptation.logic.TapRecord

/** Small coloured label: Important / Bait / Unsure. */
@Composable
fun ClassificationBadge(classification: Classification) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (classification) {
        Classification.Important -> colors.primaryContainer to colors.onPrimaryContainer
        Classification.Bait -> colors.errorContainer to colors.onErrorContainer
        Classification.Unknown -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    Badge(TapExplanations.classification(classification), background, foreground)
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** One processed tap: what it was judged as, why, and what happened. */
@Composable
fun TapRecordRow(record: TapRecord) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClassificationBadge(record.classification)
                Text(record.packageName, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(formatClock(record.timestampMillis), style = MaterialTheme.typography.bodySmall)
            }
            Text("Why: ${TapExplanations.rule(record.rule)}", style = MaterialTheme.typography.bodyMedium)
            val age = record.ageMillis?.let { " • tapped ${formatDuration(it)} after posting" }.orEmpty()
            Text(
                "${TapExplanations.outcome(record.outcome, record.reason, record.damage)} • " +
                    "health ${record.healthBefore} → ${record.healthAfter}$age",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
