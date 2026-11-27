package com.codejam.taptation.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codejam.taptation.logic.AppRule
import com.codejam.taptation.logic.InstalledApp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role

/** The user-facing name of an app rule. The UI says "distracting", never "bait". */
fun AppRule.label(): String = when (this) {
    AppRule.AlwaysBait -> "Always distracting"
    AppRule.AlwaysImportant -> "Always important"
    AppRule.LetUsDecide -> "Let us decide"
}

/** One-line effect of a rule, shown in [RuleLegend]. */
fun AppRule.effect(): String = when (this) {
    AppRule.AlwaysImportant -> "Never caught. Use it for family, work, rides and banking."
    AppRule.LetUsDecide -> "AI checks each notification. A distracting one may catch you."
    AppRule.AlwaysBait -> "Every tap is caught, with no AI check or chance roll."
}

/** Colour of a rule in illustrations (onboarding): catch orange, meadow green, or neutral. */
@Composable
fun AppRule.color(): Color = when (this) {
    AppRule.AlwaysBait -> com.codejam.taptation.ui.theme.Taptation.palette.catch
    AppRule.AlwaysImportant -> com.codejam.taptation.ui.theme.Taptation.palette.health
    AppRule.LetUsDecide -> MaterialTheme.colorScheme.outline
}

private val LEGEND_ORDER = listOf(AppRule.AlwaysImportant, AppRule.LetUsDecide, AppRule.AlwaysBait)

/** The three rules with their effect, in safe-to-strict order. */
@Composable
fun RuleLegend(modifier: Modifier = Modifier) {
    ListGroup(modifier) {
        LEGEND_ORDER.forEachIndexed { index, rule ->
            if (index > 0) RowDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rule.label(), style = MaterialTheme.typography.titleSmall)
                    if (rule == AppRule.LetUsDecide) StatusPill("Default", good = true)
                }
                Text(rule.effect(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One app with its rule picker, used by onboarding. Settings uses [AppRuleListRow] and [AppRuleSheet]. */
@Composable
fun AppRuleRow(app: InstalledApp, rule: AppRule, labels: AppLabels, enabled: Boolean, onChange: (AppRule) -> Unit) {
    BoxWithConstraints {
    val compact = maxWidth < 480.dp
    Surface(shape = com.codejam.taptation.ui.theme.CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) {
            val appName: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app.packageName, labels, 32.dp)
                    Text(app.label, style = MaterialTheme.typography.titleSmall)
                }
            }
            if (compact) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                appName()
                RuleSelector(app.label, rule, enabled, Modifier.fillMaxWidth(), onChange)
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { appName() }
                RuleSelector(app.label, rule, enabled, Modifier.width(190.dp), onChange)
            }
        }
    }
    }
}

@Composable
private fun RuleSelector(app: String, rule: AppRule, enabled: Boolean, modifier: Modifier, onChange: (AppRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = com.codejam.taptation.ui.theme.ControlShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$app notification rule" }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (enabled) rule.label() else "Saving…", style = MaterialTheme.typography.labelLarge)
                LineIcon(com.codejam.taptation.R.drawable.ic_chevron_down, size = 16.dp)
            }
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            AppRule.entries.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.label(), fontWeight = if (choice == rule) FontWeight.Medium else FontWeight.Normal) },
                    modifier = Modifier.semantics { selected = choice == rule },
                    onClick = { expanded = false; if (choice != rule) onChange(choice) })
            }
        }
    }
}

/** An app in the Settings rule list. Tapping it opens [AppRuleSheet]; [subtitle] names the rule when the list is flat. */
@Composable
fun AppRuleListRow(app: InstalledApp, labels: AppLabels, subtitle: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "${app.label} notification rule" }
        .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIcon(app.packageName, labels, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleSmall)
            val line = if (!enabled) "Saving…" else subtitle
            line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        LineIcon(com.codejam.taptation.R.drawable.ic_chevron_right)
    }
}

/** The three rules for one app, each with its effect. Choosing one saves it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRuleSheet(app: InstalledApp, rule: AppRule, labels: AppLabels, onDismiss: () -> Unit, onChange: (AppRule) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(app.packageName, labels, 32.dp)
                Text(app.label, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            LEGEND_ORDER.forEachIndexed { index, choice ->
                if (index > 0) RowDivider()
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .selectable(selected = choice == rule, role = Role.RadioButton, onClick = { if (choice != rule) onChange(choice); onDismiss() })
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RadioButton(selected = choice == rule, onClick = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(choice.label(), style = MaterialTheme.typography.titleSmall)
                        Text(choice.effect(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
