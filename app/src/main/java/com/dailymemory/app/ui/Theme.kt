package com.dailymemory.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object JournalColors {
    val Background = Color(0xFFFFF4F5)
    val Ink = Color(0xFF762713)
    val Coral = Color(0xFFFF706D)
    val Hero = Color(0xFFFE9A98)
    val Border = Color(0xFFFADCDD)
    val Navigation = Color(0xFFFED9D9)
    val Muted = Color(0xFFA9948B)
    val Cream = Color(0xFFFFF5E0)
    val Gold = Color(0xFFF4C35D)
    val GoldInk = Color(0xFF85682F)
}

private val JournalScheme = lightColorScheme(
    primary = JournalColors.Coral,
    onPrimary = Color.White,
    primaryContainer = JournalColors.Navigation,
    onPrimaryContainer = JournalColors.Ink,
    secondary = JournalColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = JournalColors.Cream,
    onSecondaryContainer = JournalColors.GoldInk,
    tertiary = JournalColors.Gold,
    onTertiary = JournalColors.Ink,
    background = JournalColors.Background,
    onBackground = JournalColors.Ink,
    surface = Color.White,
    onSurface = JournalColors.Ink,
    surfaceVariant = JournalColors.Background,
    onSurfaceVariant = JournalColors.Muted,
    outline = JournalColors.Border,
    outlineVariant = JournalColors.Border,
    error = Color(0xFFD45457),
    errorContainer = Color(0xFFFFE6E5),
    onErrorContainer = JournalColors.Ink,
    surfaceTint = Color.Transparent,
)

private fun journalText(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

private val JournalTypography = Typography(
    displaySmall = journalText(32, 40, FontWeight.Medium),
    headlineLarge = journalText(28, 36, FontWeight.Bold),
    headlineMedium = journalText(25, 33, FontWeight.SemiBold),
    headlineSmall = journalText(22, 30, FontWeight.SemiBold),
    titleLarge = journalText(22, 30, FontWeight.SemiBold),
    titleMedium = journalText(18, 26, FontWeight.Medium),
    titleSmall = journalText(16, 24, FontWeight.Medium),
    bodyLarge = journalText(16, 25),
    bodyMedium = journalText(14, 22),
    bodySmall = journalText(12, 18),
    labelLarge = journalText(15, 22, FontWeight.Medium),
    labelMedium = journalText(13, 19),
    labelSmall = journalText(11, 16),
)

@Composable
fun DailyMemoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JournalScheme,
        typography = JournalTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}
