package com.kinzer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class PiPulseExtendedColors(
    val blocked: Color,
    val blockedContainer: Color,
    val onBlockedContainer: Color,
    val permitted: Color,
    val permittedContainer: Color,
    val onPermittedContainer: Color,
    val cached: Color,
    val warning: Color,
    val warningContainer: Color,
    val chartAccent: Color
)

private val DarkExtendedColors = PiPulseExtendedColors(
    blocked = CoralBlocked,
    blockedContainer = CoralContainerDark,
    onBlockedContainer = CoralOnContainerDark,
    permitted = EmeraldSignal,
    permittedContainer = EmeraldContainerDark,
    onPermittedContainer = EmeraldOnContainerDark,
    cached = CyanPulse,
    warning = AmberPause,
    warningContainer = AmberContainerDark,
    chartAccent = VioletMetric
)

val LocalPiPulseColors = staticCompositionLocalOf { DarkExtendedColors }

private val PiPulseDarkColorScheme = darkColorScheme(
    primary = EmeraldSignal,
    onPrimary = Color(0xFF022C22),
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = EmeraldOnContainerDark,
    secondary = CyanPulse,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = CyanContainerDark,
    onSecondaryContainer = CyanOnContainerDark,
    tertiary = AmberPause,
    onTertiary = Color(0xFF451A03),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = AmberOnContainerDark,
    error = CoralBlocked,
    onError = Color(0xFFFFFFFF),
    errorContainer = CoralContainerDark,
    onErrorContainer = CoralOnContainerDark,
    background = ObsidianCanvas,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    surfaceContainerHighest = Color(0xFF283854),
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineSubtle
)

val PiPulseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PiPulseTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalPiPulseColors provides DarkExtendedColors) {
        MaterialTheme(
            colorScheme = PiPulseDarkColorScheme,
            typography = PiPulseTypography,
            shapes = PiPulseShapes,
            content = content
        )
    }
}

object PiPulseThemeTokens {
    val extended: PiPulseExtendedColors
        @Composable get() = LocalPiPulseColors.current
}
