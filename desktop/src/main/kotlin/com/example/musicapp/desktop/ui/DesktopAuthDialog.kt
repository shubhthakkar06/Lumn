package com.example.musicapp.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.auth.DesktopGoogleAuth
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.data.DesktopUserManager

@Composable
fun DesktopAuthDialog(
    userManager: DesktopUserManager,
    onDismiss: () -> Unit
) {
    val currentUser by userManager.currentUser.collectAsState()
    val savedAccounts by userManager.savedAccounts.collectAsState()

    var isAuthenticating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (currentUser == null) "Welcome to Rivara" else "Account & Profiles",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                if (currentUser != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .width(420.dp)
                    .padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Sign in to synchronize your playlists, library, and personalized mixes across mobile and desktop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0AAB8)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Saved Accounts List
                if (savedAccounts.isNotEmpty()) {
                    Text(
                        text = "SAVED ACCOUNTS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                        color = Color(0xFF8E99A8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedAccounts, key = { it.id }) { user ->
                            val isActive = user.id == currentUser?.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) Color(0xFF1E3827) else Color(0xFF22262E))
                                    .border(
                                        width = 1.dp,
                                        color = if (isActive) Color(0xFF1ED760).copy(alpha = 0.6f) else Color(0xFF2E3540),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        userManager.switchAccount(user)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) Color(0xFF1ED760) else Color(0xFF384252)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.avatarLetter,
                                        color = if (isActive) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isActive) Color(0xFF1ED760) else Color.White
                                    )
                                    if (user.email.isNotBlank()) {
                                        Text(text = user.email, style = MaterialTheme.typography.labelSmall, color = Color(0xFF8E99A8))
                                    }
                                }
                                IconButton(
                                    onClick = { userManager.signOut(user) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFF888888), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF55C7FF),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Official "Continue with Google" Action Button
                Button(
                    onClick = {
                        isAuthenticating = true
                        statusMessage = "Opening Google Sign-In in your browser..."
                        DesktopGoogleAuth.launchBrowserAuth(
                            onSuccess = { res ->
                                isAuthenticating = false
                                statusMessage = "Signed in as ${res.name}!"
                                userManager.signIn(name = res.name, email = res.email, isGuest = false)
                                onDismiss()
                            },
                            onError = { err ->
                                isAuthenticating = false
                                statusMessage = err
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Connecting to Google...", fontWeight = FontWeight.Bold)
                    } else {
                        // Google "G" Badge Icon
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4285F4)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("G", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Guest Access / Log Out Button
                if (currentUser == null || currentUser?.isGuest == true) {
                    OutlinedButton(
                        onClick = {
                            userManager.signIn(name = "Guest User", isGuest = true)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF384252))
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Continue as Guest", fontWeight = FontWeight.Medium)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            userManager.signOut()
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                    ) {
                        Text("Sign Out of Current Account", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        containerColor = Color(0xFF161A22),
        shape = RoundedCornerShape(16.dp)
    )
}
