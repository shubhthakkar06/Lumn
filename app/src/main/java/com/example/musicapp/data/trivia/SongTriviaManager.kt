package com.example.musicapp.data.trivia

import android.content.Context
import android.content.SharedPreferences
import com.example.musicapp.data.network.SongInfo

data class RelatedVideo(
    val id: String,
    val title: String,
    val artist: String,
    val duration: String,
    val thumbnailUrl: String
)

data class SongTrivia(
    val songId: String,
    val title: String,
    val text: String,
    val source: String,
    val relatedVideos: List<RelatedVideo>
)

class SongTriviaManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("song_trivia_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: SongTriviaManager? = null

        fun getInstance(context: Context): SongTriviaManager {
            return instance ?: synchronized(this) {
                instance ?: SongTriviaManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val triviaMap = mapOf(
        "dholida" to SongTrivia(
            songId = "dholida",
            title = "Dholida",
            text = "In the music video for Dholida, Aayush Sharma and Warina Hussain perform intricate garba routines choreographed to highlight their on-screen chemistry. The colorful visuals and dynamic choreography were intended to immerse viewers in the festive atmosphere, reflecting the film's central theme of love blossoming during Navratri.",
            source = "timesofindia.indiatimes.com",
            relatedVideos = listOf(
                RelatedVideo(
                    id = "daingad",
                    title = "Daingad Daingad (From \"Humpty Sharma Ki Dulhania\")",
                    artist = "Sachin-Jigar, Divya Kumar, Akriti Kakar",
                    duration = "04:00",
                    thumbnailUrl = "https://i.ytimg.com/vi/aL3X7b-m0YI/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "dhol_baje",
                    title = "Dholi Taro Dhol Baje",
                    artist = "Ismail Darbar, Kavita Krishnamurthy, Vinod Rathod",
                    duration = "05:15",
                    thumbnailUrl = "https://i.ytimg.com/vi/b_C333_Bwwc/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "kamariya",
                    title = "Kamariya (From \"Mitron\")",
                    artist = "Darshan Raval, DJ Chetas, Lijo George",
                    duration = "03:20",
                    thumbnailUrl = "https://i.ytimg.com/vi/y83x7Wg44gk/hqdefault.jpg"
                )
            )
        ),
        "viva la vida" to SongTrivia(
            songId = "viva_la_vida",
            title = "Viva La Vida",
            text = "Viva La Vida was written by all members of Coldplay and takes its title from a painting by Mexican artist Frida Kahlo. The track features an orchestral arrangement built around a looping string section, cathedral bells, and timpani percussion rather than traditional guitar riffs, winning Song of the Year at the 51st Grammy Awards.",
            source = "rollingstone.com",
            relatedVideos = listOf(
                RelatedVideo(
                    id = "yellow",
                    title = "Yellow (Official Video)",
                    artist = "Coldplay",
                    duration = "04:29",
                    thumbnailUrl = "https://i.ytimg.com/vi/yKNxeF4KMsY/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "the_scientist",
                    title = "The Scientist",
                    artist = "Coldplay",
                    duration = "04:26",
                    thumbnailUrl = "https://i.ytimg.com/vi/RB-RcX5DS5A/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "paradise",
                    title = "Paradise (Official Video)",
                    artist = "Coldplay",
                    duration = "04:38",
                    thumbnailUrl = "https://i.ytimg.com/vi/1G4isv_Fylg/hqdefault.jpg"
                )
            )
        ),
        "yellow" to SongTrivia(
            songId = "yellow",
            title = "Yellow",
            text = "Coldplay recorded 'Yellow' at Rockfield Studios in South Wales. Frontman Chris Martin was inspired by the clear starry night sky while stepping outside the studio. The iconic single-take music video was shot at Studland Bay in Dorset, England with Martin walking along the shoreline at dawn in the rain.",
            source = "nme.com",
            relatedVideos = listOf(
                RelatedVideo(
                    id = "fix_you",
                    title = "Fix You (Live in São Paulo)",
                    artist = "Coldplay",
                    duration = "05:08",
                    thumbnailUrl = "https://i.ytimg.com/vi/k4V3Mo61fJM/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "sparks",
                    title = "Sparks (Acoustic Session)",
                    artist = "Coldplay",
                    duration = "03:47",
                    thumbnailUrl = "https://i.ytimg.com/vi/W4h8P1a1e_M/hqdefault.jpg"
                )
            )
        ),
        "moti veraana" to SongTrivia(
            songId = "moti_veraana",
            title = "Moti Veraana",
            text = "Moti Veraana is an energetic Gujarati folk fusion composed by Amit Trivedi featuring the soulful vocals of Osman Mir for the Azaad project. The piece blends traditional garba rhythms with modern brass and electronic basslines, celebrating festive devotion with electrifying musical layering.",
            source = "tseries.com",
            relatedVideos = listOf(
                RelatedVideo(
                    id = "chogada",
                    title = "Chogada (From \"Loveyatri\")",
                    artist = "Darshan Raval, Asees Kaur",
                    duration = "04:09",
                    thumbnailUrl = "https://i.ytimg.com/vi/d4t6f6L8y7k/hqdefault.jpg"
                ),
                RelatedVideo(
                    id = "radha_ne_shyam",
                    title = "Radha Ne Shyam Mali Jashe",
                    artist = "Sachin-Jigar, Sachin Sanghvi",
                    duration = "04:30",
                    thumbnailUrl = "https://i.ytimg.com/vi/eZ_o7_3x9kM/hqdefault.jpg"
                )
            )
        )
    )

    fun getTrivia(song: SongInfo): SongTrivia {
        val key = triviaMap.keys.find { song.title.contains(it, ignoreCase = true) }
        if (key != null) {
            return triviaMap[key]!!
        }

        // Context-aware dynamic fallback
        return SongTrivia(
            songId = song.videoId,
            title = song.title,
            text = "'${song.title}' performed by ${song.artist}. Enjoy the track in Lumn.",
            source = "genius.com",
            relatedVideos = listOf(
                RelatedVideo(
                    id = "rel_1_${song.videoId}",
                    title = "${song.title} (Official Music Video)",
                    artist = song.artist,
                    duration = "03:45",
                    thumbnailUrl = song.albumArtUrl
                ),
                RelatedVideo(
                    id = "rel_2_${song.videoId}",
                    title = "${song.artist} - Live Performance Session",
                    artist = song.artist,
                    duration = "04:12",
                    thumbnailUrl = song.albumArtUrl
                )
            )
        )
    }

    fun setFeedback(songId: String, isLiked: Boolean) {
        prefs.edit().putBoolean("feedback_$songId", isLiked).apply()
    }

    fun getFeedback(songId: String): Boolean? {
        if (!prefs.contains("feedback_$songId")) return null
        return prefs.getBoolean("feedback_$songId", true)
    }
}
