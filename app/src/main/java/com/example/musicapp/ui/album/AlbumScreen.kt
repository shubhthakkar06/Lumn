package com.example.musicapp.ui.album

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.musicapp.data.catalog.SaavnCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.ui.components.TrackListPage
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.ui.navigation.Screen

/** A real album from the catalog, with every track playable. */
@Composable
fun AlbumScreen(
    screen: Screen.Album,
    viewModel: MainScreenViewModel,
    onBack: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    var songs by androidx.compose.runtime.remember(screen.albumId) { mutableStateOf<List<SongInfo>?>(null) }
    var year by rememberSaveable(screen.albumId) { mutableStateOf("") }
    LaunchedEffect(screen.albumId) {
        val detail = runCatching { SaavnCatalog.album(screen.albumId.toString()) }.getOrNull()
        songs = detail?.songs.orEmpty()
        year = detail?.ref?.year.orEmpty()
    }
    TrackListPage(
        title = screen.title,
        subtitle = listOf(screen.artist, year).filter { it.isNotBlank() }.joinToString(" • "),
        coverUrl = screen.coverUrl,
        songs = songs,
        viewModel = viewModel,
        onBack = onBack,
        onNavigate = onNavigate,
        numbered = true
    )
}
