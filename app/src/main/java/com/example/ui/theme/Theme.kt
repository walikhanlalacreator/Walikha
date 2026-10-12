package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1A1500),
    primaryContainer = Color(0xFF4D3B00),
    onPrimaryContainer = Color(0xFFFFDF91),
    secondary = BlueAccent,
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004C71),
    onSecondaryContainer = Color(0xFFC7E7FF),
    tertiary = BullishGreen,
    onTertiary = Color.White,
    background = TerminalBackground,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TerminalBorder,
    error = BearishRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalDarkColorScheme,
        typography = Typography,
        content = content
    )
}
