package com.example.musicapp.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.settings.SettingsManager

/**
 * Semantic colors. New and updated screens use these instead of raw hex values, so the look
 * stays consistent and can change in one place.
 */
object RivaraColors {
    val background = SpotifyBlack
    val surface = SpotifyDarkGray
    val surfaceRaised = Color(0xFF1A2130)
    val outline = Color(0xFF2E384D)
    val textPrimary = SpotifyWhite
    val textSecondary = Color(0xFFA0AEC0)
    val textMuted = Color(0xFF94A3B8)
    val error = Color(0xFFFF8A9A)
    val liked = Color(0xFFFF416C)
}

private fun schemeFor(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = SpotifyBlack,
    primaryContainer = accent.copy(alpha = 0.24f),
    onPrimaryContainer = SpotifyWhite,
    secondary = ElectricCyan,
    onSecondary = SpotifyBlack,
    tertiary = SunsetAmber,
    onTertiary = SpotifyBlack,
    background = RivaraColors.background,
    onBackground = RivaraColors.textPrimary,
    surface = RivaraColors.surface,
    onSurface = RivaraColors.textPrimary,
    surfaceVariant = RivaraColors.surfaceRaised,
    onSurfaceVariant = RivaraColors.textSecondary,
    surfaceContainer = RivaraColors.surface,
    surfaceContainerHigh = RivaraColors.surfaceRaised,
    surfaceContainerHighest = RivaraColors.surfaceRaised,
    outline = RivaraColors.outline,
    outlineVariant = RivaraColors.outline,
    error = RivaraColors.error
)

@Composable
fun MusicAppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    // Material components (dialogs, sheets, sliders, text fields, switches) follow the accent
    // of the theme chosen in Settings instead of a fixed green.
    val themeName by remember { SettingsManager.getInstance(context.applicationContext).appTheme }.collectAsStateWithLifecycle()
    val accent = remember(themeName) { runCatching { AppThemeHelper.getThemeAccent(themeName) }.getOrDefault(SpotifyGreen) }
    val colorScheme = remember(accent) { schemeFor(accent) }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
