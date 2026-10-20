package com.example.musicapp.theme

import androidx.compose.ui.graphics.Color
import java.util.Calendar

data class ThemePreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val accentColor: Color,
    val gradientColors: List<Color>
)

object AppThemeHelper {

    val presets = listOf(
        ThemePreset(
            id = "Dynamic Time",
            name = "Dynamic Time of Day",
            subtitle = "Shifts smoothly from Dawn Amber to Midnight Aurora",
            accentColor = Color(0xFF00F59B),
            gradientColors = listOf(Color(0xFF142436), Color(0xFF131726), Color(0xFF070A0F))
        ),
        ThemePreset(
            id = "Obsidian Mint",
            name = "Obsidian Mint",
            subtitle = "Cyberpunk deep dark with vivid mint neon glow",
            accentColor = Color(0xFF00F59B),
            gradientColors = listOf(Color(0xFF0D251D), Color(0xFF0F1722), Color(0xFF06090D))
        ),
        ThemePreset(
            id = "Midnight Violet",
            name = "Midnight Violet",
            subtitle = "Celestial nebula purple with deep space indigo",
            accentColor = Color(0xFFB56BFF),
            gradientColors = listOf(Color(0xFF221334), Color(0xFF161225), Color(0xFF07060E))
        ),
        ThemePreset(
            id = "Sunset Ember",
            name = "Sunset Ember",
            subtitle = "Twilight copper, warm bronze, and dusk obsidian",
            accentColor = Color(0xFFFF7A50),
            gradientColors = listOf(Color(0xFF2E1612), Color(0xFF1C131D), Color(0xFF09060A))
        ),
        ThemePreset(
            id = "Nordic Frost",
            name = "Nordic Frost",
            subtitle = "Crisp arctic sapphire, steel cyan, and midnight sea",
            accentColor = Color(0xFF00D2FF),
            gradientColors = listOf(Color(0xFF0E273C), Color(0xFF111D2A), Color(0xFF05080E))
        ),
        ThemePreset(
            id = "Cyber Monochrome",
            name = "Cyber Monochrome",
            subtitle = "Stealth titanium and obsidian minimalist",
            accentColor = Color(0xFFE2E8F0),
            gradientColors = listOf(Color(0xFF1C2028), Color(0xFF12151B), Color(0xFF07080A))
        )
    )

    fun getThemeGradient(themeId: String): List<Color> {
        if (themeId == "Dynamic Time") {
            return getDynamicTimeGradient()
        }
        val match = presets.find { it.id.equals(themeId, ignoreCase = true) }
        return match?.gradientColors ?: getDynamicTimeGradient()
    }

    fun getDynamicTimeGradient(): List<Color> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> listOf(
                // Dawn / Early Morning: Soft sunrise amber into deep obsidian
                Color(0xFF241C16),
                Color(0xFF161720),
                Color(0xFF08090D)
            )
            in 11..16 -> listOf(
                // Afternoon: Deep oceanic slate with cyan undertones
                Color(0xFF0E2436),
                Color(0xFF131A26),
                Color(0xFF06080E)
            )
            in 17..20 -> listOf(
                // Sunset / Dusk: Deep dusk plum & warm amber twilight
                Color(0xFF281424),
                Color(0xFF1A1322),
                Color(0xFF09070D)
            )
            else -> listOf(
                // Night / Midnight: Deepest cyber obsidian with emerald aurora tint
                Color(0xFF0C1D24),
                Color(0xFF0F141F),
                Color(0xFF05070B)
            )
        }
    }
}
