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
import com.google.firebase.auth.FirebaseAuth
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager

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
    val hasCompletedOnboarding: Boolean = false,
    /** Profile photo: a local file the user picked, or the Google account photo URL. */
    val photoUrl: String = "",
    /** True once the user picked their own photo (a Google photo never overrides it). */
    val hasCustomPhoto: Boolean = false,
    /** Firebase account behind a Google sign-in; the cloud library is stored under it. */
    val firebaseUid: String = ""
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

        fun resetForTesting(context: Context) {
            instance = null
            File(context.filesDir, "accounts.json").delete()
            context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }

        private const val PBKDF2_PREFIX = "pbkdf2_sha256"
        private const val PBKDF2_ITERATIONS = 120_000

        /** Legacy unsalted SHA-256 format, kept only to verify and upgrade older accounts. */
        fun hashPassword(password: String): String {
            if (password.isEmpty()) return ""
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** Salted PBKDF2 hash stored as "pbkdf2_sha256:iterations:saltHex:hashHex". */
        fun createPasswordHash(password: String): String {
            val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val hash = pbkdf2(password, salt, PBKDF2_ITERATIONS)
            return listOf(PBKDF2_PREFIX, PBKDF2_ITERATIONS.toString(), salt.toHex(), hash.toHex()).joinToString(":")
        }

        fun verifyPassword(password: String, stored: String): Boolean {
            if (password.isEmpty() || stored.isEmpty()) return false
            if (isLegacyPasswordHash(stored)) {
                return MessageDigest.isEqual(hashPassword(password).toByteArray(), stored.toByteArray())
            }
            val parts = stored.split(':')
            if (parts.size != 4) return false
            val iterations = parts[1].toIntOrNull() ?: return false
            val salt = parts[2].fromHex() ?: return false
            val expected = parts[3].fromHex() ?: return false
            return MessageDigest.isEqual(pbkdf2(password, salt, iterations), expected)
        }

        fun isLegacyPasswordHash(stored: String): Boolean =
            stored.isNotEmpty() && !stored.startsWith("$PBKDF2_PREFIX:")

        private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
            val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray? {
            if (length % 2 != 0) return null
            return runCatching { ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() } }.getOrNull()
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
            com.example.musicapp.data.storage.AtomicFiles.writeText(accountsFile, content)
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
        saveAccounts(updated)
        try {
            com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(user.id)
            com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(user.id)
            com.example.musicapp.data.sync.DeviceSyncManager.triggerSilentSync(context)
        } catch (e: Exception) {
            e.printStackTrace()
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

        val localUser = _allUsers.value.find {
            it.username.equals(clean, ignoreCase = true)
        } ?: return@runBlocking Result.failure(Exception("No account found with username \"$clean\" on this device"))

        // Accounts created through Google have no password. Previously any password was
        // accepted for them here, which let anyone open them by typing the username.
        if (localUser.passwordHash.isEmpty()) {
            return@runBlocking Result.failure(Exception("This account uses Google sign-in. Tap \"Continue with Google\"."))
        }
        if (!verifyPassword(password, localUser.passwordHash)) {
            return@runBlocking Result.failure(Exception("Incorrect password. Please try again."))
        }

        // Transparently upgrade old unsalted hashes on a successful login.
        val sessionUser = if (isLegacyPasswordHash(localUser.passwordHash)) {
            localUser.copy(passwordHash = createPasswordHash(password))
        } else localUser
        applySession(sessionUser)
        Result.success(sessionUser)
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

        val passHash = createPasswordHash(password)
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

    /** Creates or restores this device's Rivara profile after Firebase verifies the Google identity. */
    fun loginWithGoogle(uid: String, email: String, displayName: String, photoUrl: String = ""): UserProfile {
        require(uid.isNotBlank() || email.isNotBlank()) { "Google did not return a valid account." }
        val profileId = if (email.isNotBlank()) {
            "usr_" + email.lowercase().replace(Regex("[^a-z0-9]"), "_")
        } else {
            "google_$uid"
        }
        val existing = _allUsers.value.firstOrNull {
            it.id == profileId || it.id == "google_$uid" || (email.isNotBlank() && it.email.equals(email, ignoreCase = true))
        }
        val resolvedName = displayName.trim().ifBlank { email.substringBefore('@').ifBlank { "Listener" } }
        val usernameBase = email.substringBefore('@').lowercase()
            .replace(Regex("[^a-z0-9_.]"), "_")
            .take(20)
            .ifBlank { "listener" }
        val avatarColors = listOf("#1DB954", "#00A8E8", "#9C27B0", "#FF5722")
        val profile = existing?.copy(
            id = profileId,
            name = resolvedName,
            email = email,
            photoUrl = if (existing.hasCustomPhoto) existing.photoUrl else photoUrl,
            firebaseUid = uid,
            avatarLetter = resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
            isGuest = false
        ) ?: UserProfile(
            id = profileId,
            username = "${usernameBase}_${uid.takeLast(5)}",
            name = resolvedName,
            email = email,
            avatarLetter = resolvedName.firstOrNull()?.uppercaseChar()?.toString() ?: "L",
            avatarColorHex = avatarColors[_allUsers.value.size % avatarColors.size],
            photoUrl = photoUrl,
            firebaseUid = uid,
            isGuest = false,
            hasCompletedOnboarding = false
        )
        applySession(profile)
        return profile
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
        try {
            com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(guestId)
            com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(guestId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return guest
    }

    /** Updates the signed-in profile (and the saved account list) in one place. */
    fun updateCurrent(change: (UserProfile) -> UserProfile) {
        val updated = change(_currentUser.value)
        _currentUser.value = updated
        if (updated.isGuest) return
        val updatedList = _allUsers.value.map { if (it.id == updated.id) updated else it }
        _allUsers.value = updatedList
        saveAccounts(updatedList)
    }

    /**
     * Sets the profile photo from an image the user picked. The image is cropped to a square,
     * scaled to 512 px and saved as a JPEG in app storage.
     */
    fun setProfilePhoto(uri: android.net.Uri): Result<Unit> = runCatching {
        val user = _currentUser.value
        val bitmap = decodeSquare(uri, 512) ?: error("That image could not be opened.")
        val dir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
        // A new file name each time, so image caches show the new photo right away.
        val file = java.io.File(dir, "${user.id}_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        dir.listFiles()?.filter { it.name.startsWith(user.id + "_") && it != file }?.forEach { it.delete() }
        updateCurrent { it.copy(photoUrl = file.absolutePath, hasCustomPhoto = true) }
        com.example.musicapp.data.cloud.ProfilePhotoSync.photoChanged(context, file)
    }

    fun removeProfilePhoto() {
        val user = _currentUser.value
        java.io.File(context.filesDir, "avatars").listFiles()?.filter { it.name.startsWith(user.id + "_") }?.forEach { it.delete() }
        updateCurrent { it.copy(photoUrl = "", hasCustomPhoto = false) }
        com.example.musicapp.data.cloud.ProfilePhotoSync.photoChanged(context, null)
    }

    private fun decodeSquare(uri: android.net.Uri, size: Int): android.graphics.Bitmap? {
        val resolver = context.contentResolver
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        // Respect the photo's EXIF rotation (camera photos are often stored sideways).
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { androidx.exifinterface.media.ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val side = minOf(decoded.width, decoded.height)
        val matrix = android.graphics.Matrix().apply {
            val scale = size.toFloat() / side
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val cropped = android.graphics.Bitmap.createBitmap(
            decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side, matrix, true
        )
        if (cropped != decoded) decoded.recycle()
        return cropped
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
        saveAccounts(updatedList)
    }

    fun switchUser(userId: String) {
        val user = _allUsers.value.find { it.id == userId } ?: return
        _currentUser.value = user
        _isLoggedIn.value = true
        prefs.edit()
            .putString("active_user_id", userId)
            .putBoolean("is_logged_in", true)
            .apply()
        try {
            com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(userId)
            com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(userId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logout(userId: String? = null) {
        val current = _currentUser.value
        val userToLogout = userId?.let { id -> _allUsers.value.find { it.id == id } } ?: current

        // If logging out while in guest mode and no specific user ID was specified
        if (current.isGuest && (userId == null || userId == current.id)) {
            _isLoggedIn.value = false
            _currentUser.value = defaultGuest
            prefs.edit()
                .remove("active_user_id")
                .putBoolean("is_logged_in", false)
                .apply()
            try {
                com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(defaultGuest.id)
                com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(defaultGuest.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        // Remove this account from the device's saved accounts
        val remaining = _allUsers.value.filter { it.id != userToLogout.id && !it.isGuest }
        _allUsers.value = remaining
        saveAccounts(remaining)

        if (userToLogout.id.startsWith("google_") || userToLogout.id.startsWith("usr_")) {
            scope.launch {
                GoogleAuthManager.signOut(context)
            }
        }

        val isLoggingOutActiveUser = (userId == null || userId == current.id)

        if (isLoggingOutActiveUser) {
            if (remaining.isNotEmpty()) {
                // Seamlessly switch to another signed-in account without bouncing to login landing page
                val nextUser = remaining.first()
                _currentUser.value = nextUser
                _isLoggedIn.value = true
                prefs.edit()
                    .putString("active_user_id", nextUser.id)
                    .putBoolean("is_logged_in", true)
                    .apply()
                try {
                    com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(nextUser.id)
                    com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(nextUser.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                // No remaining accounts on device, return to login landing screen
                _isLoggedIn.value = false
                _currentUser.value = defaultGuest
                prefs.edit()
                    .remove("active_user_id")
                    .putBoolean("is_logged_in", false)
                    .apply()
                try {
                    com.example.musicapp.data.playlist.PlaylistManager.getInstance(context).switchUser(defaultGuest.id)
                    com.example.musicapp.data.artist.ArtistFollowManager.getInstance(context).switchUser(defaultGuest.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
