package com.shabi.sweep

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KeepColor = Color(0xFF17996A)
val TossColor = Color(0xFFE0454B)

private val Ink = Color(0xFF18202E)
private val Paper = Color(0xFFEDF1F7)

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    background = Color(0xFFE9EEF5), onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    onSurfaceVariant = Color(0xFF5D6778),
    outline = Color(0xFFCBD3DF),
)

private val DarkColors = darkColorScheme(
    primary = Paper, onPrimary = Color(0xFF121722),
    background = Color(0xFF121722), onBackground = Paper,
    surface = Color(0xFF1D2432), onSurface = Paper,
    onSurfaceVariant = Color(0xFF98A2B3),
    outline = Color(0xFF2E3747),
)

@Composable
fun SweepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
