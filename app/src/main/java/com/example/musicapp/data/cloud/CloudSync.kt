package com.example.musicapp.data.cloud

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.musicapp.data.artist.ArtistFollowManager
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.auth.UserProfile
import com.example.musicapp.data.playlist.PlaylistManager
import com.example.musicapp.data.playlist.UserPlaylist
import com.example.musicapp.data.shows.ShowFollowManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Live library sync for Google accounts, through Firebase Realtime Database. Liked songs,
 * playlists (including their order), followed artists, followed shows and the profile photo
 * follow the account to every phone, Mac and watch, within a second or two, with no manual
 * sync. Playback itself is never tied to the account, so two devices can play at once.
 *
 * Works offline: changes are queued by Firebase and merged when the device reconnects.
 */
class CloudSync private constructor(private val context: Context) {
    sealed interface Status {
        data object SignedOut : Status
        data object Connecting : Status
        data class Live(val email: String) : Status
        data class Error(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.SignedOut)
    val status: StateFlow<Status> = _status.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.IO.limitedParallelism(1)
    // Sync problems are logged, never allowed to crash the app.
    private val scope = CoroutineScope(SupervisorJob() + worker + kotlinx.coroutines.CoroutineExceptionHandler { _, e -> Log.w(TAG, "Cloud sync error", e) })
    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    private var started = false
    private var uid: String? = null
    private var dataRef: DatabaseReference? = null
    private var listener: ValueEventListener? = null
    private var lastRemote: Map<String, Map<String, RemoteItem>>? = null
    private var pushJob: Job? = null
    /** Set while remote changes are being written into the local managers. */
    @Volatile
    private var applying = false

    fun start() {
        if (started) return
        started = true
        scope.launch(Dispatchers.Main) {
            UserManager.getInstance(context).currentUser
                .distinctUntilChangedBy { it.id to it.firebaseUid }
                .collect { user -> withContext(worker) { attach(user) } }
        }
    }

    /** Called by the local managers after every change; pushes after a short pause. */
    fun localChanged() {
        if (applying || uid == null) return
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(800)
            sync(lastRemote ?: return@launch)
        }
    }

    // ------------------------------------------------------------------ connection

    private fun database(): FirebaseDatabase {
        val res = context.resources.getIdentifier("firebase_database_url", "string", context.packageName)
        val url = res.takeIf { it != 0 }?.let(context::getString).orEmpty().ifBlank { DEFAULT_DATABASE_URL }
        return FirebaseDatabase.getInstance(url).also { db ->
            if (!persistenceSet) {
                runCatching { db.setPersistenceEnabled(true) }
                persistenceSet = true
            }
        }
    }

