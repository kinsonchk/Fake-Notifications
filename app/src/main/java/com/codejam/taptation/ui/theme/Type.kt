package com.codejam.taptation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.codejam.taptation.R

/** IBM Plex Sans (variable, bundled under res/font, SIL OFL; see docs/licenses). */
@OptIn(ExperimentalTextApi::class)
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

/** IBM Plex Mono for times, counts, percentages and other meta. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private fun sans(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = PlexSans, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp, letterSpacing = tracking.sp)

/** Small mono text: times, counts, "5 / 8", axis labels. Tabular by design. */
val MetaText = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** Hierarchy comes from size and colour; weights stay at 400 and 500. Negative tracking only on large text. */
val Typography = Typography(
    displayMedium = sans(52, 56, tracking = -1f),
    displaySmall = sans(34, 40, FontWeight.Medium, -0.4f),
    headlineLarge = sans(28, 34, FontWeight.Medium, -0.3f),
    headlineMedium = sans(30, 36),
    headlineSmall = sans(24, 30, FontWeight.Medium),
    titleLarge = sans(20, 26, FontWeight.Medium),
    titleMedium = sans(16, 22, FontWeight.Medium),
    titleSmall = sans(15, 20, FontWeight.Medium),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(13, 18),
    labelLarge = sans(14, 20, FontWeight.Medium),
    labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(12, 16, FontWeight.Medium),
)
