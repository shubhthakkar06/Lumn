package com.example.musicapp.ui.navigation

import androidx.compose.runtime.saveable.Saver
import com.example.musicapp.data.network.SongInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface Screen {
    @Serializable
    data object Home : Screen

    @Serializable
    data object Search : Screen

    @Serializable
    data object Library : Screen

    @Serializable
    data class Playlist(
        val playlistId: String,
        val title: String,
        val coverUrl: String,
        val subtitle: String,
        val saves: String = "",
        val unmatchedTracks: List<SongInfo> = emptyList()
    ) : Screen

    @Serializable
    data class Artist(
        val artistName: String,
        val imageUrl: String = "",
        val listeners: String = ""
    ) : Screen

    /** An album from the catalog (Deezer album id), played through Rivara's providers. */
    @Serializable
    data class Album(
        val albumId: Long,
        val title: String,
        val artist: String,
        val coverUrl: String
    ) : Screen

    /** A generated mix (Daily Mix, radio, genre or mood mix) identified by [mixId]. */
    @Serializable
    data class Mix(
        val mixId: String,
        val title: String,
        val subtitle: String = "",
        val coverUrl: String = ""
    ) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Recents : Screen

    @Serializable
    data object SoundCapsule : Screen
}

/**
 * One back stack per bottom tab, like Spotify: switching tabs keeps where you were in each,
 * and the stacks survive rotation and the app being reclaimed in the background.
 */
@Serializable
data class TabStacks(
    val activeTab: Int = 0,
    val stacks: List<List<Screen>> = listOf(listOf(Screen.Home), listOf(Screen.Search), listOf(Screen.Library))
) {
    val current: Screen get() = stacks[activeTab].last()
    val canGoBack: Boolean get() = stacks[activeTab].size > 1 || activeTab != 0

    fun push(screen: Screen): TabStacks =
        if (stacks[activeTab].last() == screen) this
        else copy(stacks = stacks.mapIndexed { i, s -> if (i == activeTab) s + screen else s })

    /** Pops within the tab; at a tab's root, goes back to Home. */
    fun pop(): TabStacks = when {
        stacks[activeTab].size > 1 -> copy(stacks = stacks.mapIndexed { i, s -> if (i == activeTab) s.dropLast(1) else s })
        activeTab != 0 -> copy(activeTab = 0)
        else -> this
    }

    /** Selecting the active tab again returns to its root. */
    fun selectTab(tab: Int): TabStacks =
        if (tab == activeTab) copy(stacks = stacks.mapIndexed { i, s -> if (i == tab) s.take(1) else s })
        else copy(activeTab = tab)

    fun replaceActive(screens: List<Screen>): TabStacks =
        copy(stacks = stacks.mapIndexed { i, s -> if (i == activeTab) screens else s })

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val saver: Saver<TabStacks, String> = Saver(
            save = { json.encodeToString(it) },
            restore = { runCatching { json.decodeFromString<TabStacks>(it) }.getOrNull() }
        )

        fun tabOf(screen: Screen): Int? = when (screen) {
            Screen.Home -> 0
            Screen.Search -> 1
            Screen.Library -> 2
            else -> null
        }
    }
}
