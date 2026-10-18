package com.example.musicapp.data.artist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ArtistFollowManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("artist_follow_prefs", Context.MODE_PRIVATE)

    private val _followedArtists = MutableStateFlow<Set<String>>(loadFollowedArtists())
    val followedArtists: StateFlow<Set<String>> = _followedArtists.asStateFlow()

    private fun loadFollowedArtists(): Set<String> {
        val raw = prefs.getStringSet(KEY_FOLLOWED, null) ?: emptySet()
        return raw.map { it.lowercase() }.toSet()
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
        prefs.edit().putStringSet(KEY_FOLLOWED, isNowFollowing).apply()
        return isNowFollowing.contains(cleanName)
    }

    fun follow(artistName: String) {
        val cleanName = artistName.trim().lowercase()
        val current = _followedArtists.value
        if (!current.contains(cleanName)) {
            val updated = current + cleanName
            _followedArtists.value = updated
            prefs.edit().putStringSet(KEY_FOLLOWED, updated).apply()
        }
    }

    fun unfollow(artistName: String) {
        val cleanName = artistName.trim().lowercase()
        val current = _followedArtists.value
        if (current.contains(cleanName)) {
            val updated = current - cleanName
            _followedArtists.value = updated
            prefs.edit().putStringSet(KEY_FOLLOWED, updated).apply()
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
    }
}
