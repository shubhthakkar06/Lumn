package com.example.musicapp.desktop.theme

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class DesktopAppTheme(
    val displayName: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    SPOTIFY_EMERALD("Spotify Emerald", 0xFF1ED760, 0xFF179B45),
    CYBERPUNK_AMETHYST("Cyberpunk Amethyst", 0xFF9A62F6, 0xFF6C38CC),
    ELECTRIC_OCEAN("Electric Ocean", 0xFF00D2FF, 0xFF008BB3),
    NEON_ROSE("Neon Rose", 0xFFE34DAC, 0xFFAC267B),
    SUNSET_AMBER("Sunset Amber", 0xFFFF806C, 0xFFCC4D39),
    AMOLED_PLATINUM("Amoled Platinum", 0xFFE0E0E0, 0xFF888888);

    val primaryColor: Color get() = Color(primaryHex)
    val secondaryColor: Color get() = Color(secondaryHex)
}

object DesktopThemeManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val configFile = File(System.getProperty("user.home"), ".rivara/theme.json")

    private val _currentTheme = MutableStateFlow(DesktopAppTheme.SPOTIFY_EMERALD)
    val currentTheme: StateFlow<DesktopAppTheme> = _currentTheme.asStateFlow()

    init {
        loadTheme()
    }

    fun setTheme(theme: DesktopAppTheme) {
        _currentTheme.value = theme
        saveTheme(theme)
    }

    private fun loadTheme() {
        try {
            if (configFile.exists()) {
                val name = configFile.readText().trim()
                val matched = DesktopAppTheme.entries.firstOrNull { it.name == name }
                if (matched != null) {
                    _currentTheme.value = matched
                }
            }
        } catch (_: Exception) {}
    }

    private fun saveTheme(theme: DesktopAppTheme) {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writeText(theme.name)
        } catch (_: Exception) {}
    }
}
