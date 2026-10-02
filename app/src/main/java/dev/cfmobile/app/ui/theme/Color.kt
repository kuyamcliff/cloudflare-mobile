package dev.cfmobile.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette. Graphite ink carries the interface (text, primary buttons, selected controls) so
 * every label meets WCAG AA on its background; Cloudflare orange is an accent only - focus,
 * the command bar, the selected destination and progress - never body text on white.
 */

val CfOrange = Color(0xFFF6821F)
val CfOrangeDeep = Color(0xFFC2560A)

// Light
val InkLight = Color(0xFF17171A)
val CanvasLight = Color(0xFFF6F6F4)
val CardLight = Color(0xFFFFFFFF)
val Container1Light = Color(0xFFF0F0ED)
val Container2Light = Color(0xFFEAEAE6)
val Container3Light = Color(0xFFE3E3DE)
val MutedLight = Color(0xFF63636B)
val LineLight = Color(0xFFD8D8D2)
val HairlineLight = Color(0xFFE6E6E1)

// Dark
val InkDark = Color(0xFFF1F1EF)
val CanvasDark = Color(0xFF0D0D0F)
val CardDark = Color(0xFF17171A)
val Container1Dark = Color(0xFF1C1C20)
val Container2Dark = Color(0xFF232327)
val Container3Dark = Color(0xFF2A2A2F)
val MutedDark = Color(0xFFA3A3AB)
val LineDark = Color(0xFF3A3A40)
val HairlineDark = Color(0xFF26262B)

val StatusGreen = Color(0xFF1E9E57)
val StatusRed = Color(0xFFDB3B40)
val StatusAmber = Color(0xFFC77A0A)

/** Semantic status colors (spec 82, 243). Orange stays the brand accent, not a status.
 *  Mid-tone values keep at least 3:1 contrast on both the light and dark cards, and every use
 *  pairs the color with a text label. */
object StatusColors {
    val success = StatusGreen
    val warning = StatusAmber
    val error = Color(0xFFE5484D)
    val info = Color(0xFF3A7BEA)
}
