package com.codejam.taptation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Primary is the ink (dark text on light, off-white on dark): buttons, switches and selection stay
// neutral. The bobber orange lives in tertiary and in [TaptationPalette.catch].
private val LightColors = lightColorScheme(
    primary = LightText, onPrimary = Color.White, primaryContainer = LightRaised, onPrimaryContainer = LightText,
    secondary = LightText, onSecondary = Color.White, secondaryContainer = LightRaised, onSecondaryContainer = LightText,
    tertiary = BobberLight, onTertiary = Color.White, tertiaryContainer = Color(0xFFFBEDE6), onTertiaryContainer = LightText,
    background = LightBackground, onBackground = LightText, surface = LightBackground, onSurface = LightText,
    surfaceVariant = LightRaised, onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightCard, surfaceContainerLow = LightCard,
    surfaceContainer = LightCard, surfaceContainerHigh = LightRaised, surfaceContainerHighest = LightRaised,
    outline = LightTextMuted, outlineVariant = LightLine,
    error = NegativeLight, onError = Color.White, errorContainer = Color(0xFFFDECEC), onErrorContainer = Color(0xFF7F1D1D),
    inverseSurface = LightText, inverseOnSurface = LightBackground, inversePrimary = BobberDarkText,
)
private val DarkColors = darkColorScheme(
    primary = DarkText, onPrimary = DarkBackground, primaryContainer = DarkRaised, onPrimaryContainer = DarkText,
    secondary = DarkText, onSecondary = DarkBackground, secondaryContainer = DarkRaised, onSecondaryContainer = DarkText,
    tertiary = BobberDark, onTertiary = Color(0xFF1A0B05), tertiaryContainer = Color(0xFF22181A), onTertiaryContainer = DarkText,
    background = DarkBackground, onBackground = DarkText, surface = DarkBackground, onSurface = DarkText,
    surfaceVariant = DarkRaised, onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkCard, surfaceContainerLow = DarkCard,
    surfaceContainer = DarkCard, surfaceContainerHigh = DarkRaised, surfaceContainerHighest = DarkRaised,
    outline = DarkTextMuted, outlineVariant = DarkLine,
    error = NegativeDark, onError = DarkBackground, errorContainer = Color(0xFF2A1416), onErrorContainer = DarkText,
    inverseSurface = DarkText, inverseOnSurface = DarkBackground, inversePrimary = BobberLightText,
)

/** Roles Material has no slot for. Read with [Taptation.palette]. */
@Immutable
data class TaptationPalette(
    val dark: Boolean,
    /** Bobber orange: catches, damage, the selected chart value. */
    val catch: Color,
    /** Bobber orange dark enough for text on the page background. */
    val catchText: Color,
    /** Health bars, matching the meadow. */
    val health: Color,
    /** Raised surface: inputs, segmented tracks, selected rows. */
    val raised: Color,
    /** Muted text and icons: meta, captions, chevrons. */
    val muted: Color,
)

private val DarkPalette = TaptationPalette(true, BobberDark, BobberDarkText, HealthDark, DarkRaised, DarkTextMuted)
private val LightPalette = TaptationPalette(false, BobberLight, BobberLightText, HealthLight, LightRaised, LightTextMuted)
private val LocalPalette = staticCompositionLocalOf { DarkPalette }

object Taptation {
    val palette: TaptationPalette @Composable get() = LocalPalette.current
}

/** Controls 10, cards 16, dialogs 24; nested shapes subtract their padding. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp),
)

/** Every filled or outlined button: 12dp corners at 48dp height. */
val ButtonShape = RoundedCornerShape(12.dp)
val CardShape = RoundedCornerShape(16.dp)
val ControlShape = RoundedCornerShape(10.dp)

/** A stable product palette: wallpaper-derived colors must not replace it. */
@Composable
fun TaptationTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography, shapes = AppShapes, content = content)
    }
}
