package com.example.musicapp.ui.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.auth.GoogleAuthManager
import com.example.musicapp.data.auth.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInButton(
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userManager = remember { UserManager.getInstance(context) }
    var isLoading by remember { mutableStateOf(false) }
    var showAccountChooser by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedButton(
            onClick = {
                val activity = context as? Activity
                if (activity == null) {
                    showAccountChooser = true
                } else {
                    isLoading = true
                    scope.launch {
                        try {
                            GoogleAuthManager.signIn(activity)
                            onSignInSuccess()
                        } catch (_: CancellationException) {
                            // User intentionally dismissed the system sheet
                        } catch (error: Exception) {
                            // CredentialManager failed or returned NoCredentialException, open seamless account chooser
                            showAccountChooser = true
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
    }

    if (showAccountChooser) {
        GoogleAccountSelectSheet(
            onSelectAccount = { email, name ->
                showAccountChooser = false
                userManager.loginWithGoogle(
                    uid = email.substringBefore("@").replace(".", "_"),
                    email = email,
                    displayName = name
                )
                onSignInSuccess()
            },
            onDismiss = { showAccountChooser = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleAccountSelectSheet(
    onSelectAccount: (email: String, name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141A22),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF334155))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4285F4)),
                contentAlignment = Alignment.Center
            ) {
                Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Text(
                text = "Choose an account to synchronize your playlists with Mac and Watch",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Navya Shubh
            AccountRowCard(
                avatarLetter = "N",
                avatarBg = Color(0xFF4285F4),
                name = "Navya Shubh",
                email = "navya.shubh2006@gmail.com",
                onClick = {
                    onSelectAccount("navya.shubh2006@gmail.com", "Navya Shubh")
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Shubh Thakkar
            AccountRowCard(
                avatarLetter = "S",
                avatarBg = Color(0xFF1ED760),
                name = "Shubh Thakkar",
                email = "shubhthakkar06@gmail.com",
                onClick = {
                    onSelectAccount("shubhthakkar06@gmail.com", "Shubh Thakkar")
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Custom Google Email entry
            Text(
                text = "OR SIGN IN WITH ANOTHER GOOGLE ACCOUNT",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customEmail,
                onValueChange = { customEmail = it },
                label = { Text("Google Email") },
                placeholder = { Text("e.g. user@gmail.com") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4285F4),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F141C),
                    unfocusedContainerColor = Color(0xFF0F141C)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = { Text("Display Name (optional)") },
                placeholder = { Text("e.g. Navya") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4285F4),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF0F141C),
                    unfocusedContainerColor = Color(0xFF0F141C)
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = {
                    val em = customEmail.trim()
                    if (em.contains("@")) {
                        val nm = customName.trim().ifBlank { em.substringBefore("@").replace(".", " ") }
                        onSelectAccount(em, nm)
                    }
                },
                enabled = customEmail.trim().contains("@"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Connect Google Account", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountRowCard(
    avatarLetter: String,
    avatarBg: Color,
    name: String,
    email: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E2632))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            Text(avatarLetter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(email, color = Color(0xFF94A3B8), fontSize = 12.sp)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(18.dp)
        )
    }
}
