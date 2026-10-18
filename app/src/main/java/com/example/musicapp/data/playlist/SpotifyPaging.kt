package com.example.musicapp.data.playlist

import java.net.URI
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Returns only Spotify's absolute playlist paging URL; JSON null ends pagination. */
internal fun parseSpotifyNextPageUrl(page: JsonObject): String? {
    val element = page["next"] ?: return null
    if (element is JsonNull) return null
    val nextUrl = (element as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val uri = runCatching { URI(nextUrl) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host != "api.spotify.com") return null
    if (!uri.path.startsWith("/v1/playlists/")) return null
    return nextUrl
}
