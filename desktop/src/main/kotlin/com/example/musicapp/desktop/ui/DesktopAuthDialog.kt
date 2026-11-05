package com.example.musicapp.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.auth.DesktopGoogleAuth
import com.example.musicapp.desktop.data.DesktopUser
import com.example.musicapp.desktop.data.DesktopUserManager
import com.example.musicapp.desktop.sync.DesktopSyncService

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

    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(currentUser?.name) { mutableStateOf(currentUser?.name ?: "") }
    var nameUpdateSuccess by remember { mutableStateOf(false) }

    // Known Google accounts available on this machine
    val defaultGoogleAccounts = remember {
        listOf(
            DesktopUser(
                id = "usr_navya_shubh2006_gmail_com",
                name = "Shubh Thakkar",
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

    val launchBrowser = { targetEmail: String ->
        isAuthenticating = true
        statusMessage = "Browser tab opened! Please choose your Google account in the browser."
        DesktopGoogleAuth.launchBrowserAuth(
            preferredEmail = targetEmail,
            onSuccess = { res ->
                isAuthenticating = false
                userManager.signIn(name = res.name, email = res.email, isGuest = false)
                onDismiss()
            },
            onError = { err ->
                isAuthenticating = false
                statusMessage = err
            }
        )
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
                        text = if (currentUser == null || currentUser?.isGuest == true) "Sign in with Google" else "Account & Profiles",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
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

                Spacer(modifier = Modifier.height(14.dp))

                // Active Profile & Display Name Editor Card
                if (currentUser != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF4285F4), Color(0xFF1ED760))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentUser?.avatarLetter ?: "U",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "CURRENT PROFILE",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                letterSpacing = 1.2.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            ),
                                            color = Color(0xFF8E9BB0)
                                        )
                                        if (!isEditingName) {
                                            Text(
                                                text = currentUser?.name ?: "Guest",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                        }
                                        if (currentUser?.email?.isNotBlank() == true) {
                                            Text(
                                                text = currentUser!!.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFFA0AAB8)
                                            )
                                        }
                                    }
                                }

                                if (!isEditingName) {
                                    Button(
                                        onClick = {
                                            editedName = currentUser?.name ?: ""
                                            isEditingName = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202A3B)),
                                        shape = RoundedCornerShape(16.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Edit Name", color = Color(0xFF64B5F6), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            if (isEditingName) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0D121B))
                                        .border(1.dp, Color(0xFF4285F4), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    BasicTextField(
                                        value = editedName,
                                        onValueChange = { editedName = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        cursorBrush = SolidColor(Color(0xFF4285F4)),
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            if (editedName.isNotBlank()) {
                                                userManager.updateProfileName(editedName)
                                                isEditingName = false
                                                nameUpdateSuccess = true
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ED760)),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    TextButton(
                                        onClick = {
                                            editedName = currentUser?.name ?: ""
                                            isEditingName = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }

                                if (!editedName.equals("Shubh Thakkar", ignoreCase = true)) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextButton(
                                        onClick = {
                                            editedName = "Shubh Thakkar"
                                            userManager.updateProfileName("Shubh Thakkar")
                                            isEditingName = false
                                            nameUpdateSuccess = true
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            "💡 Quick set name to \"Shubh Thakkar\"",
                                            color = Color(0xFF64B5F6),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (nameUpdateSuccess) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✓ Profile name updated to \"${currentUser?.name}\"!",
                                    color = Color(0xFF1ED760),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Primary "Sign in with Google in Browser" Button (Always Available)
                Button(
                    onClick = {
                        val preferred = currentUser?.email?.ifBlank { "navya.shubh2006@gmail.com" } ?: "navya.shubh2006@gmail.com"
                        launchBrowser(preferred)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4285F4)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("G", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (currentUser == null || currentUser?.isGuest == true) "Sign In with Google in Browser" else "Re-authenticate with Google in Browser",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (isAuthenticating || statusMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2633)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = statusMessage ?: "Waiting for browser sign-in...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "http://127.0.0.1:${DesktopSyncService.SYNC_PORT}/auth",
                                    color = Color(0xFF4285F4),
                                    fontSize = 11.sp
                                )
                                TextButton(
                                    onClick = { launchBrowser("navya.shubh2006@gmail.com") },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Re-open Browser", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SAVED GOOGLE ACCOUNTS",
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

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Small Browser prompt icon button
                                IconButton(
                                    onClick = { launchBrowser(user.email) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = "Log in via browser",
                                        tint = Color(0xFF4285F4),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (isActive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Check, contentDescription = "Active", tint = Color(0xFF1ED760), modifier = Modifier.size(20.dp))
                                }
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
