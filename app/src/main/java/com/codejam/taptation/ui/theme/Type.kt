package com.codejam.taptation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun serif(size: Int, height: Int) = TextStyle(fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = height.sp, letterSpacing = (-0.6).sp)
private fun sans(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = height.sp)

val Typography = Typography(
    displaySmall = serif(34, 40), headlineLarge = serif(34, 40), headlineMedium = serif(30, 36),
    headlineSmall = serif(26, 32), titleLarge = sans(20, 27, FontWeight.Medium),
    titleMedium = sans(18, 25, FontWeight.Medium), titleSmall = sans(14, 21, FontWeight.Medium),
    bodyLarge = sans(16, 24), bodyMedium = sans(14, 21), bodySmall = sans(12, 18),
    labelLarge = sans(14, 20, FontWeight.Medium), labelMedium = sans(12, 16, FontWeight.Medium),
    labelSmall = sans(11, 15, FontWeight.Medium),
)
