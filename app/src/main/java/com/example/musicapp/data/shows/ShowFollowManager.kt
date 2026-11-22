package com.example.musicapp.data.shows

import android.content.Context
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.cloud.CloudJson
import com.example.musicapp.data.cloud.FollowedShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** Podcasts and YouTube channels the user follows, per account, synced to the cloud. */
class ShowFollowManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("followed_shows", Context.MODE_PRIVATE)
    private val userManager = UserManager.getInstance(context)
    private var activeUserId = userManager.currentUser.value.id
    private val serializer = ListSerializer(FollowedShow.serializer())

    private val _shows = MutableStateFlow(load(activeUserId))
    val shows: StateFlow<List<FollowedShow>> = _shows.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            userManager.currentUser.collect { user ->
                if (user.id != activeUserId) {
                    activeUserId = user.id
                    _shows.value = load(user.id)
                }
            }
        }
    }

    fun isFollowing(id: String): Boolean = _shows.value.any { it.id == id }

    fun toggle(show: FollowedShow): Boolean {
        val following = isFollowing(show.id)
        save(if (following) _shows.value.filterNot { it.id == show.id } else listOf(show.copy(followedAtMs = System.currentTimeMillis())) + _shows.value)
        com.example.musicapp.data.cloud.CloudSync.get(context).localChanged()
        return !following
    }

    fun replaceFromSync(shows: List<FollowedShow>) = save(shows)

    private fun key(userId: String) = "shows_" + userId.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    private fun load(userId: String): List<FollowedShow> = prefs.getString(key(userId), null)
        ?.let { runCatching { CloudJson.json.decodeFromString(serializer, it) }.getOrNull() }
        .orEmpty()

    private fun save(shows: List<FollowedShow>) {
        _shows.value = shows
        prefs.edit().putString(key(activeUserId), CloudJson.json.encodeToString(serializer, shows)).apply()
    }

    companion object {
        @Volatile
        private var instance: ShowFollowManager? = null

        fun getInstance(context: Context): ShowFollowManager = instance ?: synchronized(this) {
            instance ?: ShowFollowManager(context.applicationContext).also { instance = it }
        }
    }
}
