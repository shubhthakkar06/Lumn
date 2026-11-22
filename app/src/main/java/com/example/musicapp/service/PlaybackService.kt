package com.example.musicapp.service

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.musicapp.audio.SoundEngine
import com.example.musicapp.data.DefaultDataRepository
import com.example.musicapp.data.auth.UserManager
import com.example.musicapp.data.cache.MediaCacheProvider
import com.example.musicapp.data.history.PlayHistoryManager
import com.example.musicapp.data.network.SongInfo
import com.example.musicapp.data.settings.SettingsManager
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the player and the whole play queue.
 *
 * The queue lives inside the player (not in a screen's ViewModel), so it survives the app's
 * screens being destroyed, the next track preloads for gapless playback, and lock-screen,
 * Bluetooth, watch and Android Auto controls all see and control the same queue.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private lateinit var dataSourceFactory: TrackDataSource.Factory
    private lateinit var queueStore: QueueStore
    private lateinit var library: MediaLibraryTree

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Error recovery: one provider fallback per track, then skip; stop after a run of failures.
    private val fallbackTried = mutableSetOf<String>()
    private var consecutiveFailures = 0

    // Shuffle reorders the queue itself (current song first), so "up next" is always the
    // queue order. The original order is kept to restore when shuffle is turned off.
    private var shuffleEnabled = false
    private var unshuffledIds: List<String> = emptyList()

    private var autoplayJob: Job? = null
    private var tickerJob: Job? = null
    private var lastRecordedItemUid: String? = null
    private var fadeInStartedAtMs = 0L

    // Listening signals for recommendations: how long the current song has actually played.
    private var listening: SongInfo? = null
    private var listenedMs = 0L

    companion object {
        const val ACTION_SET_SHUFFLE = "rivara.SET_SHUFFLE"
        /** Switches the current song between audio and its music video ([EXTRA_VIDEO_ID]). */
        const val ACTION_SET_VIDEO = "rivara.SET_VIDEO"
        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_SHUFFLE = "rivara_shuffle"

        private var currentInstance: PlaybackService? = null

        fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
            runCatching { currentInstance?.player?.setPreferredAudioDevice(device) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        queueStore = QueueStore(this)
        library = MediaLibraryTree(this)

        val cache = runCatching { MediaCacheProvider.getSimpleCache(this) }.getOrNull()
        val cachedFactory = runCatching { MediaCacheProvider.getCacheDataSourceFactory(this) }.getOrElse {
            androidx.media3.datasource.DefaultDataSource.Factory(
                this,
                DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile)").setAllowCrossProtocolRedirects(true)
            )
        }
        dataSourceFactory = TrackDataSource.Factory(applicationContext, DefaultDataRepository(applicationContext), cache, cachedFactory)

        // Rivara's sound engine runs inside the player's audio pipeline, so it works on every
        // device and output (Bluetooth included), not only where Android ships system effects.
        val soundProcessor = SoundEngine.get(this).newProcessor()
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(soundProcessor))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        val exo = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(RivaraMediaSourceFactory(dataSourceFactory, this))
            .setLoadControl(MediaCacheProvider.createLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_NETWORK) // keep streaming with the screen off
            .build()
        player = exo

        // Re-apply the output the user pinned in the device picker.
        runCatching { exo.setPreferredAudioDevice(com.example.musicapp.ui.util.AudioDeviceManager.getInstance(this).preferredOutput()) }

        exo.addListener(playerListener)

        val sessionIntent = Intent(this, com.example.musicapp.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, sessionIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        createNotificationChannel()
        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).setChannelId("playback_channel").build()
        )

        session = MediaLibrarySession.Builder(this, exo, sessionCallback)
            .setSessionActivity(pendingIntent)
            .build()

        AutoplaySource.provider = { ctx, seeds, exclude, count ->
            com.example.musicapp.data.recommend.RecommendationEngine.getInstance(ctx).radioContinuation(seeds, exclude, count)
        }
        restoreQueue(exo)
        startTicker()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel("playback_channel", "Music Playback", android.app.NotificationManager.IMPORTANCE_LOW).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    // ---------------------------------------------------------------- session callback

    private val sessionCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SET_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_VIDEO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            publishExtras()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SET_SHUFFLE) {
                setShuffle(args.getBoolean(EXTRA_ENABLED))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == ACTION_SET_VIDEO) {
                setVideo(args.getBoolean(EXTRA_ENABLED), args.getString(EXTRA_VIDEO_ID))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Items arriving from controllers lose their URI/cache key over IPC; rebuild them.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { rebuild(it) }.toMutableList())

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = queueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.songs.map(TrackMediaItems::toMediaItem),
                    saved.index.coerceIn(0, saved.songs.lastIndex),
                    saved.positionMs
                )
            )
        }

        // Browse tree for Android Auto and other media browsers.
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(library.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = library.children(parentId)
            val from = (page * pageSize).coerceAtMost(children.size)
            val to = (from + pageSize).coerceAtMost(children.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(children.subList(from, to)), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = library.item(mediaId)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        // Tapping a song in Android Auto plays it in the context of its list.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.size == 1) {
                library.playContext(mediaItems[0].mediaId)?.let { (songs, index) ->
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(songs.map(TrackMediaItems::toMediaItem), index, 0L)
                    )
                }
            }
            return super.onSetMediaItems(mediaSession, controller, mediaItems.map { rebuild(it) }.toMutableList(), startIndex, startPositionMs)
        }
    }

    private fun rebuild(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item else TrackMediaItems.toMediaItem(TrackMediaItems.toSong(item))

    // ---------------------------------------------------------------- song / video switch

    /** While on, each new song switches to its music video when one exists. */
    private var videoMode = false
    private var videoLookup: Job? = null

    private fun setVideo(enabled: Boolean, videoId: String?) {
        videoMode = enabled
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val playingVideo = TrackMediaItems.videoIdOf(item)
        when {
            enabled && videoId != null && playingVideo != videoId -> swapCurrent(TrackMediaItems.toVideoItem(TrackMediaItems.toSong(item), videoId))
            !enabled && playingVideo != null -> swapCurrent(TrackMediaItems.toMediaItem(TrackMediaItems.toSong(item)))
        }
    }

    /** Replaces the current item in place, keeping position and play state. */
    private fun swapCurrent(replacement: MediaItem) {
        val p = player ?: return
        val index = p.currentMediaItemIndex
        val position = p.currentPosition
        val wasPlaying = p.playWhenReady
        p.replaceMediaItem(index, replacement)
        p.seekTo(index, position)
        p.playWhenReady = wasPlaying
    }

    /** In video mode, a new song switches to its video as soon as one is found. */
    private fun followVideoMode(item: MediaItem?) {
        videoLookup?.cancel()
        if (!videoMode || item == null || TrackMediaItems.videoIdOf(item) != null) return
        val song = TrackMediaItems.toSong(item)
        videoLookup = scope.launch {
            val id = withContext(Dispatchers.IO) { runCatching { com.example.musicapp.data.catalog.VideoMatch.find(song) }.getOrNull() } ?: return@launch
            if (videoMode && player?.currentMediaItem?.mediaId == item.mediaId) setVideo(true, id)
        }
    }

    // ---------------------------------------------------------------- player listener

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveFailures = 0
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            finishListen(reason)
            listening = mediaItem?.let(TrackMediaItems::toSong)
            listenedMs = 0L
            fadeInStartedAtMs = if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) System.currentTimeMillis() else 0L
            player?.volume = if (fadeInStartedAtMs > 0 && crossfadeMs() > 0) 0f else 1f
            recordPlay(mediaItem)
            maybeExtendWithAutoplay()
            followVideoMode(mediaItem)
            saveQueue()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                maybeExtendWithAutoplay()
                saveQueue()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) = saveQueue()

        override fun onPlayerError(error: PlaybackException) = recoverFromError(error)
    }

    private fun recoverFromError(error: PlaybackException) {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val id = item.mediaId
        android.util.Log.w("PlaybackService", "Playback error on $id: ${error.errorCodeName}", error)
        val unavailable = generateSequence<Throwable>(error) { it.cause }.any { it is TrackUnavailableException }

        // 1. Same track, different provider (and drop any partially cached bytes).
        if (!unavailable && fallbackTried.add(id)) {
            dataSourceFactory.fallbackRequested += id
            runCatching { MediaCacheProvider.getSimpleCache(this).removeResource(id) }
            p.prepare()
            p.play()
            return
        }

        // 2. Skip it.
        consecutiveFailures++
        val title = item.mediaMetadata.title ?: "this song"
        val offline = SettingsManager.getInstance(this).offlineMode.value
        if (consecutiveFailures >= 5 || !p.hasNextMediaItem()) {
            toast(if (offline) "Offline mode: only downloaded songs can play." else "Couldn't play \"$title\".")
            consecutiveFailures = 0
            return
        }
        toast(if (offline) "\"$title\" isn't downloaded. Skipping." else "Couldn't play \"$title\". Skipping.")
        p.seekToNextMediaItem()
        p.prepare()
        p.play()
    }

    // ---------------------------------------------------------------- shuffle

    private fun setShuffle(enabled: Boolean) {
        val p = player ?: return
        if (enabled == shuffleEnabled) return
        val count = p.mediaItemCount
        val current = p.currentMediaItemIndex
        if (count > 1 && current >= 0) {
            val items = (0 until count).map { p.getMediaItemAt(it) }
            if (enabled) {
                unshuffledIds = items.map { it.mediaId }
                val others = items.filterIndexed { i, _ -> i != current }.shuffled()
                if (current > 0) p.removeMediaItems(0, current)
                p.replaceMediaItems(1, p.mediaItemCount, others)
            } else {
                val position = unshuffledIds.withIndex().associate { (i, id) -> id to i }
                val currentItem = items[current]
                // Stable sort: songs added after shuffling keep their relative order at the end.
                val ordered = items.withIndex()
                    .sortedWith(compareBy({ position[it.value.mediaId] ?: Int.MAX_VALUE }, { it.index }))
                    .map { it.value }
                val target = ordered.indexOfFirst { it === currentItem }.coerceAtLeast(0)
                if (current > 0) p.removeMediaItems(0, current)
                p.replaceMediaItems(1, p.mediaItemCount, ordered.drop(target + 1))
                if (target > 0) p.addMediaItems(0, ordered.take(target))
            }
        }
        shuffleEnabled = enabled
        publishExtras()
        saveQueue()
    }

    private fun publishExtras() {
        session?.setSessionExtras(Bundle().apply { putBoolean(EXTRA_SHUFFLE, shuffleEnabled) })
    }

    // ---------------------------------------------------------------- autoplay

    private fun maybeExtendWithAutoplay() {
        val p = player ?: return
        val settings = SettingsManager.getInstance(this)
        if (!settings.autoplay.value || settings.offlineMode.value) return
        if (p.repeatMode != Player.REPEAT_MODE_OFF || p.mediaItemCount == 0) return
        if (p.currentMediaItemIndex < p.mediaItemCount - 2) return
        if (autoplayJob?.isActive == true) return

        val queue = (0 until p.mediaItemCount).map { TrackMediaItems.toSong(p.getMediaItemAt(it)) }
        autoplayJob = scope.launch {
            val more = withContext(Dispatchers.IO) {
                runCatching { AutoplaySource.nextSongs(applicationContext, queue.takeLast(5), queue.map { it.videoId }.toSet(), 10) }
                    .getOrDefault(emptyList())
            }
            val current = player ?: return@launch
            if (more.isNotEmpty() && current.repeatMode == Player.REPEAT_MODE_OFF) {
                current.addMediaItems(more.map(TrackMediaItems::toMediaItem))
            }
        }
    }

    // ---------------------------------------------------------------- fades, history, persistence

    private fun crossfadeMs(): Long = SettingsManager.getInstance(this).crossfadeSeconds.value * 1000L

    /** Fades out the end of each song and back in on the next when "Fade between songs" is set. */
    private fun startTicker() {
        tickerJob = scope.launch {
            var sinceSave = 0L
            while (isActive) {
                delay(100)
                val p = player ?: continue
                val fadeMs = crossfadeMs()
                if (p.isPlaying && fadeMs > 0) {
                    val remaining = p.duration - p.currentPosition
                    val fadeOut = if (p.duration > fadeMs * 2 && p.hasNextMediaItem() && remaining in 0..fadeMs) {
                        (remaining.toFloat() / fadeMs).coerceIn(0f, 1f)
                    } else 1f
                    val fadeIn = if (fadeInStartedAtMs > 0) {
                        ((System.currentTimeMillis() - fadeInStartedAtMs).toFloat() / (fadeMs / 2f)).coerceIn(0f, 1f)
                    } else 1f
                    p.volume = minOf(fadeOut, fadeIn)
                    if (fadeIn >= 1f) fadeInStartedAtMs = 0L
                } else if (p.volume != 1f && fadeMs == 0L) {
                    p.volume = 1f
                }
                if (p.isPlaying) listenedMs += 100
                sinceSave += 100
                if (p.isPlaying && sinceSave >= 15_000) {
                    sinceSave = 0
                    saveQueue()
                }
            }
        }
    }

    /** Logs how the previous song went: finished, skipped early, or partly heard. */
    private fun finishListen(transitionReason: Int?) {
        val song = listening ?: return
        val durationMs = song.durationSeconds * 1000
        val completed = transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
            (durationMs > 0 && listenedMs >= durationMs * 9 / 10)
        val skipped = !completed && transitionReason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK && listenedMs < 30_000
        if (listenedMs < 3_000 && !skipped) return
        com.example.musicapp.data.recommend.ListeningLog.getInstance(this).record(
            com.example.musicapp.data.recommend.ListenEvent(
                userId = UserManager.getInstance(this).currentUser.value.id,
                videoId = song.videoId,
                title = song.title,
                artist = song.artist,
                language = song.language,
                atMs = System.currentTimeMillis(),
                listenedMs = listenedMs,
                durationMs = durationMs,
                completed = completed,
                skipped = skipped
            )
        )
        com.example.musicapp.data.recommend.RecommendationEngine.getInstance(this).invalidateTaste()
    }

    private fun recordPlay(item: MediaItem?) {
        item ?: return
        val uid = "${item.mediaId}@${System.identityHashCode(item)}"
        if (uid == lastRecordedItemUid) return
        lastRecordedItemUid = uid
        val song = TrackMediaItems.toSong(item)
        val userId = UserManager.getInstance(this).currentUser.value.id
        PlayHistoryManager.getInstance(this).recordPlay(song, userId)
    }

    private fun saveQueue() {
        val p = player ?: return
        if (p.mediaItemCount == 0) {
            queueStore.clear()
            return
        }
        val songs = (0 until p.mediaItemCount).map { TrackMediaItems.toSong(p.getMediaItemAt(it)) }
        queueStore.save(
            SavedQueue(
                songs = songs,
                index = p.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = p.currentPosition.coerceAtLeast(0),
                repeatMode = p.repeatMode,
                shuffle = shuffleEnabled,
                unshuffledIds = unshuffledIds
            )
        )
    }

    private fun restoreQueue(p: ExoPlayer) {
        val saved = queueStore.load() ?: return
        shuffleEnabled = saved.shuffle
        unshuffledIds = saved.unshuffledIds
        p.repeatMode = saved.repeatMode
        p.setMediaItems(saved.songs.map(TrackMediaItems::toMediaItem), saved.index.coerceIn(0, saved.songs.lastIndex), saved.positionMs)
        // Not prepared: nothing loads until the user presses play.
    }

    private fun toast(message: String) {
        mainHandler.post { android.widget.Toast.makeText(applicationContext, message, android.widget.Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroy() {
        finishListen(null)
        saveQueue()
        scope.cancel()
        if (currentInstance === this) currentInstance = null
        session?.run {
            player.release()
            release()
        }
        session = null
        player = null
        super.onDestroy()
    }
}

/** Picks songs to keep playing after the queue ends. Replaced by the recommendation engine. */
object AutoplaySource {
    @Volatile
    var provider: (suspend (android.content.Context, List<SongInfo>, Set<String>, Int) -> List<SongInfo>)? = null

    suspend fun nextSongs(context: android.content.Context, seeds: List<SongInfo>, exclude: Set<String>, count: Int): List<SongInfo> {
        provider?.let { return it(context, seeds, exclude, count) }
        // Basic fallback: more from the last song's artist.
        val seed = seeds.lastOrNull() ?: return emptyList()
        val artist = seed.artist.split(",", "&").first().trim()
        if (artist.isBlank()) return emptyList()
        return com.example.musicapp.data.provider.ProviderRouter.shared.search(artist)
            .filter { it.videoId !in exclude && it.artist.contains(artist, ignoreCase = true) }
            .distinctBy { it.title.lowercase() }
            .take(count)
    }
}
