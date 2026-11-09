package com.janreins.tonnage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** Every digit takes the same width, so timers and set numbers don't shift as they change. */
private fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val TonnageTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.tabular(),
        displayMedium = base.displayMedium.tabular(),
        displaySmall = base.displaySmall.tabular(),
        headlineLarge = base.headlineLarge.tabular(),
        headlineMedium = base.headlineMedium.tabular(),
        headlineSmall = base.headlineSmall.tabular(),
        titleLarge = base.titleLarge.tabular(),
        titleMedium = base.titleMedium.tabular(),
        titleSmall = base.titleSmall.tabular(),
        bodyLarge = base.bodyLarge.tabular(),
        bodyMedium = base.bodyMedium.tabular(),
        bodySmall = base.bodySmall.tabular(),
        labelLarge = base.labelLarge.tabular(),
        labelMedium = base.labelMedium.tabular(),
        labelSmall = base.labelSmall.tabular(),
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = GymGreen,
    onPrimary = Color(0xFF00391C),
    primaryContainer = GymGreenContainerDark,
    onPrimaryContainer = Color(0xFFB4F1C9),
    secondary = SteelBlue,
    onSecondary = Color(0xFF0B1D2B),
    tertiary = Apricot,
    onTertiary = Color(0xFF3A2000),
    tertiaryContainer = ApricotContainerDark,
    onTertiaryContainer = Color(0xFFFFDDBE),
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFE4E4E7),
    onSurface = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFFA9ACB3),
    outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GymGreenDeep,
    onPrimary = Color.White,
    primaryContainer = GymGreenContainerLight,
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF3F6788),
    onSecondary = Color.White,
    tertiary = ApricotDeep,
    onTertiary = Color.White,
    tertiaryContainer = ApricotContainerLight,
    onTertiaryContainer = Color(0xFF2E1500),
    background = BackgroundLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onBackground = Color(0xFF1A1B1E),
    onSurface = Color(0xFF1A1B1E),
    onSurfaceVariant = Color(0xFF50535A),
    outline = OutlineLight,
)

@Composable
fun TonnageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = TonnageTypography,
        content = content,
    )
}
