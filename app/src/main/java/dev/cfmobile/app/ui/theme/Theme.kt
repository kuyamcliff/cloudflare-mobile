package dev.cfmobile.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = InkLight,
    onPrimary = Color.White,
    // Only FABs use these roles: they read as primary actions, so they get the ink fill.
    primaryContainer = InkLight,
    onPrimaryContainer = Color.White,
    secondary = CfOrangeDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6D2),
    onSecondaryContainer = Color(0xFF5A2600),
    tertiary = StatusColors.info,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE8FF),
    onTertiaryContainer = Color(0xFF0B2D6B),
    background = CanvasLight,
    onBackground = InkLight,
    surface = CanvasLight,
    onSurface = InkLight,
    surfaceVariant = Container1Light,
    onSurfaceVariant = MutedLight,
    surfaceTint = Color.Transparent,
    surfaceBright = CardLight,
    surfaceDim = Container2Light,
    surfaceContainerLowest = CardLight,
    surfaceContainerLow = CardLight,
    surfaceContainer = Container1Light,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = Container2Light,
    inverseSurface = Color(0xFF242428),
    inverseOnSurface = Color(0xFFF2F2F0),
    inversePrimary = InkDark,
    outline = LineLight,
    outlineVariant = HairlineLight,
    error = StatusRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF6B0F12),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = InkDark,
    onPrimary = Color(0xFF111113),
    primaryContainer = InkDark,
    onPrimaryContainer = Color(0xFF111113),
    secondary = CfOrange,
    onSecondary = Color(0xFF1C0C00),
    secondaryContainer = Color(0xFF3B2410),
    onSecondaryContainer = Color(0xFFFFD8B8),
    tertiary = Color(0xFF86AEFF),
    onTertiary = Color(0xFF0B1F48),
    tertiaryContainer = Color(0xFF1B2D52),
    onTertiaryContainer = Color(0xFFDCE8FF),
    background = CanvasDark,
    onBackground = InkDark,
    surface = CanvasDark,
    onSurface = InkDark,
    surfaceVariant = Container1Dark,
    onSurfaceVariant = MutedDark,
    surfaceTint = Color.Transparent,
    surfaceBright = Container2Dark,
    surfaceDim = CanvasDark,
    surfaceContainerLowest = Color(0xFF09090A),
    surfaceContainerLow = CardDark,
    surfaceContainer = Container1Dark,
    surfaceContainerHigh = Container2Dark,
    surfaceContainerHighest = Container3Dark,
    inverseSurface = InkDark,
    inverseOnSurface = Color(0xFF17171A),
    inversePrimary = InkLight,
    outline = LineDark,
    outlineVariant = HairlineDark,
    error = Color(0xFFFF6369),
    onError = Color(0xFF2A0405),
    errorContainer = Color(0xFF4A1416),
    onErrorContainer = Color(0xFFFFDAD9),
    scrim = Color.Black
)

val CfShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Colors the Material scheme has no role for. */
data class CfExtraColors(
    /** Grouped-list cards; one step lifted from the canvas. */
    val card: Color,
    val accent: Color,
    val accentSoft: Color,
    val isDark: Boolean
)

val LocalCfColors = staticCompositionLocalOf {
    CfExtraColors(card = CardLight, accent = CfOrange, accentSoft = Color(0xFFFFE6D2), isDark = false)
}

object CfTheme {
    val colors: CfExtraColors @Composable get() = LocalCfColors.current
}

@Composable
fun CfMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) {
        CfExtraColors(card = CardDark, accent = CfOrange, accentSoft = Color(0xFF3B2410), isDark = true)
    } else {
        CfExtraColors(card = CardLight, accent = CfOrange, accentSoft = Color(0xFFFFE6D2), isDark = false)
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // The window draws edge to edge and the system bars stay transparent, so only the
            // icon contrast needs setting - window.statusBarColor is deprecated and a no-op
            // from Android 15 on.
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalCfColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CfTypography,
            shapes = CfShapes,
            content = content
        )
    }
}