    private fun attach(user: UserProfile) {
        detach()
        val firebaseUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        val signedIn = !user.isGuest && firebaseUser != null &&
            (user.firebaseUid == firebaseUser.uid || (user.email.isNotBlank() && user.email.equals(firebaseUser.email, ignoreCase = true)))
        if (!signedIn) {
            _status.value = Status.SignedOut
            return
        }
        val accountUid = firebaseUser!!.uid
        uid = accountUid
        _status.value = Status.Connecting
        val ref = runCatching { database().getReference("users/$accountUid/data") }.getOrElse {
            _status.value = Status.Error("Cloud library isn't set up yet")
            uid = null
            return
        }
        ref.keepSynced(true)
        dataRef = ref
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remote = parse(snapshot)
                val profile = snapshot.child("profile")
                scope.launch {
                    if (uid != accountUid) return@launch
                    lastRemote = remote
                    sync(remote)
                    applyProfile(profile)
                    _status.value = Status.Live(firebaseUser.email.orEmpty())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Cloud sync stopped: ${error.message}")
                _status.value = Status.Error(
                    if (error.code == DatabaseError.PERMISSION_DENIED) "Cloud library permission denied (check the database rules)"
                    else error.message
                )
            }
        }
        listener = l
        ref.addValueEventListener(l)
    }

    private fun detach() {
        listener?.let { dataRef?.removeEventListener(it) }
        listener = null
        dataRef = null
        uid = null
        lastRemote = null
    }

    private fun parse(snapshot: DataSnapshot): Map<String, Map<String, RemoteItem>> =
        CloudPaths.SECTIONS.associateWith { section ->
            snapshot.child(section).children.mapNotNull { child ->
                val json = child.child("json").getValue(String::class.java) ?: return@mapNotNull null
                val at = child.child("at").getValue(Long::class.java) ?: 0L
                child.key!! to RemoteItem(json, at)
            }.toMap()
        }

    // ------------------------------------------------------------------ merge

    private suspend fun sync(remote: Map<String, Map<String, RemoteItem>>) {
        val accountUid = uid ?: return
        val base = loadBase(accountUid)
        val local = withContext(Dispatchers.Main) { localSnapshot() }
        val result = LibraryMerge.merge(base, local, remote, System.currentTimeMillis())
        if (result.localChanged) withContext(Dispatchers.Main) { applyLocal(result.local) }
        saveBase(accountUid, result.base)
        if (result.writes.isNotEmpty()) {
            val updates = result.writes.mapValues { (_, item) -> item?.let { mapOf("json" to it.json, "at" to it.at) } }
            runCatching { dataRef?.updateChildren(updates)?.await() }
                .onFailure { Log.w(TAG, "Cloud write failed; Firebase retries when online", it) }
        }
    }

    private fun localSnapshot(): LibrarySnapshot {
        val playlists = PlaylistManager.getInstance(context)
        return LibrarySnapshot.of(
            CloudPaths.PLAYLISTS to CloudJson.playlistSnapshot(playlists.playlists.value.map(::toCloud)),
            CloudPaths.LIKED to CloudJson.likedSnapshot(playlists.likedSongs.value),
            CloudPaths.ARTISTS to CloudJson.artistSnapshot(ArtistFollowManager.getInstance(context).followedArtists.value),
            CloudPaths.SHOWS to CloudJson.showSnapshot(ShowFollowManager.getInstance(context).shows.value)
        )
    }

    private fun applyLocal(merged: LibrarySnapshot) {
        applying = true
        try {
            val manager = PlaylistManager.getInstance(context)
            val userId = UserManager.getInstance(context).currentUser.value.id
            val cloudPlaylists = merged.section(CloudPaths.PLAYLISTS).values.mapNotNull(CloudJson::playlistOrNull)
            manager.updatePlaylistsFromSync { current ->
                val byId = current.associateBy { it.id }
                cloudPlaylists.map { cloud -> fromCloud(cloud, byId[cloud.id], userId) }
            }
            val liked = merged.section(CloudPaths.LIKED).values.mapNotNull(CloudJson::songOrNull)
            manager.updateLikedSongsFromSync { liked }
            ArtistFollowManager.getInstance(context).replaceFromSync(merged.section(CloudPaths.ARTISTS).values.toSet())
            ShowFollowManager.getInstance(context).replaceFromSync(merged.section(CloudPaths.SHOWS).values.mapNotNull(CloudJson::showOrNull))
        } finally {
            applying = false
        }
    }

    private fun toCloud(p: UserPlaylist) = CloudPlaylist(
        id = p.id, name = p.name, creator = p.creator, coverUrl = p.coverUrl, songs = p.songs,
        isPinned = p.isPinned, createdAtMs = p.createdAtMs, sourceUrl = p.sourceUrl
    )

    private fun fromCloud(c: CloudPlaylist, existing: UserPlaylist?, userId: String) = UserPlaylist(
        id = c.id, name = c.name, creator = c.creator, coverUrl = c.coverUrl, songs = c.songs,
        isPinned = c.isPinned, createdAtMs = c.createdAtMs, sourceUrl = c.sourceUrl,
        userId = existing?.userId ?: userId
    )

    private fun baseFile(accountUid: String) = File(context.filesDir, "cloud_base_${accountUid.replace(Regex("[^A-Za-z0-9_-]"), "_")}.json")

    private fun loadBase(accountUid: String): LibrarySnapshot = runCatching {
        CloudJson.json.decodeFromString(LibrarySnapshot.serializer(), baseFile(accountUid).readText())
    }.getOrDefault(LibrarySnapshot())

    private fun saveBase(accountUid: String, base: LibrarySnapshot) {
        runCatching {
            com.example.musicapp.data.storage.AtomicFiles.writeText(baseFile(accountUid), CloudJson.json.encodeToString(LibrarySnapshot.serializer(), base))
        }
    }

    // ------------------------------------------------------------------ profile photo

    fun uploadProfilePhoto(file: File?) {
        scope.launch {
            val ref = dataRef ?: return@launch
            val now = System.currentTimeMillis()
            val photo = file?.takeIf { it.exists() && it.length() < 400_000 }?.readBytes()
                ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }.orEmpty()
            prefs.edit().putLong("photoAt_$uid", now).apply()
            runCatching {
                ref.child("profile").updateChildren(mapOf("photo" to photo, "photoAt" to now)).await()
            }
        }
    }

    private suspend fun applyProfile(profile: DataSnapshot) {
        val accountUid = uid ?: return
        val remoteAt = profile.child("photoAt").getValue(Long::class.java) ?: return
        if (remoteAt <= prefs.getLong("photoAt_$accountUid", 0L)) return
        val photo = profile.child("photo").getValue(String::class.java).orEmpty()
        prefs.edit().putLong("photoAt_$accountUid", remoteAt).apply()
        val users = UserManager.getInstance(context)
        val userId = users.currentUser.value.id
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        dir.listFiles()?.filter { it.name.startsWith(userId + "_") }?.forEach { it.delete() }
        if (photo.isBlank()) {
            withContext(Dispatchers.Main) { users.updateCurrent { it.copy(photoUrl = "", hasCustomPhoto = false) } }
            return
        }
        val file = File(dir, "${userId}_$remoteAt.jpg")
        runCatching { file.writeBytes(Base64.decode(photo, Base64.DEFAULT)) }.onSuccess {
            withContext(Dispatchers.Main) { users.updateCurrent { it.copy(photoUrl = file.absolutePath, hasCustomPhoto = true) } }
        }
    }

    // ------------------------------------------------------------------ linking computers

    data class LinkedDevice(val id: String, val name: String, val linkedAtMs: Long)

    /**
     * Links a computer showing [code] to this account: the computer signed in anonymously and
     * published its device id under links/{code}; this grants that id access to the library.
     */
    suspend fun linkComputer(code: String): Result<String> = runCatching {
        val accountUid = uid ?: error("Sign in with Google on this phone first.")
        val clean = code.filter(Char::isDigit)
        require(clean.length == 6) { "Enter the 6-digit code shown on your computer." }
        val db = database()
        val link = db.getReference("links/$clean").get().await()
        val deviceUid = link.child("device").getValue(String::class.java) ?: error("That code wasn't found. Check it and try again.")
        val name = link.child("name").getValue(String::class.java).orEmpty().ifBlank { "Computer" }
        val createdAt = link.child("at").getValue(Long::class.java) ?: 0L
        require(System.currentTimeMillis() - createdAt < 15 * 60_000L) { "That code expired. Get a new one on your computer." }
        val user = UserManager.getInstance(context).currentUser.value
        db.getReference("users/$accountUid/grants/$deviceUid").setValue(mapOf("name" to name, "at" to System.currentTimeMillis())).await()
        db.getReference("links/$clean").updateChildren(
            mapOf("uid" to accountUid, "email" to user.email, "displayName" to user.name)
        ).await()
        name
    }

    suspend fun linkedDevices(): List<LinkedDevice> {
        val accountUid = uid ?: return emptyList()
        val snap = runCatching { database().getReference("users/$accountUid/grants").get().await() }.getOrNull() ?: return emptyList()
        return snap.children.map {
            LinkedDevice(it.key.orEmpty(), it.child("name").getValue(String::class.java).orEmpty(), it.child("at").getValue(Long::class.java) ?: 0L)
        }.sortedByDescending { it.linkedAtMs }
    }

    suspend fun unlink(deviceId: String) {
        val accountUid = uid ?: return
        runCatching { database().getReference("users/$accountUid/grants/$deviceId").removeValue().await() }
    }

    companion object {
        private const val TAG = "CloudSync"
        /** Used until google-services.json includes the database URL. */
        const val DEFAULT_DATABASE_URL = "https://lumn-24ba5-default-rtdb.firebaseio.com"
        private var persistenceSet = false

        @Volatile
        private var instance: CloudSync? = null

        fun get(context: Context): CloudSync = instance ?: synchronized(this) {
            instance ?: CloudSync(context.applicationContext).also { instance = it }
        }
    }
}
