package dev.cfmobile.app.ui.theme

import androidx.compose.ui.graphics.Color

val CfOrange = Color(0xFFF6821F)
val CfOrangeDark = Color(0xFFC9660F)

val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEFEFEF)
val LightOnSurface = Color(0xFF1A1A1A)
val LightOnSurfaceVariant = Color(0xFF5C5C5C)
val LightOutline = Color(0xFFDDDDDD)

val DarkBackground = Color(0xFF0F0F10)
val DarkSurface = Color(0xFF19191B)
val DarkSurfaceVariant = Color(0xFF242426)
val DarkOnSurface = Color(0xFFEDEDED)
val DarkOnSurfaceVariant = Color(0xFFA5A5A8)
val DarkOutline = Color(0xFF333335)

val StatusGreen = Color(0xFF16A34A)
val StatusRed = Color(0xFFDC2626)
val StatusAmber = Color(0xFFD97706)

/** Semantic status colors (spec 82, 243). Orange stays the brand accent, not a status. */
object StatusColors {
    // Mid-tone values chosen to keep at least 3:1 contrast on both the light and dark surfaces.
    val success = Color(0xFF1F9D55)
    val warning = Color(0xFFC27C0E)
    val error = Color(0xFFE5484D)
    val info = Color(0xFF3B82F6)
}
