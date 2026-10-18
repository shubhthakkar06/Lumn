package com.example.musicapp.data.auth

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

@Serializable
data class UserProfile(
    val id: String,
    val username: String = "",
    val name: String = username,
    val email: String = "",
    val passwordHash: String = "",
    val avatarLetter: String = "U",
    val avatarColorHex: String = "#1ED760",
    val isPremium: Boolean = true,
    val isStudent: Boolean = false,
    val isGuest: Boolean = false,
    val selectedGenres: List<String> = emptyList(),
    val selectedArtists: List<String> = emptyList(),
    val hasCompletedOnboarding: Boolean = false
)

class UserManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val accountsFile = File(context.filesDir, "accounts.json")
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: UserManager? = null

        fun getInstance(context: Context): UserManager {
            return instance ?: synchronized(this) {
                instance ?: UserManager(context.applicationContext).also { instance = it }
            }
        }

        fun hashPassword(password: String): String {
            if (password.isEmpty()) return ""
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun isValidUsername(username: String): Boolean {
            val trimmed = username.trim()
            return trimmed.length in 3..30 && trimmed.matches(Regex("^[a-zA-Z0-9_.]+$"))
        }

        fun isValidEmail(email: String): Boolean {
            val trimmed = email.trim()
            val atIndex = trimmed.indexOf('@')
            val dotIndex = trimmed.lastIndexOf('.')
            return atIndex > 0 && dotIndex > atIndex + 1 && dotIndex < trimmed.length - 1
        }
    }

    private val defaultGuest = UserProfile(
        id = "guest_default",
        username = "Guest",
        name = "Guest",
        email = "",
        passwordHash = "",
        avatarLetter = "G",
        avatarColorHex = "#535353",
        isPremium = false,
        isStudent = false,
        isGuest = true,
        selectedGenres = emptyList(),
        selectedArtists = emptyList(),
        hasCompletedOnboarding = false
    )

    private val _allUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val allUsers: StateFlow<List<UserProfile>> = _allUsers.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile>(defaultGuest)
    val currentUser: StateFlow<UserProfile> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        try {
            if (accountsFile.exists()) {
                val content = accountsFile.readText()
                val loaded = json.decodeFromString<List<UserProfile>>(content)
                if (loaded.isNotEmpty()) {
                    _allUsers.value = loaded
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _allUsers.value = emptyList()
        }

        val savedLoggedIn = prefs.getBoolean("is_logged_in", false)
        val activeId = prefs.getString("active_user_id", "") ?: ""

        val active = _allUsers.value.find { it.id == activeId }
        if (savedLoggedIn && activeId.startsWith("guest_")) {
            _currentUser.value = defaultGuest
            _isLoggedIn.value = true
        } else if (active != null && savedLoggedIn) {
            _currentUser.value = active
            _isLoggedIn.value = true
        } else {
            _currentUser.value = defaultGuest
            _isLoggedIn.value = false
        }

    }

    private fun saveAccounts(users: List<UserProfile>) {
        try {
            val content = json.encodeToString(users.filter { !it.isGuest })
            accountsFile.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySession(user: UserProfile) {
        val updated = _allUsers.value.filter { it.id != user.id && !it.isGuest } + user
        _allUsers.value = updated
        _currentUser.value = user
        _isLoggedIn.value = true
        prefs.edit()
            .putString("active_user_id", user.id)
            .putBoolean("is_logged_in", true)
            .apply()
        scope.launch {
            saveAccounts(updated)
        }
    }

    private fun createLocalUser(username: String, passwordHash: String): UserProfile {
        val letter = username.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
        val colors = listOf("#1DB954", "#FF5722", "#9C27B0", "#2196F3")
        val colorHex = colors[_allUsers.value.size % colors.size]
        return UserProfile(
            id = "user_" + System.currentTimeMillis(),
            username = username,
            name = username,
            email = "",
            passwordHash = passwordHash,
            avatarLetter = letter,
            avatarColorHex = colorHex,
            isPremium = true,
            isStudent = false,
            isGuest = false,
            selectedGenres = emptyList(),
            selectedArtists = emptyList(),
            hasCompletedOnboarding = false
        )
    }

    fun login(username: String, password: String): Result<UserProfile> = runBlocking {
        val clean = username.trim()
        if (clean.isBlank()) {
            return@runBlocking Result.failure(Exception("Please enter your username"))
        }

        val passHash = hashPassword(password)
        val localUser = _allUsers.value.find {
            it.username.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
        } ?: return@runBlocking Result.failure(Exception("No account found with username \"$clean\" on this device"))

        if (localUser.passwordHash.isNotEmpty() && localUser.passwordHash != passHash) {
            return@runBlocking Result.failure(Exception("Incorrect password. Please try again."))
        }

        applySession(localUser)
        Result.success(localUser)
    }

    fun signup(username: String, password: String): Result<UserProfile> = runBlocking {
        val clean = username.trim()
        if (clean.isBlank()) {
            return@runBlocking Result.failure(Exception("Please enter a username"))
        }
        if (!isValidUsername(clean)) {
            return@runBlocking Result.failure(Exception("Username must be 3-30 characters and can only contain letters, numbers, underscores, and dots"))
        }
        if (password.length < 6) {
            return@runBlocking Result.failure(Exception("Password must be at least 6 characters"))
        }

        val passHash = hashPassword(password)
        val existingLocal = _allUsers.value.find {
            it.username.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
        }
        if (existingLocal != null && !existingLocal.isGuest) {
            return@runBlocking Result.failure(Exception("Username \"$clean\" is already taken. Please choose another."))
        }

        val profile = createLocalUser(clean, passHash)
        applySession(profile)
        Result.success(profile)
    }

    fun loginAsGuest(): UserProfile {
        val guestId = "guest_" + System.currentTimeMillis()
        val guest = UserProfile(
            id = guestId,
            username = "Guest",
            name = "Guest",
            email = "",
            passwordHash = "",
            avatarLetter = "G",
            avatarColorHex = "#535353",
            isPremium = false,
            isStudent = false,
            isGuest = true,
            selectedGenres = emptyList(),
            selectedArtists = emptyList(),
            hasCompletedOnboarding = false
        )
        _currentUser.value = guest
        _isLoggedIn.value = true
        prefs.edit()
            .putString("active_user_id", guestId)
            .putBoolean("is_logged_in", true)
            .apply()
        return guest
    }

    fun updateInterests(genres: List<String>, artists: List<String>) {
        val current = _currentUser.value
        val updated = current.copy(
            selectedGenres = genres,
            selectedArtists = artists,
            hasCompletedOnboarding = true
        )
        _currentUser.value = updated
        val updatedList = _allUsers.value.map { if (it.id == updated.id) updated else it }
        _allUsers.value = updatedList
        scope.launch {
            saveAccounts(updatedList)
        }
    }

    fun switchUser(userId: String) {
        val user = _allUsers.value.find { it.id == userId } ?: return
        _currentUser.value = user
        _isLoggedIn.value = true
        prefs.edit()
            .putString("active_user_id", userId)
            .putBoolean("is_logged_in", true)
            .apply()
    }

    fun logout() {
        _isLoggedIn.value = false
        _currentUser.value = defaultGuest
        prefs.edit().putBoolean("is_logged_in", false).apply()
    }
}
