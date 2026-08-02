package com.gangyi.guardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GuardianColorScheme = darkColorScheme(
    primary = GuardianAccent,
    onPrimary = GuardianBg,
    background = GuardianBg,
    onBackground = GuardianText,
    surface = GuardianSurface,
    onSurface = GuardianText,
    surfaceVariant = GuardianSurface2,
    onSurfaceVariant = GuardianTextDim,
    error = GuardianDanger,
    onError = GuardianText,
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuardianColorScheme,
        typography = GuardianTypography,
        content = content
    )
}
