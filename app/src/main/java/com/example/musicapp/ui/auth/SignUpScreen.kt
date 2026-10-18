package com.example.musicapp.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicapp.data.auth.UserManager

@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSignUpSuccess: () -> Unit
) {
    val context = LocalContext.current
    val userManager = remember { UserManager.getInstance(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var agreeTerms by remember { mutableStateOf(true) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier.size(38.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(com.example.musicapp.R.drawable.lumn_launcher_icon),
                        contentDescription = "Lumn app logo",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Create account",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error banner if any
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE22134),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Field 1: Choose a username (NO EMAIL ASKED)
            Text(
                text = "Choose a username",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMessage = null
                },
                placeholder = { Text("username", color = Color(0xFFA0A0A0)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1ED760),
                    unfocusedBorderColor = Color(0xFF535353),
                    focusedContainerColor = Color(0xFF282828),
                    unfocusedContainerColor = Color(0xFF282828)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Use 3 to 30 characters (letters, numbers, underscores).",
                color = Color(0xFFA0A0A0),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Field 2: Password
            Text(
                text = "Create a password",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                placeholder = { Text("Password (at least 6 characters)", color = Color(0xFFA0A0A0)) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility",
                            tint = Color(0xFFA0A0A0)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF1ED760),
                    unfocusedBorderColor = Color(0xFF535353),
                    focusedContainerColor = Color(0xFF282828),
                    unfocusedContainerColor = Color(0xFF282828)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Use at least 6 characters.",
                color = Color(0xFFA0A0A0),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Terms checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Checkbox(
                    checked = agreeTerms,
                    onCheckedChange = { agreeTerms = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF1ED760),
                        checkmarkColor = Color.Black,
                        uncheckedColor = Color(0xFF535353)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I agree to the Lumn Terms of Service and Privacy Policy.",
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }

            // Create account button
            Button(
                onClick = {
                    if (!agreeTerms) {
                        errorMessage = "Please accept the Terms of Service to proceed"
                        return@Button
                    }
                    isLoading = true
                    val result = userManager.signup(username, password)
                    isLoading = false
                    result.fold(
                        onSuccess = {
                            onSignUpSuccess()
                        },
                        onFailure = { error ->
                            errorMessage = error.message ?: "Sign up failed"
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1ED760),
                    contentColor = Color.Black
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Text(
                        text = "Create account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
