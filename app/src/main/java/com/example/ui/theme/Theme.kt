package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TealAccent,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealOnContainer,
    secondary = NavyPrimary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantEditorial,
    onSecondaryContainer = NavyPrimary,
    tertiary = GreenIndicator,
    onTertiary = Color.White,
    tertiaryContainer = GreenContainer,
    onTertiaryContainer = NavyPrimary,
    background = CanvasBackground,
    onBackground = NavyPrimary,
    surface = SurfaceCard,
    onSurface = NavyPrimary,
    surfaceVariant = SurfaceVariantEditorial,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderSubtle,
    error = CoralNegative,
    onError = Color.White,
    errorContainer = CoralContainer,
    onErrorContainer = NavyPrimary,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTealPrimary,
    onPrimary = NavyDeep,
    primaryContainer = DarkTealContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = Color(0xFF93B8CE),
    onSecondary = NavyDeep,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = Color(0xFF6BB396),
    onTertiary = NavyDeep,
    tertiaryContainer = Color(0xFF1E3E33),
    onTertiaryContainer = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    error = Color(0xFFE58279),
    onError = NavyDeep,
    errorContainer = Color(0xFF4A201D),
    onErrorContainer = DarkTextPrimary,
)

val EarthPulseShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun EarthPulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = EarthPulseTypography,
        shapes = EarthPulseShapes,
        content = content,
    )
}
