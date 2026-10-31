package com.example.musicapp.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.auth.GoogleAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInButton(
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = {
                val activity = context as? Activity
                if (activity == null) {
                    errorMessage = "Google sign-in needs an active app window. Please try again."
                } else {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            GoogleAuthManager.signIn(activity)
                            onSignInSuccess()
                        } catch (cancelled: CancellationException) {
                            // Closing the Google account sheet is a normal user action.
                        } catch (error: Exception) {
                            errorMessage = error.message ?: "Google sign-in failed. Please try again."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text("G", color = Color(0xFF8AB4F8), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Spacer(Modifier.size(10.dp))
                    Text("Continue with Google", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A9A), fontSize = 13.sp)
        }
    }
}
