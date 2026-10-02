package dev.cfmobile.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em
)

/** A compact, tool-like scale: big type only for screen titles, everything else tight. */
val CfTypography = Typography(
    displaySmall = style(34, FontWeight.SemiBold, 40, -0.02),
    headlineLarge = style(30, FontWeight.SemiBold, 36, -0.02),
    headlineMedium = style(26, FontWeight.SemiBold, 32, -0.015),
    headlineSmall = style(22, FontWeight.SemiBold, 28, -0.01),
    titleLarge = style(20, FontWeight.SemiBold, 26, -0.005),
    titleMedium = style(16, FontWeight.SemiBold, 22),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 23),
    bodyMedium = style(14, FontWeight.Normal, 20),
    bodySmall = style(13, FontWeight.Normal, 18),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16, 0.01),
    labelSmall = style(11, FontWeight.Medium, 14, 0.02)
)

/** IDs, paths and other values a user may need to compare character by character. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
