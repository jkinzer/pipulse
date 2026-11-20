package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    val cachedContainer: Color,
    val onCachedContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val chartAccent: Color,
    val subtleBorder: Color
)

private val DarkExtendedColors = PiPulseExtendedColors(
    blocked = CoralBlocked,
    blockedContainer = CoralContainerDark,
    onBlockedContainer = CoralOnContainerDark,
    permitted = EmeraldSignal,
    permittedContainer = EmeraldContainerDark,
    onPermittedContainer = EmeraldOnContainerDark,
    cached = CyanPulse,
    cachedContainer = CyanContainerDark,
    onCachedContainer = CyanOnContainerDark,
    warning = AmberPause,
    warningContainer = AmberContainerDark,
    onWarningContainer = AmberOnContainerDark,
    chartAccent = VioletMetric,
    subtleBorder = ObsidianOutlineSubtle
)

private val LightExtendedColors = PiPulseExtendedColors(
    blocked = CoralErrorLight,
    blockedContainer = CoralContainerLight,
    onBlockedContainer = CoralOnContainerLight,
    permitted = EmeraldPrimaryLight,
    permittedContainer = EmeraldContainerLight,
    onPermittedContainer = EmeraldOnContainerLight,
    cached = CyanSecondaryLight,
    cachedContainer = CyanContainerLight,
    onCachedContainer = CyanOnContainerLight,
    warning = AmberPause,
    warningContainer = Color(0xFFFEF3C7),
    onWarningContainer = Color(0xFF78350F),
    chartAccent = VioletMetric,
    subtleBorder = SlateOutlineSubtleLight
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

private val PiPulseLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainerLight,
    onPrimaryContainer = EmeraldOnContainerLight,
    secondary = CyanSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = CyanContainerLight,
    onSecondaryContainer = CyanOnContainerLight,
    tertiary = AmberPause,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CoralErrorLight,
    onError = Color.White,
    errorContainer = CoralContainerLight,
    onErrorContainer = CoralOnContainerLight,
    background = SlateCanvasLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateSurfaceHighLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SlateCanvasLight,
    surfaceContainer = SlateSurfaceHighLight,
    surfaceContainerHigh = SlateSurfaceHighestLight,
    surfaceContainerHighest = Color(0xFFD2DDF0),
    outline = SlateOutlineLight,
    outlineVariant = SlateOutlineSubtleLight
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
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PiPulseDarkColorScheme else PiPulseLightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalPiPulseColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
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
