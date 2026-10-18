package com.example.musicapp.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.ui.auth.AuthDialog
import com.example.musicapp.ui.navigation.Screen

@Composable
fun ProfileDrawerContent(
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }
    val currentUser by userManager.currentUser.collectAsStateWithLifecycle()
    var showAuthDialog by remember { mutableStateOf(false) }

    val avatarColor = remember(currentUser.avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentUser.avatarColorHex))
        } catch (e: Exception) {
            Color(0xFFFF5722)
        }
    }

    if (showAuthDialog) {
        AuthDialog(onDismiss = { showAuthDialog = false })
    }

    ModalDrawerSheet(
        drawerContainerColor = Color(0xFF0F1520),
        drawerContentColor = Color.White,
        modifier = Modifier.width(320.dp).fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Profile Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAuthDialog = true }
                    .padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentUser.avatarLetter,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = currentUser.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "View profile / Switch",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF00F59B)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Add account
            DrawerItem(
                icon = Icons.Default.Add,
                title = "Add account",
                onClick = { showAuthDialog = true }
            )

            // Your Sound Capsule
            DrawerItem(
                icon = Icons.Default.ShowChart,
                title = "Your Sound Capsule",
                onClick = {
                    onClose()
                    onNavigate(Screen.SoundCapsule)
                }
            )

            // Recents
            DrawerItem(
                icon = Icons.Default.History,
                title = "Recents",
                onClick = {
                    onClose()
                    onNavigate(Screen.Recents)
                }
            )

            // Settings and privacy
            DrawerItem(
                icon = Icons.Default.Settings,
                title = "Settings and privacy",
                onClick = {
                    onClose()
                    onNavigate(Screen.Settings)
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Logout item
            DrawerItem(
                icon = Icons.Default.ExitToApp,
                title = "Log out",
                onClick = {
                    userManager.logout()
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White
        )
    }
}
