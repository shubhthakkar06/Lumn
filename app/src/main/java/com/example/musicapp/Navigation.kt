package com.example.musicapp

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicapp.data.DefaultDataRepository
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.ui.auth.AuthFlowContainer
import com.example.musicapp.ui.main.MainScreen
import com.example.musicapp.ui.main.MainScreenViewModel
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.playlist.UserPlaylist
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue

private enum class NavigationRootState {
    Auth,
    Interests,
    Main
}

@Composable
fun MainNavigation(
    sharedSong: SongInfo? = null,
    onSharedSongHandled: () -> Unit = {},
    sharedPlaylist: UserPlaylist? = null,
    onSharedPlaylistHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val isLoggedIn by userManager.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()

    val rootState = when {
        !isLoggedIn -> NavigationRootState.Auth
        !currentUser.hasCompletedOnboarding -> NavigationRootState.Interests
        else -> NavigationRootState.Main
    }

    Crossfade(
        targetState = rootState,
        label = "AuthMainCrossfade",
        modifier = Modifier.fillMaxSize()
    ) { state ->
        when (state) {
            NavigationRootState.Main -> {
                val viewModel: MainScreenViewModel = viewModel {
                    MainScreenViewModel(DefaultDataRepository(context), context)
                }
                var playlistToOpen by remember { androidx.compose.runtime.mutableStateOf<UserPlaylist?>(null) }
                LaunchedEffect(sharedSong) {
                    sharedSong?.let {
                        viewModel.playSong(it)
                        onSharedSongHandled()
                    }
                }
                LaunchedEffect(sharedPlaylist) {
                    sharedPlaylist?.let { incoming ->
                        val created = PlaylistManager.getInstance(context).createPlaylist(
                            name = incoming.name,
                            creator = incoming.creator,
                            coverUrl = incoming.coverUrl,
                            songs = incoming.songs,
                            sourceUrl = incoming.sourceUrl
                        )
                        if (created != null) playlistToOpen = created
                        onSharedPlaylistHandled()
                    }
                }
                MainScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    sharedPlaylist = playlistToOpen,
                    onSharedPlaylistOpened = { playlistToOpen = null }
                )
            }
            NavigationRootState.Interests -> {
                com.example.musicapp.ui.auth.MusicInterestsScreen(
                    onInterestsCompleted = {
                        // userManager.updateInterests sets hasCompletedOnboarding = true
                    }
                )
            }
            NavigationRootState.Auth -> {
                AuthFlowContainer(
                    onAuthComplete = {
                        // State transition automatically triggered by userManager
                    }
                )
            }
        }
    }
}
