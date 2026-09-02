package com.dailymemory.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC15A3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3C0900),
    secondary = Color(0xFF77574D),
    secondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceVariant = Color(0xFFF5DED6),
    outline = Color(0xFF8E7067),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59D),
    onPrimary = Color(0xFF65200D),
    primaryContainer = Color(0xFF8B3A24),
    secondary = Color(0xFFE7BDB0),
    background = Color(0xFF201A18),
    surface = Color(0xFF201A18),
    surfaceVariant = Color(0xFF53433E),
)

@Composable
fun DailyMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
