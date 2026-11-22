package com.codejam.taptation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Forest, onPrimary = Color.White, primaryContainer = Sage, onPrimaryContainer = Ink,
    secondary = Forest, onSecondary = Color.White, secondaryContainer = Sage, onSecondaryContainer = Ink,
    tertiary = Earth, tertiaryContainer = Warm, onTertiaryContainer = Ink,
    background = Cream, onBackground = Ink, surface = Cream, onSurface = Ink,
    surfaceVariant = Color.White, onSurfaceVariant = MutedGreen,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = MutedGreen, outlineVariant = BorderGreen,
    error = Color(0xFFA23D3D), errorContainer = Warm, onErrorContainer = Color(0xFF723B2C),
)
private val DarkColors = darkColorScheme(
    primary = NightGreen, onPrimary = Color(0xFF173422), primaryContainer = NightSage, onPrimaryContainer = NightInk,
    secondary = NightGreen, onSecondary = Night, secondaryContainer = NightSage, onSecondaryContainer = NightInk,
    tertiary = Color(0xFFC3A575), tertiaryContainer = Color(0xFF403226), onTertiaryContainer = NightInk,
    background = Night, onBackground = NightInk, surface = Night, onSurface = NightInk,
    surfaceVariant = NightSurface, onSurfaceVariant = NightMuted,
    surfaceContainerLowest = NightSurface, surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface, surfaceContainerHigh = NightSurface, surfaceContainerHighest = NightSurface,
    outline = NightMuted, outlineVariant = NightBorder,
    error = Color(0xFFEFAAAA), errorContainer = Color(0xFF403226), onErrorContainer = Color(0xFFF0D7C4),
)
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** A stable product palette: wallpaper-derived colors must not replace the mockup's greens. */
@Composable
fun TaptationTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, shapes = AppShapes, content = content)
}
