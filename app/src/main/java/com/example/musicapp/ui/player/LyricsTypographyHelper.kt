package com.example.musicapp.ui.player

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.network.SongInfo

enum class SongGenreMood {
    ROMANTIC_ITALIAN,
    DREAMY_CHILL,
    ROCK_ENERGETIC,
    URBAN_HIPHOP,
    MODERN_POP
}

object LyricsTypographyHelper {

    fun detectMood(song: SongInfo): SongGenreMood {
        val text = "${song.title} ${song.artist} ${song.albumName}".lowercase()
        return when {
            // Romantic keywords & artists -> Italian style / cursive serif italic
            text.contains("love") || text.contains("ishq") || text.contains("dil") ||
            text.contains("mohabbat") || text.contains("pyaar") || text.contains("romantic") ||
            text.contains("shayad") || text.contains("kesariya") || text.contains("tum") ||
            text.contains("humsafar") || text.contains("heart") || text.contains("darling") ||
            text.contains("arijit") || text.contains("atif aslam") || text.contains("ed sheeran") ||
            text.contains("taylor swift") || text.contains("pritam") || text.contains("armaan") ||
            text.contains("shreya") -> SongGenreMood.ROMANTIC_ITALIAN

            // Rock / Energetic
            text.contains("rock") || text.contains("metal") || text.contains("queen") ||
            text.contains("ac/dc") || text.contains("linkin") || text.contains("metallica") ||
            text.contains("imagine dragons") || text.contains("believer") || text.contains("remix") ||
            text.contains("dance") || text.contains("club") -> SongGenreMood.ROCK_ENERGETIC

            // Hip Hop / Rap
            text.contains("rap") || text.contains("hip hop") || text.contains("drake") ||
            text.contains("eminem") || text.contains("divine") || text.contains("mc stan") ||
            text.contains("sidhu") || text.contains("karan aujla") || text.contains("badshah") ||
            text.contains("kendrick") || text.contains("travis") -> SongGenreMood.URBAN_HIPHOP

            // Chill / Acoustic / Dreamy
            text.contains("chill") || text.contains("lofi") || text.contains("acoustic") ||
            text.contains("slowed") || text.contains("ambient") || text.contains("peace") ||
            text.contains("sparks") || text.contains("yellow") || text.contains("coldplay") ||
            text.contains("prateek") || text.contains("jasleen") -> SongGenreMood.DREAMY_CHILL

            else -> SongGenreMood.MODERN_POP
        }
    }

    data class LyricFontStyle(
        val fontFamily: FontFamily,
        val fontStyle: FontStyle,
        val activeWeight: FontWeight,
        val inactiveWeight: FontWeight,
        val letterSpacing: TextUnit = 0.sp,
        val genreLabel: String
    )

    fun getFontStyle(song: SongInfo): LyricFontStyle {
        return when (detectMood(song)) {
            SongGenreMood.ROMANTIC_ITALIAN -> LyricFontStyle(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                activeWeight = FontWeight.Bold,
                inactiveWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                genreLabel = "Romantic Italian Serif"
            )
            SongGenreMood.DREAMY_CHILL -> LyricFontStyle(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                activeWeight = FontWeight.SemiBold,
                inactiveWeight = FontWeight.Normal,
                letterSpacing = 0.3.sp,
                genreLabel = "Dreamy Acoustic"
            )
            SongGenreMood.ROCK_ENERGETIC -> LyricFontStyle(
                fontFamily = FontFamily.SansSerif,
                fontStyle = FontStyle.Normal,
                activeWeight = FontWeight.Black,
                inactiveWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                genreLabel = "Energetic Grotesque"
            )
            SongGenreMood.URBAN_HIPHOP -> LyricFontStyle(
                fontFamily = FontFamily.Monospace,
                fontStyle = FontStyle.Normal,
                activeWeight = FontWeight.Bold,
                inactiveWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
                genreLabel = "Urban Monospace"
            )
            SongGenreMood.MODERN_POP -> LyricFontStyle(
                fontFamily = FontFamily.Default,
                fontStyle = FontStyle.Normal,
                activeWeight = FontWeight.ExtraBold,
                inactiveWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
                genreLabel = "Modern Sans"
            )
        }
    }
}
