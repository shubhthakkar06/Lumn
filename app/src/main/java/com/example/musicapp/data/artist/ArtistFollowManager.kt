package com.example.musicapp.data.artist

import android.content.Context
import android.content.SharedPreferences
import com.example.musicapp.data.auth.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArtistFollowManager private constructor(context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("artist_follow_prefs", Context.MODE_PRIVATE)

    private val userManager = UserManager.getInstance(context)
    private var activeUserId: String = userManager.currentUser.value.id

    private val _followedArtists = MutableStateFlow<Set<String>>(loadFollowedArtists(activeUserId))
    val followedArtists: StateFlow<Set<String>> = _followedArtists.asStateFlow()

    init {
        scope.launch(Dispatchers.Main.immediate) {
            userManager.currentUser.collect { user ->
                if (user.id != activeUserId) {
                    activeUserId = user.id
                    _followedArtists.value = loadFollowedArtists(user.id)
                }
            }
        }
    }

    private fun getFollowKey(userId: String): String =
        "followed_artists_" + userId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "default" }

    private fun loadFollowedArtists(userId: String): Set<String> {
        val userKey = getFollowKey(userId)
        val raw = prefs.getStringSet(userKey, null)
        if (raw != null) {
            return raw.map { it.lowercase() }.toSet()
        }
        // Migrate legacy global preferences if migrating existing profile
        val legacy = prefs.getStringSet(KEY_FOLLOWED, null)
        if (legacy != null && !userId.startsWith("guest_")) {
            val migrated = legacy.map { it.lowercase() }.toSet()
            prefs.edit().putStringSet(userKey, migrated).remove(KEY_FOLLOWED).apply()
            return migrated
        }
        return emptySet()
    }

    fun switchUser(userId: String) {
        if (userId != activeUserId) {
            activeUserId = userId
            _followedArtists.value = loadFollowedArtists(userId)
        }
    }

    fun isFollowing(artistName: String): Boolean {
        return _followedArtists.value.contains(artistName.trim().lowercase())
    }

    fun toggleFollow(artistName: String): Boolean {
        val cleanName = artistName.trim().lowercase()
        val current = _followedArtists.value
        val isNowFollowing = if (current.contains(cleanName)) {
            current - cleanName
        } else {
            current + cleanName
        }
        _followedArtists.value = isNowFollowing
        prefs.edit().putStringSet(getFollowKey(activeUserId), isNowFollowing).apply()
        return isNowFollowing.contains(cleanName)
    }

    fun follow(artistName: String) {
        val cleanName = artistName.trim().lowercase()
        val current = _followedArtists.value
        if (!current.contains(cleanName)) {
            val updated = current + cleanName
            _followedArtists.value = updated
            prefs.edit().putStringSet(getFollowKey(activeUserId), updated).apply()
        }
    }

    fun unfollow(artistName: String) {
        val cleanName = artistName.trim().lowercase()
        val current = _followedArtists.value
        if (current.contains(cleanName)) {
            val updated = current - cleanName
            _followedArtists.value = updated
            prefs.edit().putStringSet(getFollowKey(activeUserId), updated).apply()
        }
    }

    companion object {
        private const val KEY_FOLLOWED = "followed_artists_set"

        @Volatile
        private var instance: ArtistFollowManager? = null

        fun getInstance(context: Context): ArtistFollowManager {
            return instance ?: synchronized(this) {
                instance ?: ArtistFollowManager(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(context: Context) {
            instance = null
            context.getSharedPreferences("artist_follow_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
