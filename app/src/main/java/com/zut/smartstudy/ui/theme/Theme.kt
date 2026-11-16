package com.zut.smartstudy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Midnight,
    primaryContainer = StudyBlue,
    onPrimaryContainer = SoftWhite,
    secondary = SoftViolet,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF3B3470),
    onSecondaryContainer = SoftWhite,
    tertiary = SoftViolet,
    background = Midnight,
    onBackground = SoftWhite,
    surface = SurfaceNavy,
    onSurface = SoftWhite,
    surfaceVariant = Color(0xFF243247),
    onSurfaceVariant = MutedText,
    outline = Color(0xFF4A5F82),
    error = Color(0xFFFF8A8A)
)

@Composable
fun SmartStudyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
