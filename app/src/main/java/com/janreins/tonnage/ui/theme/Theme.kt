package com.janreins.tonnage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = IronOrange,
    onPrimary = Color(0xFF2A1200),
    primaryContainer = IronOrangeContainerDark,
    onPrimaryContainer = Color(0xFFFFDCC4),
    secondary = SteelBlue,
    onSecondary = Color(0xFF0B1D2B),
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = Color(0xFFE4E4E7),
    onSurface = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFFA9ACB3),
    outline = OutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = IronOrangeDeep,
    onPrimary = Color.White,
    primaryContainer = IronOrangeContainerLight,
    onPrimaryContainer = Color(0xFF3A1A00),
    secondary = Color(0xFF3F6788),
    onSecondary = Color.White,
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
        typography = Typography(),
        content = content,
    )
}
