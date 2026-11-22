package com.example.musicapp.data.cloud

import kotlinx.serialization.Serializable

/**
 * The cloud copy of a user's library lives in Firebase Realtime Database:
 *
 *   users/{uid}/data/playlists/{key}  = {json, at}   one playlist, as JSON
 *   users/{uid}/data/liked/{key}      = {json, at}   one liked song
 *   users/{uid}/data/artists/{key}    = {json, at}   one followed artist (its name)
 *   users/{uid}/data/shows/{key}      = {json, at}   one followed show / channel
 *   users/{uid}/data/profile          = {name, photo, photoAt}
 *   users/{uid}/grants/{deviceUid}    = linked computers allowed to read and write data/
 *
 * Every device keeps the "base": the library as it was after its last sync. Comparing base,
 * local and remote tells who changed what, so additions, edits and deletions on any device
 * reach every other device, and nothing needs a manual sync.
 */
object CloudPaths {
    const val PLAYLISTS = "playlists"
    const val LIKED = "liked"
    const val ARTISTS = "artists"
    const val SHOWS = "shows"
    val SECTIONS = listOf(PLAYLISTS, LIKED, ARTISTS, SHOWS)

    /** Realtime Database keys cannot contain . # $ [ ] or /. */
    fun key(raw: String): String = raw.trim().lowercase().ifBlank { "_" }
        .replace(Regex("[.#$\\[\\]/\\s]"), "_")
        .take(700)
}

@Serializable
data class RemoteItem(val json: String = "", val at: Long = 0L)

/** A library as key → JSON per section. */
@Serializable
data class LibrarySnapshot(val sections: Map<String, Map<String, String>> = emptyMap()) {
    fun section(name: String): Map<String, String> = sections[name].orEmpty()

    companion object {
        fun of(vararg pairs: Pair<String, Map<String, String>>) = LibrarySnapshot(pairs.toMap())
    }
}

data class MergeResult(
    /** What the local library should become. */
    val local: LibrarySnapshot,
    /** Writes for the cloud, as "section/key" → item, or null to delete. */
    val writes: Map<String, RemoteItem?>,
    /** New base to persist. */
    val base: LibrarySnapshot,
    val localChanged: Boolean
)

object LibraryMerge {
    /**
     * Three-way merge per item. If only one side changed an item since the base, that side
     * wins (including deletions). If both changed it differently, a surviving edit beats a
     * deletion, and otherwise this device's version wins.
     */
    fun merge(
        base: LibrarySnapshot,
        local: LibrarySnapshot,
        remote: Map<String, Map<String, RemoteItem>>,
        now: Long
    ): MergeResult {
        val writes = LinkedHashMap<String, RemoteItem?>()
        val newLocal = LinkedHashMap<String, Map<String, String>>()
        val newBase = LinkedHashMap<String, Map<String, String>>()
        var localChanged = false

        for (section in CloudPaths.SECTIONS) {
            val b = base.section(section)
            val l = local.section(section)
            val r = remote[section].orEmpty().mapValues { it.value.json }
            val keys = LinkedHashSet<String>().apply { addAll(l.keys); addAll(r.keys); addAll(b.keys) }
            val merged = LinkedHashMap<String, String>()
            for (key in keys) {
                val bv = b[key]
                val lv = l[key]
                val rv = r[key]
                val result = when {
                    lv == rv -> lv
                    lv == bv -> rv
                    rv == bv -> lv
                    lv == null -> rv
                    rv == null -> lv
                    else -> lv
                }
                if (result != null) merged[key] = result
                if (result != rv) writes["$section/$key"] = result?.let { RemoteItem(it, now) }
                if (result != lv) localChanged = true
            }
            // Keep local order for items that stayed; items new from the cloud go first
            // (newest first), which matches "recently added" for liked songs and shows.
            val remoteAt = remote[section].orEmpty()
            val ordered = LinkedHashMap<String, String>()
            merged.keys.filter { it !in l }.sortedByDescending { remoteAt[it]?.at ?: 0L }.forEach { ordered[it] = merged.getValue(it) }
            l.keys.filter { it in merged }.forEach { ordered[it] = merged.getValue(it) }
            newLocal[section] = ordered
            newBase[section] = ordered
        }
        return MergeResult(LibrarySnapshot(newLocal), writes, LibrarySnapshot(newBase), localChanged)
    }
}
