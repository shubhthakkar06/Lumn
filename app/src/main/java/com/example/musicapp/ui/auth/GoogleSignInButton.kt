package com.example.musicapp.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.musicapp.data.auth.GoogleAuthManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Google sign-in through Credential Manager + Firebase. There is deliberately no fallback
 * that lets someone type an email address: an account is only created after Google and
 * Firebase have verified it.
 */
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
                    errorMessage = "Google sign-in isn't available here. Please restart the app and try again."
                    return@OutlinedButton
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    try {
                        GoogleAuthManager.signIn(activity)
                        onSignInSuccess()
                    } catch (_: CancellationException) {
                        // Coroutine cancelled (screen left); nothing to report.
                    } catch (_: GetCredentialCancellationException) {
                        // User dismissed the Google account sheet.
                    } catch (_: NoCredentialException) {
                        errorMessage = "No Google account found on this device. Add one in Android Settings → Accounts, or sign up with a username."
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "Google sign-in failed. Please try again."
                    } finally {
                        isLoading = false
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
        errorMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = Color(0xFFFF8A9A),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
