package com.example.musicapp.desktop.data

import com.example.musicapp.data.cloud.FollowedShow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Shows followed on the Mac (synced with the phone through the cloud library). */
object DesktopShowFollows {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(System.getProperty("user.home"), ".rivara/followed_shows.json")
    private val serializer = ListSerializer(FollowedShow.serializer())
    private val _shows = MutableStateFlow(runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList()))
    val shows: StateFlow<List<FollowedShow>> = _shows.asStateFlow()

    fun isFollowing(id: String) = _shows.value.any { it.id == id }

    fun toggle(show: FollowedShow) {
        replace(if (isFollowing(show.id)) _shows.value.filterNot { it.id == show.id } else listOf(show.copy(followedAtMs = System.currentTimeMillis())) + _shows.value)
    }

    fun replace(shows: List<FollowedShow>) {
        _shows.value = shows
        runCatching { file.parentFile.mkdirs(); file.writeText(json.encodeToString(serializer, shows)) }
    }
}
