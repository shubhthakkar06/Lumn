package com.example.musicapp.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

val WearPrimary = Color(0xFF1ED760)
val WearPrimaryContainer = Color(0xFF144D25)
val WearOnPrimary = Color(0xFF000000)
val WearSecondary = Color(0xFF00D2FF)
val WearBackground = Color(0xFF000000)
val WearSurface = Color(0xFF1A1A1A)
val WearOnSurface = Color(0xFFEEEEEE)

private val WearColorScheme = ColorScheme(
    primary = WearPrimary,
    primaryContainer = WearPrimaryContainer,
    onPrimary = WearOnPrimary,
    secondary = WearSecondary,
    background = WearBackground,
    surfaceContainer = WearSurface,
    onSurface = WearOnSurface
)

@Composable
fun RivaraWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WearColorScheme,
        content = content
    )
}
