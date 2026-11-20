package com.example.musicapp.data.recommend

import android.content.Context
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.catalog.AlbumRef
import com.example.musicapp.data.catalog.ArtistRef
import com.example.musicapp.data.catalog.DeezerArtists
import com.example.musicapp.data.catalog.SaavnCatalog
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.provider.ProviderRouter
import com.example.musicapp.data.provider.TrackMatching
import com.example.musicapp.data.settings.SettingsManager
import com.example.musicapp.data.storage.AtomicFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln

/** A personal mix shown on Home. [id] is what [RecommendationEngine.mixSongs] understands. */
@Serializable
data class MixCard(val id: String, val title: String, val subtitle: String, val imageUrl: String, val reason: String = "")

/**
 * On-device recommendations.
 *
 * 1. A taste profile is built from what you finish, skip, like and follow (recent listens
 *    count more), plus the artists picked at sign-up.
 * 2. Candidates come from JioSaavn's "more like this" and radio stations, artists' top songs,
 *    and related artists (JioSaavn, then Deezer).
 * 3. Candidates are ranked by fit with your taste, how new they are to you (controlled by the
 *    Discovery setting), recent plays and skips, then spread out so one artist doesn't dominate.
 */
class RecommendationEngine private constructor(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pidCache = ConcurrentHashMap<String, String>()
    private val artistCache = ConcurrentHashMap<String, ArtistRef>()

    // ---------------------------------------------------------------- taste

    data class Taste(
        val artistAffinity: Map<String, Double>,
        val playCounts: Map<String, Int>,
        val skipCounts: Map<String, Int>,
        val recentlyPlayed: Set<String>,
        val languageShare: Map<String, Double>,
        val favoriteSongs: List<SongInfo>,
        val topArtists: List<String>
    ) {
        val maxAffinity: Double = artistAffinity.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    }

    private var tasteCache: Pair<Long, Taste>? = null

    fun taste(): Taste {
        tasteCache?.takeIf { System.currentTimeMillis() - it.first < 60_000 }?.let { return it.second }
        val user = UserManager.getInstance(context).currentUser.value
        val now = System.currentTimeMillis()
        val events = ListeningLog.getInstance(context).forUser(user.id)
        val liked = PlaylistManager.getInstance(context).likedSongs.value
        val followed = ArtistFollowManager.getInstance(context).followedArtists.value

        val affinity = HashMap<String, Double>()
        val plays = HashMap<String, Int>()
        val skips = HashMap<String, Int>()
        val recent = HashSet<String>()
        val langs = HashMap<String, Double>()
        val songScore = HashMap<String, Double>()
        val songById = HashMap<String, SongInfo>()

        for (e in events) {
            val ageDays = (now - e.atMs) / 86_400_000.0
            val decay = exp(-ageDays / 21.0) // ~3-week memory
            val weight = when {
                e.skipped -> -0.7
                e.completed -> 1.0
                e.durationMs > 0 && e.listenedMs > e.durationMs / 2 -> 0.6
                else -> 0.2
            }
            val artist = primaryArtist(e.artist)
            affinity[artist] = (affinity[artist] ?: 0.0) + weight * decay
            if (e.skipped) skips[e.videoId] = (skips[e.videoId] ?: 0) + 1 else plays[e.videoId] = (plays[e.videoId] ?: 0) + 1
            if (ageDays < 3) recent += e.videoId
            if (e.language.isNotBlank() && !e.skipped) langs[e.language] = (langs[e.language] ?: 0.0) + decay
            if (!e.skipped) {
                songScore[e.videoId] = (songScore[e.videoId] ?: 0.0) + weight * decay
                songById[e.videoId] = SongInfo(e.videoId, e.title, e.artist, "", language = e.language)
            }
        }
        liked.forEach { s ->
            val a = primaryArtist(s.artist)
            affinity[a] = (affinity[a] ?: 0.0) + 2.0
            songScore[s.videoId] = (songScore[s.videoId] ?: 0.0) + 2.0
            songById[s.videoId] = s
            if (s.language.isNotBlank()) langs[s.language] = (langs[s.language] ?: 0.0) + 0.5
        }
        followed.forEach { a -> affinity[a.lowercase()] = (affinity[a.lowercase()] ?: 0.0) + 3.0 }
        user.selectedArtists.forEach { a -> affinity[a.lowercase()] = (affinity[a.lowercase()] ?: 0.0) + 1.5 }

        val totalLang = langs.values.sum().takeIf { it > 0 } ?: 1.0
        val taste = Taste(
            artistAffinity = affinity,
            playCounts = plays,
            skipCounts = skips,
            recentlyPlayed = recent,
            languageShare = langs.mapValues { it.value / totalLang },
            favoriteSongs = songScore.entries.sortedByDescending { it.value }.mapNotNull { songById[it.key] }.take(25),
            topArtists = affinity.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key }.take(12)
        )
        tasteCache = now to taste
        return taste
    }

    fun invalidateTaste() {
        tasteCache = null
    }

    /** 0 = stick to what I know, 1 = surprise me. */
    private fun explore(): Double = SettingsManager.getInstance(context).discoveryLevel.value / 100.0

    // ---------------------------------------------------------------- ranking

    /**
     * Ranks [candidates] (best source first) for this listener and spreads artists out.
     * [noveltyBias] adds to the user's Discovery setting for feeds that are meant to be new.
     */
    fun rank(candidates: List<SongInfo>, limit: Int, exclude: Set<String> = emptySet(), noveltyBias: Double = 0.0): List<SongInfo> {
        val taste = taste()
        val explore = (explore() + noveltyBias).coerceIn(0.0, 1.0)
        val seenTitles = HashSet<String>()
        val scored = candidates.asSequence()
            .filter { it.videoId !in exclude && it.title.isNotBlank() }
            .distinctBy { it.videoId }
            .mapIndexed { index, song ->
                val artist = primaryArtist(song.artist)
                val familiarity = ((taste.artistAffinity[artist] ?: 0.0) / taste.maxAffinity).coerceIn(-1.0, 1.0)
                val plays = taste.playCounts[song.videoId] ?: 0
                val skips = taste.skipCounts[song.videoId] ?: 0
                var score = 1.0 - index * 0.004 // keep some of the source's own ordering
                score += familiarity * (0.8 - 0.6 * explore)
                score += if (plays == 0) 0.5 * explore else -0.15 * plays * explore
                if (song.videoId in taste.recentlyPlayed) score -= 1.0
                score -= 0.8 * skips
                if (taste.languageShare.isNotEmpty() && song.language.isNotBlank()) {
                    score += 0.4 * (taste.languageShare[song.language] ?: -0.25)
                }
                score += 0.02 * ln(1.0 + song.popularityScore / 1_000_000.0)
                song to score
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()

        // Diversity: at most ~2 songs per artist in every 10, no duplicate titles.
        val perArtistCap = maxOf(2, limit / 5)
        val perArtist = HashMap<String, Int>()
        val out = ArrayList<SongInfo>(limit)
        val deferred = ArrayList<SongInfo>()
        for (song in scored) {
            val titleKey = TrackMatching.normalize(song.title)
            if (!seenTitles.add(titleKey)) continue
            val artist = primaryArtist(song.artist)
            val count = perArtist[artist] ?: 0
            if (count >= perArtistCap) {
                deferred += song
                continue
            }
            perArtist[artist] = count + 1
            out += song
            if (out.size >= limit) return out
        }
        out += deferred.take(limit - out.size)
        return out
    }

    // ---------------------------------------------------------------- building blocks

    /** JioSaavn id for a song, finding the matching JioSaavn recording if needed. */
    suspend fun saavnPid(song: SongInfo): String? {
        if (song.videoId.startsWith("saavn_")) return song.videoId.removePrefix("saavn_")
        pidCache[song.videoId]?.let { return it }
        val hits = runCatching {
            com.example.musicapp.data.provider.SaavnProvider().search("${song.title} ${song.artist}")
        }.getOrDefault(emptyList())
        val match = hits.firstOrNull {
            it.videoId.startsWith("saavn_") &&
                TrackMatching.confidence(song.title, song.artist, song.durationSeconds, it) >= TrackMatching.MIN_CONFIDENCE
        } ?: return null
        return match.videoId.removePrefix("saavn_").also { pidCache[song.videoId] = it }
    }

    suspend fun artist(name: String): ArtistRef? {
        val key = primaryArtist(name)
        artistCache[key]?.let { return it }
        return SaavnCatalog.findArtist(name)?.also { artistCache[key] = it }
    }

    private suspend fun similarArtistNames(name: String, limit: Int): List<String> {
        val ref = artist(name)
        val fromSaavn = ref?.let { SaavnCatalog.artistPage(it.id)?.similar?.map { s -> s.name } }.orEmpty()
        if (fromSaavn.size >= limit) return fromSaavn.take(limit)
        val fromDeezer = runCatching { DeezerArtists.relatedArtistNames(name, limit) }.getOrDefault(emptyList())
        return (fromSaavn + fromDeezer).distinctBy { it.lowercase() }.take(limit)
    }

    private suspend fun topSongsOf(name: String, limit: Int): List<SongInfo> {
        val ref = artist(name) ?: return emptyList()
        return SaavnCatalog.artistPage(ref.id)?.topSongs.orEmpty().take(limit)
    }

    // ---------------------------------------------------------------- features

    /** Songs to append when the queue is about to end (autoplay). */
    suspend fun radioContinuation(seeds: List<SongInfo>, exclude: Set<String>, count: Int): List<SongInfo> = withContext(Dispatchers.IO) {
        val pids = seeds.takeLast(3).mapNotNull { saavnPid(it) }
        val candidates = coroutineScope {
            val reco = pids.map { async { SaavnCatalog.recommendations(it) } }
            val station = async { SaavnCatalog.station(pids, 20) }
            reco.awaitAll().flatten() + station.await()
        }
        val ranked = rank(candidates, count, exclude)
        ranked.ifEmpty { fallbackByArtist(seeds.lastOrNull(), exclude, count) }
    }

    private suspend fun fallbackByArtist(seed: SongInfo?, exclude: Set<String>, count: Int): List<SongInfo> {
        seed ?: return emptyList()
        val artist = primaryArtist(seed.artist)
        return ProviderRouter.shared.search(seed.artist.split(",").first())
            .filter { it.videoId !in exclude && primaryArtist(it.artist) == artist }
            .take(count)
    }

    /** "Song radio": endless music like one song. */
    suspend fun songRadio(seed: SongInfo, count: Int = 40): List<SongInfo> = withContext(Dispatchers.IO) {
        val pid = saavnPid(seed)
        val candidates = coroutineScope {
            val reco = async { pid?.let { SaavnCatalog.recommendations(it) }.orEmpty() }
            val station = async { pid?.let { SaavnCatalog.station(listOf(it), 30) }.orEmpty() }
            val artist = async { topSongsOf(seed.artist, 6) }
            reco.await() + station.await() + artist.await()
        }
        listOf(seed) + rank(candidates, count - 1, exclude = setOf(seed.videoId))
    }

    /** "Artist radio": the artist plus similar artists. */
    suspend fun artistRadio(name: String, count: Int = 40): List<SongInfo> = withContext(Dispatchers.IO) {
        val own = topSongsOf(name, 12)
        val similar = similarArtistNames(name, 5)
        val others = coroutineScope { similar.map { async { topSongsOf(it, 6) } }.awaitAll().flatten() }
        val station = SaavnCatalog.station(own.take(3).mapNotNull { saavnPid(it) }, 20)
        rank(own.take(6) + station + others + own.drop(6), count, noveltyBias = 0.1)
    }

    /** Up to four Daily Mixes, each built around one of your favourite artists. */
    suspend fun dailyMixes(): List<MixCard> = withContext(Dispatchers.IO) {
        val taste = taste()
        val user = UserManager.getInstance(context).currentUser.value
        val seeds = (taste.topArtists + user.selectedArtists.map { it.lowercase() }).distinct().take(4)
        coroutineScope {
            seeds.mapIndexed { i, seed ->
                async {
                    val ref = artist(seed) ?: return@async null
                    val related = similarArtistNames(ref.name, 3)
                    MixCard(
                        id = "daily:${i + 1}:${ref.name}",
                        title = "Daily Mix ${i + 1}",
                        subtitle = (listOf(ref.name) + related).take(3).joinToString(", ") + " and more",
                        imageUrl = ref.imageUrl,
                        reason = "Because you listen to ${ref.name}"
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun dailyMixSongs(artistName: String): List<SongInfo> = coroutineScope {
        val own = async { topSongsOf(artistName, 15) }
        val related = similarArtistNames(artistName, 4)
        val others = related.map { async { topSongsOf(it, 6) } }
        val favoritesByArtist = taste().favoriteSongs.filter { primaryArtist(it.artist) == primaryArtist(artistName) }.take(3)
        val reco = favoritesByArtist.mapNotNull { saavnPid(it) }.map { async { SaavnCatalog.recommendations(it) } }
        val pool = own.await() + reco.awaitAll().flatten() + others.awaitAll().flatten()
        rank(pool, 30, noveltyBias = -0.2)
    }

    /** New-to-you songs, stable for the week (like Discover Weekly). */
    suspend fun freshFinds(): List<SongInfo> = withContext(Dispatchers.IO) {
        val user = UserManager.getInstance(context).currentUser.value
        val cal = Calendar.getInstance()
        val key = "${user.id}:${cal.get(Calendar.YEAR)}:${cal.get(Calendar.WEEK_OF_YEAR)}"
        loadCached("fresh_finds.json", key)?.let { return@withContext it }

        val taste = taste()
        val seeds = taste.favoriteSongs.take(8).ifEmpty {
            user.selectedArtists.take(3).flatMap { topSongsOf(it, 2) }
        }
        val pids = seeds.mapNotNull { saavnPid(it) }
        val candidates = coroutineScope {
            val reco = pids.map { async { SaavnCatalog.recommendations(it) } }
            val station = async { SaavnCatalog.station(pids.take(5), 30) }
            reco.awaitAll().flatten() + station.await()
        }
        val familiar = taste.topArtists.take(3).toSet()
        val fresh = candidates.filter { song ->
            val credits = song.artist.lowercase()
            (taste.playCounts[song.videoId] ?: 0) == 0 && familiar.none { credits.contains(it) }
        }
        val result = rank(fresh, 30, noveltyBias = 0.4)
        if (result.isNotEmpty()) saveCached("fresh_finds.json", key, result)
        result
    }

    /** Recent releases from artists you follow or play most. */
    suspend fun releaseRadarAlbums(): List<AlbumRef> = withContext(Dispatchers.IO) {
        val taste = taste()
        val followed = ArtistFollowManager.getInstance(context).followedArtists.value
        val artists = (followed + taste.topArtists).distinct().take(10)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        coroutineScope {
            artists.map { name ->
                async {
                    val ref = artist(name) ?: return@async emptyList()
                    SaavnCatalog.artistPage(ref.id, latestFirst = true)?.albums.orEmpty()
                        .filter { (it.year.toIntOrNull() ?: 0) >= year - 1 }
                        .take(3)
                }
            }.awaitAll().flatten()
        }.distinctBy { it.id }.sortedByDescending { it.releaseDate.ifBlank { it.year } }.take(20)
    }

    private suspend fun releaseRadarSongs(): List<SongInfo> = coroutineScope {
        val albums = releaseRadarAlbums().take(12)
        albums.map { async { SaavnCatalog.album(it.id)?.songs.orEmpty().take(2) } }.awaitAll().flatten()
    }

    /** "Because you listened to …": a recent favourite and songs like it. */
    suspend fun becauseYouListened(): Pair<SongInfo, List<SongInfo>>? = withContext(Dispatchers.IO) {
        val seed = taste().favoriteSongs.firstOrNull() ?: return@withContext null
        val songs = songRadio(seed, 13).drop(1)
        if (songs.isEmpty()) null else seed to songs
    }

    /** Songs for any mix id shown in the app. */
    suspend fun mixSongs(mixId: String): List<SongInfo> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(45_000) {
            when {
                mixId.startsWith("daily:") -> dailyMixSongs(mixId.substringAfter(':').substringAfter(':'))
                mixId == "fresh" -> freshFinds()
                mixId == "radar" -> releaseRadarSongs()
                mixId.startsWith("radio:artist:") -> artistRadio(mixId.removePrefix("radio:artist:"))
                mixId.startsWith("radio:song:") -> runCatching { json.decodeFromString<SongInfo>(mixId.removePrefix("radio:song:")) }
                    .getOrNull()?.let { songRadio(it) }.orEmpty()
                mixId.startsWith("playlist:") -> SaavnCatalog.playlist(mixId.removePrefix("playlist:"))?.songs.orEmpty()
                mixId.startsWith("channel:") -> {
                    val lists = SaavnCatalog.channelPlaylists(mixId.removePrefix("channel:")).take(3)
                    val songs = coroutineScope { lists.map { async { SaavnCatalog.playlist(it.id)?.songs.orEmpty() } }.awaitAll().flatten() }
                    rank(songs, 50, noveltyBias = 0.0)
                }
                else -> emptyList()
            }
        }.orEmpty()
    }

    // ---------------------------------------------------------------- weekly cache

    @Serializable
    private data class CachedSongs(val key: String, val songs: List<SongInfo>)

    private fun loadCached(name: String, key: String): List<SongInfo>? = runCatching {
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        json.decodeFromString<CachedSongs>(file.readText()).takeIf { it.key == key }?.songs
    }.getOrNull()

    private fun saveCached(name: String, key: String, songs: List<SongInfo>) {
        runCatching { AtomicFiles.writeText(File(context.filesDir, name), json.encodeToString(CachedSongs(key, songs.map { it.copy(audioStreamUrl = null) }))) }
    }

    companion object {
        fun primaryArtist(artist: String): String =
            artist.split(",", "&", " feat.", " ft.", " x ").first().trim().lowercase()

        @Volatile private var instance: RecommendationEngine? = null

        fun getInstance(context: Context): RecommendationEngine = instance ?: synchronized(this) {
            instance ?: RecommendationEngine(context.applicationContext).also { instance = it }
        }
    }
}
