package com.enes.liquidtube.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.enes.liquidtube.MainActivity
import com.enes.liquidtube.download.DownloadUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Media3 playback service. Background play, notification, lockscreen and
 * Bluetooth controls come from MediaSessionService.
 *
 * Data path: CacheDataSource (downloaded songs, keyed by videoId) → on a miss,
 * StreamResolver turns the placeholder URI into a fresh googlevideo URL.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : androidx.media3.session.MediaLibraryService() {

    @Inject lateinit var resolver: StreamResolver
    @Inject lateinit var downloads: DownloadUtil
    @Inject lateinit var settings: com.enes.liquidtube.data.SettingsStore
    @Inject lateinit var connection: PlayerConnection
    @Inject lateinit var playCache: com.enes.liquidtube.download.PlaybackCache
    @Inject lateinit var quality: com.enes.liquidtube.premium.QualityStore

    private val fx = AudioFx()
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    /** EQ preset + Sound Check gain for whatever is playing now. */
    private fun applyFx(player: Player) {
        val id = player.currentMediaItem?.mediaId
        val gain = if (settings.soundCheck.value && id != null) AudioFx.soundCheckGain(quality.loudness(id)) else 0f
        fx.set(settings.eqPreset.value, gain)
    }

    private var mediaSession: MediaLibrarySession? = null
    @Inject lateinit var repository: com.enes.liquidtube.data.MusicRepository
    @Inject lateinit var libraryStore: com.enes.liquidtube.data.LibraryStore
    private val car by lazy { CarLibrary(repository, libraryStore, downloads, playCache, serviceScope) }
    private lateinit var sourceFactory: DefaultMediaSourceFactory
    private var retryId: String? = null
    private var retries = 0

    override fun onCreate() {
        super.onCreate()

        val keyFactory = androidx.media3.datasource.cache.CacheKeyFactory { spec ->
            spec.uri.getQueryParameter("v") ?: spec.key ?: spec.uri.toString()
        }
        // Listening cache: everything you stream is saved as it plays (LRU, size in Settings).
        val listeningFactory = CacheDataSource.Factory()
            .setCache(playCache.cache)
            .setUpstreamDataSourceFactory(resolver.dataSourceFactory)
            .setCacheKeyFactory(keyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // Downloads first (read-only), then the listening cache, then the network.
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(downloads.cache)
            .setUpstreamDataSourceFactory(listeningFactory)
            .setCacheWriteDataSinkFactory(null) // read-only: only downloads write
            .setCacheKeyFactory(keyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        sourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(RecoveryListener(player))
        fx.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) { fx.attach(audioSessionId); applyFx(player) }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = applyFx(player)
        })
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(settings.eqPreset, settings.soundCheck) { _, _ -> }.collect { applyFx(player) }
        }
        startCrossfade(player)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The session sees the player through the lock-screen lyrics layer.
        val sessionPlayer = LyricsMetadataPlayer(player)
        LockScreenLyrics.player = sessionPlayer
        mediaSession = MediaLibrarySession.Builder(this, sessionPlayer, SessionCallback())
            .setSessionActivity(openApp)
            .build()
        // Lock screen / notification: a ★ button next to the transport controls.
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshFavoriteButton()
                playCache.refresh() // the song that just finished is now complete in the cache
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED || state == Player.STATE_READY) playCache.refresh()
            }
        })
        connection.favoriteChanged = { handler.post { refreshFavoriteButton() } }
        refreshFavoriteButton()
    }

    /** Re-resolves once on failure (e.g. expired URL → 403), then skips the track. */
    private inner class RecoveryListener(private val player: ExoPlayer) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            retryId = null
            retries = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId
            val http = generateSequence<Throwable>(error) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()
            Log.e(TAG, "Playback error on $id: ${error.errorCodeName} http=${http?.responseCode}", error)
            if (id != null) resolver.invalidate(id)

            if (retryId != id) {
                retryId = id
                retries = 0
            }
            if (retries < 1) {
                retries++
                player.prepare()
                player.play()
            } else if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    // ------------------------------------------------------- lock-screen favorite

    private val favoriteCommand = androidx.media3.session.SessionCommand(ACTION_FAVORITE, android.os.Bundle.EMPTY)

    private fun refreshFavoriteButton() {
        val session = mediaSession ?: return
        val id = session.player.currentMediaItem?.mediaId
        val fav = id != null && connection.isFavorite(id)
        val button = androidx.media3.session.CommandButton.Builder()
            .setDisplayName(if (fav) "Unfavorite" else "Favorite")
            .setIconResId(if (fav) com.enes.liquidtube.R.drawable.ic_w_star else com.enes.liquidtube.R.drawable.ic_w_star_outline)
            .setSessionCommand(favoriteCommand)
            .build()
        session.setCustomLayout(com.google.common.collect.ImmutableList.of(button))
    }

    /** Session callbacks: the lock-screen ★, plus the browsable library for Android Auto. */
    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?) =
            car.root(params)

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int, params: LibraryParams?,
        ) = car.children(parentId, params)

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String) = car.item(mediaId)

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?) =
            car.search(session, browser, query, params)

        override fun onGetSearchResult(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, page: Int, pageSize: Int, params: LibraryParams?,
        ) = car.searchResult(query, page, pageSize, params)

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> =
            com.google.common.util.concurrent.Futures.immediateFuture(mediaItems.map(car::complete).toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
        ) = car.resolve(mediaItems, startIndex, startPositionMs)

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon().add(favoriteCommand).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: android.os.Bundle,
        ): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult> {
            if (customCommand.customAction == ACTION_FAVORITE) {
                session.player.currentMediaItem?.let { item ->
                    with(PlayerConnection) { connection.toggleFavorite(item.toSong()) }
                }
                refreshFavoriteButton()
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS),
            )
        }
    }

    // ------------------------------------------------------------- crossfade

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Real crossfade (AutoMix-style): near the end of a song a second "tail" player picks
     * up its last seconds and fades them out, while the session's player has already
     * moved on to the next song and fades it in, so the two overlap. Off (0) leaves
     * ExoPlayer's gapless playback untouched.
     */
    private var tail: ExoPlayer? = null
    private var tailStartMs = -1L          // position in the ending song where the overlap starts
    private var handoffAt = 0L             // uptime when the overlap began; 0 = none
    private var ourTransition = false
    private var softStartAt = 0L           // manual skips fade in briefly

    private fun startCrossfade(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (ourTransition) { ourTransition = false; return }
                // Any other change (skip, new queue) cancels an overlap in progress.
                releaseTail()
                if (settings.crossfadeSeconds.value > 0 && reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    softStartAt = android.os.SystemClock.uptimeMillis()
                    player.volume = 0f
                } else {
                    player.volume = 1f
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val t = tail ?: return
                if (handoffAt > 0) { if (isPlaying) t.play() else t.pause() }
            }
        })

        val tick = object : Runnable {
            override fun run() {
                runCatching { crossfadeTick(player) }.onFailure { Log.w(TAG, "crossfade", it); releaseTail() }
                handler.postDelayed(this, 40)
            }
        }
        handler.post(tick)
    }

    private fun crossfadeTick(player: ExoPlayer) {
        val fade = settings.crossfadeSeconds.value * 1000L
        val now = android.os.SystemClock.uptimeMillis()
        val curve = { x: Float -> kotlin.math.sin(x.coerceIn(0f, 1f) * (Math.PI / 2)).toFloat() }

        // 1. An overlap in progress: tail fades out, the new song fades in.
        if (handoffAt > 0) {
            val t = (now - handoffAt).toFloat() / fade.coerceAtLeast(1)
            tail?.volume = curve(1f - t)
            player.volume = curve(t)
            if (t >= 1f) { releaseTail(); player.volume = 1f }
            return
        }
        // 2. Soft start after a manual skip.
        if (softStartAt > 0) {
            val t = (now - softStartAt) / 450f
            player.volume = curve(t)
            if (t >= 1f) softStartAt = 0
        }
        if (fade <= 0 || !player.isPlaying) return

        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < fade * 3) return
        if (!player.hasNextMediaItem() || player.repeatMode == Player.REPEAT_MODE_ONE) return
        val pos = player.currentPosition
        val start = duration - fade

        // 3. Shortly before the overlap, get the tail ready at the overlap point.
        if (tail == null && pos >= start - 2500 && pos < start) {
            val item = player.currentMediaItem ?: return
            tailStartMs = start
            tail = ExoPlayer.Builder(this)
                .setMediaSourceFactory(sourceFactory)
                .setAudioAttributes(
                    AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
                    false,
                )
                .build().apply {
                    setMediaItem(item, start)
                    volume = 1f
                    playWhenReady = false
                    prepare()
                }
            Log.i(TAG, "Crossfade: preparing tail at ${start}ms of ${item.mediaId}")
        }

        // 4. Overlap point reached: tail takes over the ending, player moves on.
        val t = tail
        if (t != null && pos >= tailStartMs) {
            if (t.playbackState == Player.STATE_READY) {
                t.seekTo(pos)
                t.play()
                ourTransition = true
                player.volume = 0f
                player.seekToNextMediaItem()
                handoffAt = now
                Log.i(TAG, "Crossfade: overlap started (${fade}ms)")
            } else if (pos > tailStartMs + 1500) {
                // Tail couldn't load in time: fall back to plain fade-out of this song.
                releaseTail()
            }
        } else if (t == null && pos >= start) {
            player.volume = curve((duration - pos).toFloat() / fade)
        }
    }

    private fun releaseTail() {
        tail?.release()
        tail = null
        tailStartMs = -1L
        handoffAt = 0L
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releaseTail()
        fx.release()
        LockScreenLyrics.player = null
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val TAG = "LiquidTube"
        const val ACTION_FAVORITE = "com.enes.liquidtube.FAVORITE"
        fun placeholderUri(videoId: String) = StreamResolver.placeholderUri(videoId)
    }
}
