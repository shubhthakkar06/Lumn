package com.example.musicapp.desktop.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.desktop.data.DesktopUserManager

/**
 * Local profiles for Rivara on the Mac. The desktop app has no Google sign-in of its own, so it
 * no longer pretends to: a profile is just a name on this computer, and the library reaches the
 * phone through pairing in the Sync window.
 */
@Composable
fun DesktopAuthDialog(
    userManager: DesktopUserManager,
    onDismiss: () -> Unit
) {
    val currentUser by userManager.currentUser.collectAsState()
    val savedAccounts by userManager.savedAccounts.collectAsState()

    var newProfileName by remember { mutableStateOf("") }
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember(currentUser?.name) { mutableStateOf(currentUser?.name ?: "") }

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
                    text = if (currentUser == null) "Create your profile" else "Profiles",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(modifier = Modifier.width(440.dp).padding(vertical = 4.dp)) {
                Text(
                    text = "Profiles live on this Mac. To share your library with your phone, open Sync and pair the phone with the code shown there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA0AAB8)
                )
                Spacer(Modifier.height(14.dp))

                val user = currentUser
                if (user != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF131A26)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("CURRENT PROFILE", color = Color(0xFF8E9BB0), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            if (isEditingName) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NameField(editedName, { editedName = it }, "Profile name", Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (editedName.isNotBlank()) {
                                                userManager.updateProfileName(editedName)
                                                isEditingName = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ED760)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                    TextButton(onClick = { isEditingName = false; editedName = user.name }) {
                                        Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(user.avatarLetter)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        user.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { editedName = user.name; isEditingName = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rename", color = Color(0xFF64B5F6), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                val others = savedAccounts.filter { it.id != user?.id && !it.isGuest }
                if (others.isNotEmpty()) {
                    Text("OTHER PROFILES ON THIS MAC", color = Color(0xFF8E99A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (profile in others) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF20252E))
                                    .border(1.dp, Color(0xFF2E3540), RoundedCornerShape(12.dp))
                                    .clickable { userManager.switchAccount(profile); onDismiss() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(profile.avatarLetter)
                                Spacer(Modifier.width(12.dp))
                                Text(profile.name, color = Color.White, modifier = Modifier.weight(1f))
                                IconButton(onClick = { userManager.signOut(profile) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove profile", tint = Color(0xFF8E99A8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text("NEW PROFILE", color = Color(0xFF8E99A8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NameField(newProfileName, { newProfileName = it }, "Your name", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = newProfileName.isNotBlank(),
                        onClick = {
                            userManager.signIn(name = newProfileName.trim(), isGuest = false)
                            newProfileName = ""
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ED760), contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("Create", fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { userManager.signIn(name = "Guest User", isGuest = true); onDismiss() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA0AAB8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF384252)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Continue as Guest", fontSize = 12.sp)
                    }
                    if (user != null && !user.isGuest) {
                        TextButton(onClick = { userManager.signOut() }) {
                            Text("Remove this profile", color = Color(0xFFFF5252), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF161A22),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun Avatar(letter: String) {
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1ED760)),
        contentAlignment = Alignment.Center
    ) {
        Text(letter, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF262C36))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) Text(placeholder, color = Color.Gray, fontSize = 12.5.sp)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(40)) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 12.5.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
