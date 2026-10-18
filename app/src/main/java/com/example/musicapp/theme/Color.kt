package com.example.musicapp.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Vibrant Modern Spotify & Cyber Obsidian Palette
val SpotifyGreen = Color(0xFF00F59B) // Vibrant Electric Mint/Emerald
val SpotifyGreenClassic = Color(0xFF1DB954)
val ElectricCyan = Color(0xFF00D2FF)
val SunsetAmber = Color(0xFFFF7A00)
val CoralNeon = Color(0xFFFF5757)

val SpotifyBlack = Color(0xFF0A0E14) // Deep Cyber Obsidian
val SpotifyDarkGray = Color(0xFF161F2E) // Frosted Slate Glass
val SpotifyLightGray = Color(0xFF94A3B8) // Clean cool slate
val SpotifyWhite = Color(0xFFFFFFFF)

// Gradients & Surface Accents
val AuroraGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00F59B), Color(0xFF00D2FF))
)

val AmberSunsetGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF7A00), Color(0xFFFF5757))
)

val GlassCardBorder = Color(0x33FFFFFF)
val GlassCardBackground = Color(0xFF161F2E)
