package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SaliDarkColorScheme = darkColorScheme(
    primary = VioletPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = VioletLight,
    secondary = PurpleSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B0764),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = IndigoTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF1E1B4B),
    onTertiaryContainer = Color(0xFFC7D2FE),
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun SaliTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SaliDarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Backward-compatible alias for existing tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SaliTheme(content = content)
}

