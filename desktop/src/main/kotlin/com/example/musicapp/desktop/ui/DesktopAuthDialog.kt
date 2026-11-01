package com.example.musicapp.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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

    var showCustomInput by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }
    var isAuthenticating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Known Google accounts available on this machine
    val defaultGoogleAccounts = remember {
        listOf(
            DesktopUser(
                id = "usr_navya_shubh2006_gmail_com",
                name = "Navya Shubh",
                email = "navya.shubh2006@gmail.com",
                avatarColorHex = "#4285F4"
            ),
            DesktopUser(
                id = "usr_shubhthakkar06_gmail_com",
                name = "Shubh Thakkar",
                email = "shubhthakkar06@gmail.com",
                avatarColorHex = "#1ED760"
            )
        )
    }

    // Merge saved accounts and known accounts
    val displayAccounts = remember(savedAccounts) {
        val map = linkedMapOf<String, DesktopUser>()
        // Show saved accounts first
        for (acc in savedAccounts) {
            map[acc.email.lowercase().ifBlank { acc.id }] = acc
        }
        // Ensure the two primary Google accounts are always quick-accessible
        for (def in defaultGoogleAccounts) {
            if (!map.containsKey(def.email.lowercase())) {
                map[def.email.lowercase()] = def
            }
        }
        map.values.toList()
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (currentUser == null) "Welcome to Rivara" else "Account & Profiles",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
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
                    .width(440.dp)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Sign in to synchronize your playlists, library, and personalized mixes across mobile, desktop, and watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0AAB8)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary "Continue with Google" Button (if no user or guest)
                if (currentUser == null || currentUser?.isGuest == true) {
                    Button(
                        onClick = {
                            isAuthenticating = true
                            DesktopGoogleAuth.launchBrowserAuth(
                                preferredEmail = "navya.shubh2006@gmail.com",
                                onSuccess = { res ->
                                    isAuthenticating = false
                                    userManager.signIn(name = res.name, email = res.email, isGuest = false)
                                    onDismiss()
                                },
                                onError = {
                                    isAuthenticating = false
                                    userManager.signIn(name = "Navya Shubh", email = "navya.shubh2006@gmail.com", isGuest = false)
                                    onDismiss()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Connecting...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4285F4)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("G", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Continue with Google (Navya Shubh)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = "SELECT GOOGLE ACCOUNT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF8E99A8)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // List of Google Accounts with 1-click login
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (user in displayAccounts) {
                        val isActive = user.id == currentUser?.id || (currentUser?.email.equals(user.email, ignoreCase = true) && user.email.isNotBlank())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) Color(0xFF193222) else Color(0xFF20252E))
                                .border(
                                    width = 1.dp,
                                    color = if (isActive) Color(0xFF1ED760).copy(alpha = 0.8f) else Color(0xFF2E3540),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    userManager.signIn(name = user.name, email = user.email, isGuest = false)
                                    onDismiss()
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Google "G" Badge Icon
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (user.email.contains("navya")) Color(0xFF4285F4) else Color(0xFF1ED760)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.avatarLetter,
                                    color = if (user.email.contains("navya")) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (isActive) Color(0xFF1ED760) else Color.White
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF1ED760))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                                if (user.email.isNotBlank()) {
                                    Text(text = user.email, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0AAB8))
                                }
                            }

                            if (isActive) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF1ED760), modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = "Sign In",
                                    color = Color(0xFF4285F4),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom Google Account Sign-In Expandable
                if (!showCustomInput) {
                    TextButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF4285F4), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add / Sign in with another Google Account", color = Color(0xFF4285F4), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1B2028))
                            .border(1.dp, Color(0xFF2E3846), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text("Sign In with Google Account", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Name Field
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262C36))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (customName.isEmpty()) {
                                Text("Display Name (e.g. Navya or Shubh)", color = Color.Gray, fontSize = 12.5.sp)
                            }
                            BasicTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Email Field
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262C36))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (customEmail.isEmpty()) {
                                Text("Google Email (e.g. user@gmail.com)", color = Color.Gray, fontSize = 12.5.sp)
                            }
                            BasicTextField(
                                value = customEmail,
                                onValueChange = { customEmail = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCustomInput = false }) {
                                Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val name = customName.trim().ifBlank { customEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() } }
                                    val email = customEmail.trim().ifBlank { "user@gmail.com" }
                                    userManager.signIn(name = name, email = email, isGuest = false)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4), contentColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Guest Access / Sign Out
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            userManager.signIn(name = "Guest User", isGuest = true)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA0AAB8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF384252)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Continue as Guest", fontSize = 12.sp)
                    }

                    if (currentUser != null && !currentUser!!.isGuest) {
                        TextButton(
                            onClick = {
                                userManager.signOut()
                            }
                        ) {
                            Text("Sign Out", color = Color(0xFFFF5252), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF161A22),
        shape = RoundedCornerShape(16.dp)
    )
}
