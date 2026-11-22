package com.codejam.taptation.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Small status pill: green when [good], warm when something needs attention. */
@Composable
fun StatusPill(text: String, good: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50),
        color = if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onTertiaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun BackLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("←  $label") }
}

/** A row of mutually exclusive choices, e.g. "Last 7 days" / "Last 30 days". */
@Composable
fun SegmentedChoice(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(Modifier.weight(1f).heightIn(min = 48.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** A selectable card with a radio button, a title and one line of explanation. */
@Composable
fun RadioOption(selected: Boolean, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/**
 * Names or words the user manages (people who always get through, urgent words…). Removing needs
 * a clearly labelled Remove button and can be undone for a few seconds.
 */
@Composable
fun TermListCard(title: String, subtitle: String, note: String?, fieldLabel: String, emptyText: String,
    values: Set<String>, onChange: (Set<String>) -> Unit,
) {
    var input by rememberSaveable(title) { mutableStateOf("") }
    var removed by remember { mutableStateOf<String?>(null) }
    val term = input.trim()
    LaunchedEffect(removed) { if (removed != null) { delay(6000); removed = null } }
    SectionCard(title, subtitle) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(fieldLabel) },
                singleLine = true, modifier = Modifier.weight(1f))
            Button(enabled = term.isNotEmpty() && values.none { it.equals(term, ignoreCase = true) },
                onClick = { onChange(values + term); input = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add") }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (values.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        values.sortedBy { it.lowercase() }.forEach { value ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onChange(values - value); removed = value },
                    modifier = Modifier.semantics { contentDescription = "Remove $value" }) {
                    Text("✕  Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        removed?.let { gone ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Removed “$gone”", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onChange(values + gone); removed = null }) {
                        Text("Undo", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                }
            }
        }
    }
}
