package com.example.musicapp.service

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.musicapp.audio.AudioEffectManager
import java.io.File

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var audioEffectManager: AudioEffectManager? = null
    
    companion object {
        private var downloadCache: SimpleCache? = null
        var activeAudioEffectManager: AudioEffectManager? = null
            private set
        var currentInstance: PlaybackService? = null
            private set

        var onNextTrackRequested: (() -> Unit)? = null
        var onPreviousTrackRequested: (() -> Unit)? = null

        fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?) {
            try {
                currentInstance?.exoPlayer?.setPreferredAudioDevice(device)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        currentInstance = this
        
        val cacheDataSourceFactory = runCatching {
            com.example.musicapp.data.cache.MediaCacheProvider.getCacheDataSourceFactory(this)
        }.onFailure {
            android.util.Log.e("PlaybackService", "Media cache unavailable; continuing without cache", it)
        }.getOrElse {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile)")
                .setAllowCrossProtocolRedirects(true)
            androidx.media3.datasource.DefaultDataSource.Factory(applicationContext, httpFactory)
        }
        val loadControl = com.example.musicapp.data.cache.MediaCacheProvider.createLoadControl()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableAudioFloatOutput(true)

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(cacheDataSourceFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .build()
        exoPlayer = player
            
        audioEffectManager = runCatching { AudioEffectManager(this) }
            .onFailure { android.util.Log.w("PlaybackService", "Audio effects could not initialize on this device", it) }
            .getOrNull()
        activeAudioEffectManager = audioEffectManager
        
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioEffectManager?.attachToSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    attachAudioEffectsToCurrentSession(player)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    attachAudioEffectsToCurrentSession(player)
                }
            }
        })
        
        val sessionActivityIntent = Intent(this, com.example.musicapp.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Ensure Notification Channel exists for Android O+
        val channelId = "playback_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(
                channelId,
                "Music Playback",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        setMediaNotificationProvider(
            androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(channelId)
                .build()
        )

        val callback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .build()
            }

            override fun onPlayerCommandRequest(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                playerCommand: Int
            ): Int {
                if (playerCommand == Player.COMMAND_SEEK_TO_NEXT || playerCommand == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) {
                    onNextTrackRequested?.invoke()
                    return androidx.media3.session.SessionResult.RESULT_SUCCESS
                } else if (playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS || playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
                    onPreviousTrackRequested?.invoke()
                    return androidx.media3.session.SessionResult.RESULT_SUCCESS
                }
                return super.onPlayerCommandRequest(session, controller, playerCommand)
            }
        }

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun seekToNext() {
                onNextTrackRequested?.invoke()
            }

            override fun seekToNextMediaItem() {
                onNextTrackRequested?.invoke()
            }

            override fun seekToPrevious() {
                onPreviousTrackRequested?.invoke()
            }

            override fun seekToPreviousMediaItem() {
                onPreviousTrackRequested?.invoke()
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun attachAudioEffectsToCurrentSession(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId > 0) {
            audioEffectManager?.attachToSession(sessionId)
        }
    }

    override fun onDestroy() {
        if (currentInstance == this) {
            currentInstance = null
        }
        mediaSession?.run {
            exoPlayer?.release()
            release()
            mediaSession = null
        }
        if (activeAudioEffectManager == audioEffectManager) {
            activeAudioEffectManager = null
        }
        audioEffectManager?.release()
        audioEffectManager = null
        super.onDestroy()
    }
}
