package com.example.musicapp.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopUser(
    val id: String,
    val name: String,
    val email: String = "",
    val avatarColorHex: String = "#1ED760",
    val isGuest: Boolean = false
) {
    val avatarLetter: String
        get() = name.trim().firstOrNull()?.uppercase() ?: "U"
}

class DesktopUserManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dataDir = File(System.getProperty("user.home"), ".rivara").apply { mkdirs() }
    private val userFile = File(dataDir, "user.json")
    private val accountsFile = File(dataDir, "accounts.json")

    private val _currentUser = MutableStateFlow<DesktopUser?>(null)
    val currentUser: StateFlow<DesktopUser?> = _currentUser.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<DesktopUser>>(emptyList())
    val savedAccounts: StateFlow<List<DesktopUser>> = _savedAccounts.asStateFlow()

    init {
        loadUsers()
    }

    private fun loadUsers() {
        try {
            if (accountsFile.exists()) {
                val list = json.decodeFromString<List<DesktopUser>>(accountsFile.readText())
                _savedAccounts.value = list
            }
            if (userFile.exists()) {
                val user = json.decodeFromString<DesktopUser>(userFile.readText())
                _currentUser.value = user
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun signIn(name: String, email: String = "", isGuest: Boolean = false) {
        val id = if (isGuest) {
            "guest_${System.currentTimeMillis()}"
        } else if (email.isNotBlank()) {
            "usr_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
        } else {
            "usr_" + name.lowercase().replace(Regex("[^a-z0-9]"), "_")
        }
        val user = DesktopUser(
            id = id,
            name = name.ifBlank { if (isGuest) "Guest User" else "Music Lover" },
            email = email,
            avatarColorHex = "#1ED760",
            isGuest = isGuest
        )
        _currentUser.value = user

        val currentAccounts = _savedAccounts.value.filter { it.id != user.id }
        _savedAccounts.value = listOf(user) + currentAccounts

        save()
    }

    fun updateProfileName(newName: String) {
        val current = _currentUser.value ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val updated = current.copy(name = trimmed)
        _currentUser.value = updated
        _savedAccounts.value = _savedAccounts.value.map {
            if (it.id == updated.id || (it.email.isNotBlank() && it.email.equals(updated.email, ignoreCase = true))) updated else it
        }
        save()
    }

    fun switchAccount(user: DesktopUser) {
        _currentUser.value = user
        save()
    }

    fun signOut(account: DesktopUser? = null) {
        val target = account ?: _currentUser.value ?: return
        val remaining = _savedAccounts.value.filter { it.id != target.id }
        _savedAccounts.value = remaining

        if (_currentUser.value?.id == target.id) {
            _currentUser.value = remaining.firstOrNull()
        }
        save()
    }

    private fun save() {
        try {
            _currentUser.value?.let {
                userFile.writeText(json.encodeToString(it))
            } ?: run {
                if (userFile.exists()) userFile.delete()
            }
            accountsFile.writeText(json.encodeToString(_savedAccounts.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
