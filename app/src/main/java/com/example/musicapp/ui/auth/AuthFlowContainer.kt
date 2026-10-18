package com.example.musicapp.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.musicapp.data.auth.UserManager

private enum class AuthScreenState {
    Landing,
    SignUp,
    SignIn
}

@Composable
fun AuthFlowContainer(
    onAuthComplete: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AuthScreenState.Landing) }

    BackHandler(enabled = currentScreen != AuthScreenState.Landing) {
        currentScreen = AuthScreenState.Landing
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == AuthScreenState.Landing) {
                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
            } else {
                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
            }
        },
        label = "AuthFlowTransition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            AuthScreenState.Landing -> {
                AuthLandingScreen(
                    onNavigateToSignUp = { currentScreen = AuthScreenState.SignUp },
                    onNavigateToSignIn = { currentScreen = AuthScreenState.SignIn }
                )
            }
            AuthScreenState.SignUp -> {
                SignUpScreen(
                    onBack = { currentScreen = AuthScreenState.Landing },
                    onSignUpSuccess = onAuthComplete
                )
            }
            AuthScreenState.SignIn -> {
                SignInScreen(
                    onBack = { currentScreen = AuthScreenState.Landing },
                    onNavigateToSignUp = { currentScreen = AuthScreenState.SignUp },
                    onSignInSuccess = onAuthComplete
                )
            }
        }
    }
}
