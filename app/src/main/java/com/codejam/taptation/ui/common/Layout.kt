package com.codejam.taptation.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.codejam.taptation.R
import com.codejam.taptation.ui.theme.CardShape
import com.codejam.taptation.ui.theme.MetaText
import com.codejam.taptation.ui.theme.Taptation

/** Scrolling tab body: a title, then [content] items, padded around the bottom bar. */
@Composable
fun TabScreen(contentPadding: PaddingValues, title: String,
    subtitle: String? = null, itemSpacing: Dp = 16.dp,
    navigation: (@Composable () -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit) {
    // Top inset as real padding so scrolled content never shows under the status bar.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.testTag("tab-content").widthIn(max = 560.dp).fillMaxSize().padding(
                top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    navigation?.invoke()
                    Text(title, modifier = Modifier.testTag("screen-title"), style = MaterialTheme.typography.headlineLarge)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            content()
        }
        Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.widthIn(max = 560.dp), content = overlay)
        }
    }
}

/** Card with a title and an optional one-line explanation. Use for a block of related controls or text. */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            content()
        }
    }
}

/** A small label above a group: "Recent", "Difficulty". Optional mono [meta] on the right, like a count. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, meta: String? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        meta?.let { Text(it, style = MetaText, color = Taptation.palette.muted) }
    }
}

/** Rows on one card, separated by hairlines. Put [GroupRow]s (or any rows) inside, with [RowDivider] between them. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(content = content)
    }
}

@Composable
fun RowDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

/**
 * One row of a [ListGroup]: optional leading icon, a title, an optional line below, then [trailing]
 * (a value, a switch). With [onClick] the row is tappable and shows a chevron unless [trailing] is set.
 */
@Composable
fun GroupRow(title: String, subtitle: String? = null, @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, value: String? = null,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        icon?.let { LineIcon(it) }
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (trailing != null) trailing() else if (onClick != null) LineIcon(R.drawable.ic_chevron_right)
    }
}

/** A line icon from res/drawable (Lucide set), tinted with a text role. Decorative unless [description] is set. */
@Composable
fun LineIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 18.dp,
    tint: Color = Taptation.palette.muted, description: String? = null) {
    Icon(painterResource(icon), contentDescription = description, modifier = modifier.size(size), tint = tint)
}

/** Label on the left, value on the right, inside a [SectionCard]. */
@Composable
fun LabelValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 12.dp))
    }
}
