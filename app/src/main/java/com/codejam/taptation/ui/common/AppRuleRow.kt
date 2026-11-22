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

/** Stripe colour of a rule, shared by [AppRuleRow] and [RuleLegend]. */
@Composable
fun AppRule.color(): Color = when (this) {
    AppRule.AlwaysBait -> MaterialTheme.colorScheme.tertiary
    AppRule.AlwaysImportant -> MaterialTheme.colorScheme.primary
    AppRule.LetUsDecide -> MaterialTheme.colorScheme.outline
}

private val LEGEND_ORDER = listOf(AppRule.AlwaysImportant, AppRule.LetUsDecide, AppRule.AlwaysBait)

/** The three rules with their stripe colour and effect, in safe-to-strict order. */
@Composable
fun RuleLegend(modifier: Modifier = Modifier) {
    Column(modifier) {
        LEGEND_ORDER.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(rule.color(), RoundedCornerShape(3.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(rule.label(), style = MaterialTheme.typography.titleSmall)
                        if (rule == AppRule.LetUsDecide) Surface(color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(50)) {
                            Text("Default", style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    Text(rule.effect(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** One app with its rule picker, as on Settings > Notification rules (also used by onboarding). */
@Composable
fun AppRuleRow(app: InstalledApp, rule: AppRule, labels: AppLabels, enabled: Boolean, onChange: (AppRule) -> Unit) {
    val stripe = rule.color()
    BoxWithConstraints {
    val compact = maxWidth < 480.dp
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
            Box(Modifier.weight(1f).padding(14.dp)) {
                val appName: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(app.packageName, labels, 36.dp)
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (compact) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
}

@Composable
private fun RuleSelector(app: String, rule: AppRule, enabled: Boolean, modifier: Modifier, onChange: (AppRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "$app notification rule" }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (enabled) rule.label() else "Saving…", style = MaterialTheme.typography.labelLarge)
                Text("▾")
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

