package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CyberBlack,
    primaryContainer = Color(0xFF00382E),
    onPrimaryContainer = Color(0xFF73FBD3),
    secondary = ElectricBlue,
    onSecondary = CyberBlack,
    secondaryContainer = Color(0xFF0C4A6E),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = NeonPurple,
    onTertiary = CyberBlack,
    tertiaryContainer = Color(0xFF581C87),
    onTertiaryContainer = Color(0xFFE9D5FF),
    background = CyberBlack,
    onBackground = TextPrimary,
    surface = CyberDark,
    onSurface = TextPrimary,
    surfaceVariant = CyberSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = CyberBorder,
    error = CyberPink,
    onError = Color.White
)

private val LightColorScheme = darkColorScheme( // Carlos AI is visually optimized with futuristic dark mode aesthetics
    primary = NeonCyan,
    onPrimary = CyberBlack,
    surface = CyberDark,
    background = CyberBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
