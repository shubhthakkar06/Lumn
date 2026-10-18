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

private enum class NavigationRootState {
    Auth,
    Interests,
    Main
}

@Composable
fun MainNavigation() {
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
                MainScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
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
