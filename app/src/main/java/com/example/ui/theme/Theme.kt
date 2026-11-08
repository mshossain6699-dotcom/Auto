package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AutoPilotDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF004E59),
    onPrimaryContainer = Color(0xFFB8F5FF),
    secondary = NeonEmerald,
    onSecondary = Color(0xFF002911),
    secondaryContainer = Color(0xFF005227),
    onSecondaryContainer = Color(0xFFB9FFD3),
    tertiary = AmberPulse,
    onTertiary = Color(0xFF2E1E00),
    background = ObsidianBackground,
    onBackground = SlateTextPrimary,
    surface = ObsidianSurface,
    onSurface = SlateTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    error = CoralAlert,
    onError = Color.White,
    errorContainer = Color(0xFF4A1218),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AutoPilotDarkColorScheme,
        typography = Typography,
        content = content
    )
}
