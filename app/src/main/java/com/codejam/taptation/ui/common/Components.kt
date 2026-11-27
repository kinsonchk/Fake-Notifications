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
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.codejam.taptation.ui.theme.ControlShape
import com.codejam.taptation.ui.theme.MetaText
import com.codejam.taptation.ui.theme.Taptation

/** Small status tag: neutral when [good], the notice tone when something needs attention. */
@Composable
fun StatusPill(text: String, good: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(6.dp),
        color = if (good) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (good) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onTertiaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

/** Back to the parent page: a chevron and the parent's name. Android Back does the same. */
@Composable
fun BackLink(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(start = 0.dp, end = 12.dp),
        modifier = Modifier.semantics { contentDescription = "Back to $label" },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
        LineIcon(com.codejam.taptation.R.drawable.ic_chevron_left, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The monitoring bobber: the top half is the catch colour. It floats gently only while [live]
 * (Taptation is watching) and motion is on; otherwise it sits still.
 */
@Composable
fun Bobber(live: Boolean, modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val lineColor = Taptation.palette.muted
    val top = Taptation.palette.catch
    val bottom = MaterialTheme.colorScheme.onSurface
    val offset = if (live && motion) {
        val transition = rememberInfiniteTransition(label = "Bobber")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = EaseOutQuint), RepeatMode.Reverse), label = "Bob").value
    } else 0f
    Canvas(modifier.size(width = 12.dp, height = 18.dp).semantics { contentDescription = if (live) "Watching" else "Not watching" }) {
        val shift = offset * 2.dp.toPx()
        val r = 5.dp.toPx(); val cx = size.width / 2; val cy = 12.dp.toPx() + shift
        drawLine(lineColor, Offset(cx, 0f), Offset(cx, cy - r), strokeWidth = 1.2.dp.toPx())
        drawArc(top, 180f, 180f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
        drawArc(bottom, 0f, 180f, true, Offset(cx - r, cy - r), Size(r * 2, r * 2))
    }
}

/**
 * A row of mutually exclusive choices, e.g. "7 days" / "30 days". [captions] adds a mono second line
 * under each option (difficulty percentages). The selected option is a raised card on a quiet track.
 */
@Composable
fun SegmentedChoice(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    captions: List<String>? = null, descriptions: List<String>? = null) {
    Surface(modifier.fillMaxWidth(), shape = ControlShape, color = Taptation.palette.raised) {
        Row(Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(Modifier.weight(1f).heightIn(min = if (captions != null) 56.dp else 44.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .then(descriptions?.getOrNull(index)?.let { d -> Modifier.semantics { contentDescription = d } } ?: Modifier),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else androidx.compose.ui.graphics.Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                    contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                        captions?.getOrNull(index)?.let {
                            Text(it, style = MetaText, color = if (selected) Taptation.palette.catchText else Taptation.palette.muted)
                        }
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
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Taptation.palette.raised else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp,
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
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        removed?.let { gone ->
            Surface(shape = ControlShape, color = MaterialTheme.colorScheme.inverseSurface,
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
