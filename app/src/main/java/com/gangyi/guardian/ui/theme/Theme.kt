package com.gangyi.guardian.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val GuardianColorScheme = darkColorScheme(
    primary = GuardianAccent,
    onPrimary = GuardianBg,
    primaryContainer = GuardianAccentDim,
    onPrimaryContainer = GuardianText,
    secondary = GuardianInfo,
    onSecondary = GuardianBg,
    background = GuardianBg,
    onBackground = GuardianText,
    surface = GuardianSurface,
    onSurface = GuardianText,
    surfaceVariant = GuardianSurface2,
    onSurfaceVariant = GuardianTextDim,
    surfaceContainer = GuardianSurface,
    surfaceContainerHigh = GuardianSurface2,
    surfaceContainerHighest = GuardianSurface3,
    outline = GuardianBorder,
    outlineVariant = GuardianBorder,
    error = GuardianDanger,
    onError = GuardianText,
)

private val GuardianShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuardianColorScheme,
        typography = GuardianTypography,
        shapes = GuardianShapes,
        content = content
    )
}
