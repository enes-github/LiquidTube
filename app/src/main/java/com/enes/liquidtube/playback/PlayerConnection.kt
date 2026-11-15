package com.enes.liquidtube.playback

import com.enes.liquidtube.ui.theme.tr
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.enes.liquidtube.data.AuthStore
import com.enes.liquidtube.data.LibraryStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One entry of the up-next list: its index in the player's queue plus the song. */
data class QueueEntry(val index: Int, val song: SongItem)

/**
 * Owns the MediaController bound to [PlaybackService] and exposes player state as
 * flows. The whole UI talks to playback only through this class.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    val library: LibraryStore,
    val auth: AuthStore,
    val downloads: com.enes.liquidtube.download.DownloadUtil,
    val settings: com.enes.liquidtube.data.SettingsStore,
    val network: com.enes.liquidtube.data.NetworkMonitor,
    val smart: com.enes.liquidtube.download.SmartDownloads,
    val playCache: com.enes.liquidtube.download.PlaybackCache,
    val quality: com.enes.liquidtube.premium.QualityStore,
    val upgrader: com.enes.liquidtube.premium.QualityUpgrader,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var radioJob: Job? = null
    private var positionJob: Job? = null

    // ------------------------------------------------- connection-aware queue

    /**
     * What was coming up when the connection dropped, so it can be put back. Non-null
     * while the queue is in offline mode.
     */
    private var onlineUpNext: List<SongItem>? = null
    /** Songs we added to keep the music going offline (taken out again when back online). */
    private val offlineFill = LinkedHashSet<String>()

    init {
        MusicRepository.onNetworkError = { network.reportFailure() }
        // Plain Main (not immediate): starts after this object is fully built.
        scope.launch(Dispatchers.Main) {
            network.online.collect { online ->
                if (online) flushHistory()
                val c = controller?.takeIf { it.isConnected } ?: return@collect
                adaptToConnection(c, online)
            }
        }
    }

    /** Re-plan the queue for the connection we have, without touching the song playing now. */
    private fun adaptToConnection(c: MediaController, online: Boolean) {
        if (c.mediaItemCount == 0) return
        if (!online) goOffline(c) else goOnline(c)
    }

    private fun goOffline(c: MediaController) {
        if (onlineUpNext != null) return
        radioJob?.cancel()
        val cur = c.currentMediaItemIndex.coerceAtLeast(0)
        val upcoming = (cur + 1 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        onlineUpNext = upcoming

        // Take out what can't play without a connection (from the end, so indices hold).
        var removed = 0
        for (i in c.mediaItemCount - 1 downTo cur + 1) {
            if (!isOffline(c.getMediaItemAt(i).toSong())) {
                c.removeMediaItem(i)
                removed++
            }
        }
        val added = fillOffline(c, target = 25)
        Log.i(TAG, "Offline: queue switched ($removed removed, $added offline songs added)")
        if (removed > 0 || added > 0) toast(tr("You’re offline — playing your downloads next", "Çevrimdışısınız — sırada indirdikleriniz çalacak"))
    }

    private fun goOnline(c: MediaController) {
        val saved = onlineUpNext ?: return
        onlineUpNext = null
        val cur = c.currentMediaItemIndex.coerceAtLeast(0)
        val currentId = c.currentMediaItem?.mediaId
        // Played already (before, or now playing): don't bring those back.
        val played = (0..cur).map { c.getMediaItemAt(it).mediaId }.toSet()
        val restore = saved.filter { it.id !in played }
        val restoreIds = restore.map { it.id }.toSet()

        // Remove the offline fill-ins and the kept songs (they come back in their old order).
        for (i in c.mediaItemCount - 1 downTo cur + 1) {
            val id = c.getMediaItemAt(i).mediaId
            if (id in offlineFill || id in restoreIds) c.removeMediaItem(i)
        }
        offlineFill.clear()
        if (restore.isNotEmpty()) {
            c.addMediaItems(c.currentMediaItemIndex + 1, restore.map { it.toMediaItem() })
        }
        Log.i(TAG, "Online: queue restored (${restore.size} songs), now playing $currentId")
        if (restore.isNotEmpty()) toast(tr("Back online — your queue is back", "Tekrar çevrimiçi — sıranız geri geldi"))
        maybeExtendQueue()
    }

    /**
     * Tops the queue up to [target] upcoming songs from what's playable offline: the
     * current artist first, then the rest shuffled. Returns how many were added.
     */
    private fun fillOffline(c: MediaController, target: Int): Int {
        val cur = c.currentMediaItemIndex.coerceAtLeast(0)
        val have = c.mediaItemCount - cur - 1
        if (have >= target) return 0
        val present = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
        val artist = c.currentMediaItem?.toSong()?.artists?.split(" & ", ", ")?.firstOrNull()?.trim().orEmpty()
        val pool = offlineSongs().filterNot { it.id in present }
        val (same, rest) = pool.partition { artist.isNotEmpty() && it.artists.contains(artist, ignoreCase = true) }
        val picks = (same.shuffled() + rest.shuffled()).take(target - have)
        if (picks.isEmpty()) return 0
        offlineFill += picks.map { it.id }
        c.addMediaItems(picks.map { it.toMediaItem() })
        return picks.size
    }

    /** Everything that plays without a connection: downloads and the listening cache. */
    private fun offlineSongs(): List<SongItem> =
        (downloads.downloads.value.values.filter { it.isDone }.map { it.song } + playCache.cached.value)
            .distinctBy { it.id }

    /** Offline and the song can't play: move on to the next one that can, quietly. */
    private fun skipUnplayable(c: MediaController): Boolean {
        val cur = c.currentMediaItemIndex
        for (i in cur + 1 until c.mediaItemCount) {
            if (isOffline(c.getMediaItemAt(i).toSong())) {
                c.seekTo(i, 0L)
                c.prepare()
                c.play()
                return true
            }
        }
        if (fillOffline(c, target = 25) > 0) {
            c.seekTo(cur + 1, 0L)
            c.prepare()
            c.play()
            return true
        }
        return false
    }

    private val _current = MutableStateFlow<SongItem?>(null)
    val current: StateFlow<SongItem?> = _current.asStateFlow()

    /** True while playback is intended (playing or buffering towards playing). */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _upNext = MutableStateFlow<List<QueueEntry>>(emptyList())
    val upNext: StateFlow<List<QueueEntry>> = _upNext.asStateFlow()

    private val _history = MutableStateFlow<List<QueueEntry>>(emptyList())
    /** What played before the current song in this queue, oldest first. */
    val history: StateFlow<List<QueueEntry>> = _history.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            controller?.let(::saveSession)
            if (!isPlaying) sessionPrefs.edit().putLong(KEY_PAUSED_AT, System.currentTimeMillis()).apply()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let(::saveSession)
            mediaItem?.toSong()?.let {
                library.addRecent(it)
                playCache.remember(it)
            }
            maybeExtendQueue()
        }

        override fun onPlayerError(error: PlaybackException) {
            val c = controller
            val networkError = error.errorCode in NETWORK_ERRORS
            if (networkError) network.reportFailure()
            // Offline (or the connection just died): don't stop, go to the next song that
            // plays without a connection. The connection check then switches the queue.
            if (c != null && (networkError || !network.online.value)) {
                if (!network.online.value && skipUnplayable(c)) return
                if (networkError) {
                    scope.launch {
                        delay(2_500) // give the connection check a moment
                        val now = controller ?: return@launch
                        if (!network.online.value) {
                            adaptToConnection(now, false)
                            if (!skipUnplayable(now)) toast(tr("You’re offline — no downloaded songs to play", "Çevrimdışısınız — çalacak indirilmiş şarkı yok"))
                        } else {
                            // Still online: a hiccup. Try the same song again.
                            now.prepare()
                            now.play()
                        }
                    }
                    return
                }
            }
            val title = c?.currentMediaItem?.mediaMetadata?.title ?: "this track"
            Toast.makeText(context, tr("Couldn't play “$title”", "“$title” çalınamadı"), Toast.LENGTH_SHORT).show()
        }
    }

    private val _playbackHealthy = MutableStateFlow(true)
    /** False when a startup self-test couldn't get a stream although we're online. */
    val playbackHealthy: StateFlow<Boolean> = _playbackHealthy.asStateFlow()
    private var healthChecked = false

    /** Runs once per app start, quietly, a few seconds after launch. */
    private fun checkPlaybackHealth() {
        if (healthChecked) return
        healthChecked = true
        scope.launch {
            delay(4000)
            if (!network.online.value) return@launch
            val probe = library.recents.value.firstOrNull()?.id ?: "fJ9rUzIMcZQ"
            val ok = repository.streamHealth(probe) || repository.streamHealth("fJ9rUzIMcZQ")
            _playbackHealthy.value = ok
            if (!ok) Log.w(TAG, "Playback self-test failed: no stream for $probe")
        }
    }

    fun recheckPlayback() {
        healthChecked = false
        _playbackHealthy.value = true
        checkPlaybackHealth()
    }

    // ------------------------------------- resume when headphones or the car connect

    private var deviceWatch = false

    /** Headphones, a Bluetooth speaker or the car appearing as an output: resume. */
    private fun watchAudioDevices() {
        if (deviceWatch) return
        deviceWatch = true
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return
        val since = System.currentTimeMillis()
        audio.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) {
                // Gone again before the resume fired: don't resume.
                if (removed.any { it.type in LISTENING_DEVICES } && !listeningDeviceConnected()) resumeJob?.cancel()
            }

            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
                // The callback first lists what's already connected: that's not a new arrival.
                if (System.currentTimeMillis() - since < 3_000) return
                if (added.any { it.isSink && it.type in LISTENING_DEVICES }) resumeOnDevice()
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    /**
     * Resume what was playing if it was paused recently (within 4 hours), e.g. you got
     * in the car or put your headphones on. Called by the device watcher and by the
     * Bluetooth receiver when the app wasn't running.
     */
    fun resumeOnDevice() {
        if (!settings.autoResume.value) return
        val pausedAt = sessionPrefs.getLong(KEY_PAUSED_AT, 0L)
        if (System.currentTimeMillis() - pausedAt > 4 * 3_600_000L) return
        // A loose AUX cable or a flaky adapter connects and disconnects many times a
        // second; resuming on each connect fought the "unplugged" pause in a loop. So:
        // wait until the device has stayed connected for a few seconds, and resume at
        // most once per minute.
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(3_000)
            if (System.currentTimeMillis() - lastAutoResume < 60_000L) return@launch
            if (!listeningDeviceConnected()) return@launch
            withController { c ->
                if (c.isPlaying || c.mediaItemCount == 0) return@withController
                lastAutoResume = System.currentTimeMillis()
                Log.i(TAG, "Resuming: an audio device connected")
                c.prepare()
                c.play()
            }
        }
    }

    private var resumeJob: Job? = null
    private var lastAutoResume = 0L

    /** Headphones, AUX, Bluetooth audio or a car is connected right now. */
    private fun listeningDeviceConnected(): Boolean {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return false
        return audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in LISTENING_DEVICES }
    }

    // ------------------------------------------------- resume where you left off

    private val sessionPrefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val sessionJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val queueSerializer = kotlinx.serialization.builtins.ListSerializer(SongItem.serializer())
    private var savedQueueSignature = ""
    private var saveTick = 0

    /** Persist the queue, current song and position (cheap: the queue only when it changed). */
    private fun saveSession(c: Player) {
        val count = c.mediaItemCount
        if (count == 0) return
        val index = c.currentMediaItemIndex
        // Keep a window around the current song so huge radios stay small.
        val from = (index - 50).coerceAtLeast(0)
        val to = (index + 150).coerceAtMost(count)
        val signature = "$from-$to-$count-${c.getMediaItemAt(from).mediaId}-${c.getMediaItemAt(to - 1).mediaId}"
        val edit = sessionPrefs.edit()
        if (signature != savedQueueSignature) {
            val songs = (from until to).map { c.getMediaItemAt(it).toSong() }
            edit.putString(KEY_QUEUE, sessionJson.encodeToString(queueSerializer, songs))
            savedQueueSignature = signature
        }
        edit.putInt(KEY_INDEX, index - from)
            .putLong(KEY_POSITION, c.currentPosition.coerceAtLeast(0))
            .putBoolean(KEY_SHUFFLE, c.shuffleModeEnabled)
            .putInt(KEY_REPEAT, c.repeatMode)
            .apply()
    }

    /** After the app (and its player) restarted: put the last session back, paused. */
    private fun restoreSession(c: MediaController) {
        if (c.mediaItemCount > 0 || pending.isNotEmpty()) return
        val songs = sessionPrefs.getString(KEY_QUEUE, null)
            ?.let { runCatching { sessionJson.decodeFromString(queueSerializer, it) }.getOrNull() }
            .orEmpty()
        if (songs.isEmpty()) return
        val index = sessionPrefs.getInt(KEY_INDEX, 0).coerceIn(songs.indices)
        val position = sessionPrefs.getLong(KEY_POSITION, 0L)
        c.setMediaItems(songs.map { it.toMediaItem() }, index, position)
        c.shuffleModeEnabled = false // smart shuffle lives in the queue order itself
        c.repeatMode = sessionPrefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
        c.playWhenReady = false
        c.prepare()
        _current.value = songs[index]
        _positionMs.value = position
        Log.i(TAG, "Restored session: ${songs[index].title} at ${position / 1000}s (${songs.size} in queue)")
    }

    fun initialize() {
        watchAudioDevices()
        checkPlaybackHealth()
        smart.start()
        upgrader.start { _current.value?.id }
        if (controller?.isConnected == true || connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            com.enes.liquidtube.ui.launch.Startup.playerReady()
            val c = runCatching { future.get() }.getOrElse {
                Log.e(TAG, "MediaController connection failed", it)
                return@addListener
            }
            controller = c
            c.addListener(listener)
            restoreSession(c)
            if (!network.online.value) adaptToConnection(c, false)
            sync(c)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            startPositionLoop()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null && c.isConnected) {
            block(c)
        } else {
            pending += block
            initialize()
        }
    }

    // ---------------------------------------------------------------- actions

    /**
     * Replace the queue with [songs] and start at [startIndex]. With [radio], a
     * YouTube Music radio for the starting song is appended once it loads, so
     * playback continues like autoplay in the official app.
     */
    fun play(
        songs: List<SongItem>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        radio: Boolean = false,
    ) {
        if (songs.isEmpty()) return
        // Offline: only downloaded songs can play; keep the tapped one as the start.
        if (!network.online.value) {
            val tapped = songs.getOrNull(startIndex)
            val playable = songs.filter(::isOffline)
            if (playable.isEmpty() || (tapped != null && tapped !in playable)) {
                toast(tr("You’re offline — only downloaded songs can play", "Çevrimdışısınız — yalnızca indirilen şarkılar çalabilir"))
                if (playable.isEmpty()) return
            }
            if (playable.size != songs.size) {
                play(playable, playable.indexOf(tapped).coerceAtLeast(0), shuffle, radio = false)
                return
            }
        }
        val start = startIndex.coerceIn(songs.indices)
        val seed = songs[start]
        // A new queue: whatever was saved from before the connection dropped no longer applies.
        onlineUpNext = if (network.online.value) null else emptyList()
        smartOrder = null
        offlineFill.clear()
        _current.value = seed
        _positionMs.value = 0
        _durationMs.value = 0
        radioJob?.cancel()

        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
            c.prepare()
            c.play()
        }

        if (radio && network.online.value) {
            radioJob = scope.launch {
                val more = shapeStation(repository.radio(seed.id).filterNot { it.id == seed.id }, seed)
                withController { c ->
                    if (c.currentMediaItem?.mediaId == seed.id && c.mediaItemCount == 1) {
                        c.addMediaItems(more.map { it.toMediaItem() })
                    }
                }
            }
        }
    }

    /** Single tap on a song: play it and keep going with its radio. */
    fun playSong(song: SongItem) = play(listOf(song), radio = true)

    fun shufflePlay(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val order = smartShuffle(songs, lastArtists = emptyList())
        play(order, startIndex = 0, shuffle = false)
        smartOrder = songs.map { it.id }
        controller?.let(::sync)
    }

    /** Smart shuffle on: the order the queue had before shuffling (to restore it). */
    private var smartOrder: List<String>? = null

    /**
     * Smart shuffle: random, but never the same artist twice in a row (or within three
     * songs, when the list allows), and leaning a little toward favorites and songs you
     * play often, so a shuffled playlist sounds like a good mix rather than dice rolls.
     */
    private fun smartShuffle(songs: List<SongItem>, lastArtists: List<String>): List<SongItem> {
        val favorites = library.favorites.value.map { it.id }.toSet()
        val counts = library.playCounts.value
        val pool = songs.distinctBy { it.id }.toMutableList()
        if (pool.size < 2) return pool
        // How many songs each artist still has left: an artist with many songs must be
        // spread over the whole list, or they'd all pile up at the end.
        val left = HashMap<String, Int>()
        pool.forEach { left.merge(primaryArtist(it), 1, Int::plus) }
        val biggest = left.values.max().toFloat() / pool.size
        // With one artist over a third of the list, "not twice in a row" is the best possible.
        val window = if (biggest > 0.34f) 1 else 2
        val out = ArrayList<SongItem>(pool.size)
        val recent = ArrayDeque(lastArtists.takeLast(window))
        val random = kotlin.random.Random(System.nanoTime())
        while (pool.isNotEmpty()) {
            val candidates = pool.filter { primaryArtist(it) !in recent }.ifEmpty { pool }
            val weights = candidates.map { s ->
                val taste = 1.0 + (if (s.id in favorites) 1.5 else 0.0) + kotlin.math.ln(1.0 + (counts[s.id] ?: 0)) * 0.5
                taste * Math.pow((left[primaryArtist(s)] ?: 1).toDouble(), 1.5)
            }
            var r = random.nextDouble() * weights.sum()
            var pick = candidates.last()
            for (i in candidates.indices) {
                r -= weights[i]
                if (r <= 0) { pick = candidates[i]; break }
            }
            out += pick
            pool.remove(pick)
            val a = primaryArtist(pick)
            left[a] = (left[a] ?: 1) - 1
            recent.addLast(a)
            while (recent.size > window) recent.removeFirst()
        }
        return out
    }

    /** "Mariah Carey & Boyz II Men" → "mariah carey". */
    private fun primaryArtist(s: SongItem): String =
        s.artists.split(" & ", ", ", " feat", " ft.", " x ", " X ", " and ").first().trim().lowercase()

    /**
     * Keeps a list's order but never lets the same artist play twice in a row when
     * there's another song to put between (stations and autoplay).
     */
    private fun spreadArtists(songs: List<SongItem>, after: SongItem?): List<SongItem> {
        val pending = songs.toMutableList()
        val out = ArrayList<SongItem>(songs.size)
        var last = after?.let(::primaryArtist)
        while (pending.isNotEmpty()) {
            val i = pending.indexOfFirst { primaryArtist(it) != last }.let { if (it < 0) 0 else it }
            val next = pending.removeAt(i)
            out += next
            last = primaryArtist(next)
        }
        return out
    }

    fun playNext(song: SongItem) = withController { c ->
        if (c.mediaItemCount == 0) playSong(song)
        else c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
        toast(tr("Playing next", "Sıradaki olarak çalacak"))
    }

    fun addToQueue(song: SongItem) = withController { c ->
        if (c.mediaItemCount == 0) playSong(song) else c.addMediaItem(song.toMediaItem())
        toast(tr("Added to queue", "Sıraya eklendi"))
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Search a seed query and start a radio from the top result (Radio stations). */
    fun playFromQuery(query: String) {
        scope.launch {
            val first = repository.searchSongs(query).getOrNull()?.firstOrNull()
            if (first != null) playSong(first)
            else Toast.makeText(context, tr("Couldn't start station", "İstasyon başlatılamadı"), Toast.LENGTH_SHORT).show()
        }
    }

    fun playAt(index: Int) = withController { c ->
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        when {
            c.playbackState == Player.STATE_ENDED -> {
                c.seekToDefaultPosition()
                c.play()
            }
            c.playbackState == Player.STATE_IDLE -> {
                c.prepare()
                c.play()
            }
            c.playWhenReady -> c.pause()
            else -> c.play()
        }
    }

    fun skipNext() = withController { c -> if (c.hasNextMediaItem()) c.seekToNextMediaItem() }

    fun skipPrevious() = withController { c -> c.seekToPrevious() }

    fun seekTo(ms: Long) {
        _positionMs.value = ms
        withController { it.seekTo(ms) }
    }

    /** Shuffle the rest of the queue (smart shuffle), or put it back in its original order. */
    fun toggleShuffle() = withController { c ->
        if (c.shuffleModeEnabled) {
            c.shuffleModeEnabled = false
            return@withController
        }
        val cur = c.currentMediaItemIndex.coerceAtLeast(0)
        val upcoming = (cur + 1 until c.mediaItemCount).map { c.getMediaItemAt(it).toSong() }
        val original = smartOrder
        if (original != null) {
            // Off: back to the order the songs had (new additions stay at the end).
            val rank = original.withIndex().associate { it.value to it.index }
            val restored = upcoming.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
            smartOrder = null
            if (upcoming.isNotEmpty()) {
                c.removeMediaItems(cur + 1, c.mediaItemCount)
                c.addMediaItems(restored.map { it.toMediaItem() })
            }
        } else {
            smartOrder = upcoming.map { it.id }
            if (upcoming.size > 1) {
                val playing = c.currentMediaItem?.toSong()
                val mixed = smartShuffle(upcoming, listOfNotNull(playing?.let(::primaryArtist)))
                c.removeMediaItems(cur + 1, c.mediaItemCount)
                c.addMediaItems(mixed.map { it.toMediaItem() })
            }
        }
        sync(c)
    }

    fun toggleAutoplay() {
        settings.setAutoplay(!settings.autoplay.value)
        if (settings.autoplay.value) maybeExtendQueue()
    }

    /** Move a queue item (player indices). */
    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    /** Put a song back where it was (Undo after removing it from the queue). */
    fun insertQueueItem(index: Int, song: SongItem) = withController { c ->
        c.addMediaItem(index.coerceIn(c.currentMediaItemIndex + 1, c.mediaItemCount), song.toMediaItem())
    }

    fun removeQueueItem(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount && index != c.currentMediaItemIndex) c.removeMediaItem(index)
    }

    fun clearUpNext() = withController { c ->
        val count = c.mediaItemCount
        val cur = c.currentMediaItemIndex
        if (cur + 1 < count) c.removeMediaItems(cur + 1, count)
    }

    /** Autoplay: when two or fewer songs are left, append a radio of the current song. */
    private fun maybeExtendQueue() {
        if (!settings.autoplay.value || radioJob?.isActive == true) return
        val c = controller ?: return
        if (c.repeatMode != Player.REPEAT_MODE_OFF) return
        if (c.mediaItemCount - c.currentMediaItemIndex > 2) return
        // Offline: keep going with downloads instead of a radio we can't fetch.
        if (!network.online.value) {
            fillOffline(c, target = 25)
            return
        }
        val seed = c.currentMediaItem?.mediaId ?: return
        radioJob = scope.launch {
            val existing = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }.toSet()
            val seedSong = c.currentMediaItem?.toSong()
            val more = shapeStation(repository.radio(seed).filterNot { it.id in existing }, seedSong)
                .filterNot { it.id in existing }.take(25)
            if (more.isNotEmpty()) withController { it.addMediaItems(more.map { s -> s.toMediaItem() }) }
        }
    }

    // ------------------------------------------------------- sleep timer & speed

    private var sleepJob: Job? = null
    private val _sleepAtMs = MutableStateFlow<Long?>(null)
    /** Wall-clock time the sleep timer fires; -1 = at the end of the current song; null = off. */
    val sleepAtMs: StateFlow<Long?> = _sleepAtMs.asStateFlow()

    /** Pause after [minutes]; 0 = at the end of this song; null cancels. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        _sleepAtMs.value = null
        if (minutes == null) { toast(tr("Sleep timer off", "Uyku zamanlayıcısı kapalı")); return }
        if (minutes == 0) {
            _sleepAtMs.value = -1L
            val target = controller?.currentMediaItem?.mediaId
            sleepJob = scope.launch {
                while (isActive) {
                    delay(500)
                    val c = controller ?: continue
                    val endingNow = c.duration > 0 && c.duration - c.currentPosition < 900
                    if (c.currentMediaItem?.mediaId != target || endingNow) {
                        c.pause()
                        if (c.currentMediaItem?.mediaId != target) c.seekTo(0)
                        break
                    }
                }
                _sleepAtMs.value = null
            }
            toast(tr("Stopping at the end of this song", "Bu şarkının sonunda duracak"))
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _sleepAtMs.value = at
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            // Gentle stop: fade the volume down over ~4 seconds, then pause.
            controller?.let { c ->
                for (i in 20 downTo 0) { c.volume = i / 20f; delay(200) }
                c.pause()
                c.volume = 1f
            }
            _sleepAtMs.value = null
        }
        toast(tr("Sleep timer: $minutes min", "Uyku zamanlayıcısı: $minutes dk"))
    }

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun setSpeed(speed: Float) = withController { c ->
        c.setPlaybackSpeed(speed)
        _speed.value = speed
    }

    /**
     * Station styles (Settings): "familiar" mixes your favorites and most-played songs by the
     * same artists into YouTube's radio, "discovery" drops songs you already play a lot,
     * "balanced" leaves the radio as it is.
     */
    private fun shapeStation(radio: List<SongItem>, seed: SongItem?): List<SongItem> =
        spreadArtists(shapeStationRaw(radio, seed), seed)

    private fun shapeStationRaw(radio: List<SongItem>, seed: SongItem?): List<SongItem> {
        val counts = library.playCounts.value
        return when (settings.stationStyle.value) {
            "discovery" -> {
                val known = library.favorites.value.map { it.id }.toSet()
                radio.filter { (counts[it.id] ?: 0) < 2 && it.id !in known }.ifEmpty { radio }
            }
            "familiar" -> {
                val artists = (radio.map { it.artists } + listOfNotNull(seed?.artists)).map { it.lowercase() }.toSet()
                val loved = (library.favorites.value + library.frequent.value).distinctBy { it.id }
                val related = loved.filter { it.artists.lowercase() in artists }.shuffled()
                val pool = (related + loved.shuffled()).distinctBy { it.id }
                    .filter { it.id != seed?.id && radio.none { r -> r.id == it.id } }
                // One of yours after every two from the radio.
                buildList {
                    var p = 0
                    radio.forEachIndexed { i, s ->
                        add(s)
                        if (i % 2 == 1 && p < pool.size) add(pool[p++])
                    }
                }
            }
            else -> radio
        }
    }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    suspend fun lyrics(song: SongItem, durationMs: Long) = repository.lyrics(song, durationMs)

    // ------------------------------------------------------ collections & playlists

    /** Play (or shuffle) a whole album / playlist / artist straight from a menu. */
    fun playCollection(browseId: String, shuffle: Boolean = false) {
        scope.launch {
            val songs = repository.browse(browseId).getOrNull()?.songs.orEmpty()
            if (songs.isEmpty()) toast(tr("Couldn't load songs", "Şarkılar yüklenemedi")) else if (shuffle) shufflePlay(songs) else play(songs)
        }
    }

    fun downloadCollection(browseId: String) {
        scope.launch {
            val songs = repository.browse(browseId).getOrNull()?.songs.orEmpty()
            if (songs.isEmpty()) toast(tr("Couldn't load songs", "Şarkılar yüklenemedi")) else {
                downloads.download(songs)
                toast(tr("Downloading ${songs.size} songs", "${songs.size} şarkı indiriliyor"))
            }
        }
    }

    suspend fun playlistOptions(songs: List<SongItem>) =
        repository.playlistOptions(songs.map { it.id }).getOrDefault(emptyList())

    private val _playlistChanged = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** A playlist's songs changed (id without "VL"): open pages of it reload. */
    val playlistChanged: kotlinx.coroutines.flow.SharedFlow<String> = _playlistChanged

    private fun changed(playlistId: String) { _playlistChanged.tryEmit(playlistId.removePrefix("VL")) }

    fun addToPlaylist(playlistId: String, title: String, songs: List<SongItem>, quiet: Boolean = false, onDone: () -> Unit = {}) {
        scope.launch {
            repository.addToPlaylist(playlistId, songs.map { it.id })
                .onSuccess {
                    if (!quiet) toast(tr("Added to \u201C$title\u201D", "“$title” listesine eklendi"))
                    changed(playlistId)
                    onDone()
                }
                .onFailure { toast(tr("Couldn't add to playlist", "Çalma listesine eklenemedi")) }
        }
    }

    fun createPlaylist(title: String, songs: List<SongItem>, onCreated: (String) -> Unit = {}) {
        scope.launch {
            val id = repository.createPlaylist(title, songs.map { it.id }).getOrNull()
            if (id != null) {
                toast(tr("Created \u201C$title\u201D", "“$title” oluşturuldu"))
                onCreated(id)
            } else toast(tr("Couldn't create playlist", "Çalma listesi oluşturulamadı"))
        }
    }

    fun removeFromPlaylist(playlistId: String, song: SongItem, onDone: () -> Unit) {
        val setId = song.setVideoId ?: return toast(tr("Can't remove this song", "Bu şarkı kaldırılamıyor"))
        scope.launch {
            repository.removeFromPlaylist(playlistId, song.id, setId)
                .onSuccess { toast(tr("Removed", "Kaldırıldı")); changed(playlistId); onDone() }
                .onFailure { toast(tr("Couldn't remove song", "Şarkı kaldırılamadı")) }
        }
    }

    fun renamePlaylist(playlistId: String, name: String, onDone: () -> Unit) {
        scope.launch {
            repository.renamePlaylist(playlistId, name)
                .onSuccess { onDone() }
                .onFailure { toast(tr("Couldn't rename playlist", "Çalma listesi yeniden adlandırılamadı")) }
        }
    }

    fun deletePlaylist(playlistId: String, onDone: () -> Unit) {
        scope.launch {
            repository.deletePlaylist(playlistId)
                .onSuccess { toast(tr("Playlist deleted", "Çalma listesi silindi")); onDone() }
                .onFailure { toast(tr("Couldn't delete playlist", "Çalma listesi silinemedi")) }
        }
    }

    fun savePlaylist(playlistId: String, save: Boolean) {
        scope.launch {
            // Albums are opened by their page id (MPREb_…); the library saves their playlist (OLAK5uy_…).
            val id = if (playlistId.startsWith("MPRE")) {
                repository.browse(playlistId).getOrNull()?.playlistId
            } else playlistId
            if (id == null) {
                toast(tr("Couldn't update your library", "Kitaplığınız güncellenemedi"))
                return@launch
            }
            repository.savePlaylist(id, save)
                .onSuccess { toast(if (save) tr("Added to Library", "Kitaplığa Eklendi") else tr("Removed from Library", "Kitaplıktan Kaldırıldı")) }
                .onFailure { toast(tr("Couldn't update your library", "Kitaplığınız güncellenemedi")) }
        }
    }

    /** Follow or unfollow an artist; [onDone] gets the new state, or the old one on failure. */
    fun followArtist(channelId: String, follow: Boolean, onDone: (Boolean) -> Unit) {
        scope.launch {
            repository.subscribe(channelId, follow)
                .onSuccess { toast(if (follow) tr("Following", "Takip ediliyor") else tr("Unfollowed", "Takip bırakıldı")); onDone(follow) }
                .onFailure { toast(tr("Couldn't update following", "Takip güncellenemedi")); onDone(!follow) }
        }
    }

    fun share(title: String, url: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            .putExtra(android.content.Intent.EXTRA_TEXT, url)
        context.startActivity(
            android.content.Intent.createChooser(send, tr("Share", "Paylaş")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Local favorite; when signed in, also likes/unlikes it on YouTube Music. */
    fun toggleFavorite(song: SongItem) {
        val nowFavorite = library.favorites.value.none { it.id == song.id }
        library.toggleFavorite(song)
        pushWidget()
        favoriteChanged?.invoke()
        if (auth.isLoggedIn.value) scope.launch { repository.like(song.id, nowFavorite) }
    }

    // ------------------------------------------------------------------ state

    private fun sync(p: Player) {
        val state = p.playbackState
        _isPlaying.value = p.playWhenReady && (state == Player.STATE_READY || state == Player.STATE_BUFFERING)
        _isBuffering.value = state == Player.STATE_BUFFERING
        if (p.mediaItemCount == 0) {
            if (radioJob?.isActive != true && pending.isEmpty()) _current.value = null
        } else {
            p.currentMediaItem?.toSong()?.let { _current.value = it }
        }
        _durationMs.value = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        _positionMs.value = p.currentPosition
        _shuffle.value = p.shuffleModeEnabled || smartOrder != null
        _repeatMode.value = p.repeatMode

        val timeline = p.currentTimeline
        val next = mutableListOf<QueueEntry>()
        var i = p.currentMediaItemIndex
        if (!timeline.isEmpty && i != C.INDEX_UNSET) {
            while (next.size < 200) {
                i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
                if (i == C.INDEX_UNSET) break
                next += QueueEntry(i, p.getMediaItemAt(i).toSong())
            }
        }
        _upNext.value = next
        val cur = p.currentMediaItemIndex
        _history.value = if (timeline.isEmpty || cur == C.INDEX_UNSET || p.shuffleModeEnabled) emptyList()
            else (maxOf(0, cur - 50) until cur).map { QueueEntry(it, p.getMediaItemAt(it).toSong()) }
        pushWidget()
    }

    /** Home screen widget + lock-screen favorite button follow the player. */
    private fun pushWidget() {
        val song = _current.value
        val fav = song != null && library.favorites.value.any { it.id == song.id }
        com.enes.liquidtube.widget.WidgetState.update(
            context, song?.title, song?.artists, song?.thumbnailUrl, _isPlaying.value, fav,
        )
    }

    private fun startPositionLoop() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isConnected) {
                        _positionMs.value = c.currentPosition
                        if (c.isPlaying) _current.value?.let {
                            library.listen(it, 250L)
                            countTowardHistory(it)
                        }
                        if (c.duration > 0) _durationMs.value = c.duration
                        if (++saveTick % 16 == 0) saveSession(c) // every ~4 seconds
                        lyricsWidgetTick(c)
                    }
                }
                delay(250)
            }
        }
    }

    // ------------------------------------------------- YouTube Music history sync

    private var historySong: String? = null
    private var historyMs = 0L
    private val historyPrefs = context.getSharedPreferences("history_sync", Context.MODE_PRIVATE)

    /**
     * After 30 seconds of a song (the same rule YouTube uses), it's added to your YouTube
     * Music history, so it shows under Recently Played on your other devices. Plays
     * while offline wait and are sent when the connection is back.
     */
    private fun countTowardHistory(song: SongItem) {
        if (historySong != song.id) {
            historySong = song.id
            historyMs = 0
        }
        historyMs += 250
        if (historyMs != 30_000L) return
        if (!auth.isLoggedIn.value) return
        val queued = (historyPrefs.getString("pending", "").orEmpty().split(',').filter { it.isNotBlank() } + song.id).takeLast(100)
        historyPrefs.edit().putString("pending", queued.joinToString(",")).apply()
        flushHistory()
    }

    private var historyJob: Job? = null

    private fun flushHistory() {
        if (historyJob?.isActive == true || !network.online.value) return
        historyJob = scope.launch {
            while (true) {
                val queued = historyPrefs.getString("pending", "").orEmpty().split(',').filter { it.isNotBlank() }
                val id = queued.firstOrNull() ?: break
                val ok = repository.reportPlayback(id)
                if (!ok && !network.online.value) break // try again when back online
                historyPrefs.edit().putString("pending", queued.drop(1).joinToString(",")).apply()
                if (ok) Log.i(TAG, "Added to YouTube Music history: $id")
                _accountHistoryStale = true
            }
        }
    }

    @Volatile private var _accountHistoryStale = true
    private val _accountHistory = MutableStateFlow<List<SongItem>?>(null)
    /** Recently Played from YouTube Music: every device (null until loaded). */
    val accountHistory: StateFlow<List<SongItem>?> = _accountHistory.asStateFlow()

    fun refreshAccountHistory() {
        if (!auth.isLoggedIn.value) return
        scope.launch {
            repository.history().onSuccess {
                _accountHistory.value = it
                _accountHistoryStale = false
            }
        }
    }

    // ------------------------------------------------------------- lyrics widget

    private var widgetLyricsFor: String? = null
    private var widgetLyrics: com.enes.innertube.models.Lyrics? = null
    private var widgetLyricsJob: Job? = null

    /** Keeps the home screen lyrics widget on the line being sung (only if one is placed). */
    private fun lyricsWidgetTick(c: MediaController) {
        val widget = com.enes.liquidtube.widget.LyricsWidgetState
        val onWidget = widget.installed(context)
        val onLockScreen = settings.lockScreenLyrics.value
        if (!onWidget && !onLockScreen) {
            LockScreenLyrics.publish(null)
            return
        }
        val song = _current.value
        if (song == null) {
            if (onWidget) widget.update(context, null, null, null, null, "", tr("Play a song to see its lyrics here", "Sözlerini burada görmek için bir şarkı çalın"), "", null, false)
            LockScreenLyrics.publish(null)
            return
        }
        if (widgetLyricsFor != song.id) {
            widgetLyricsFor = song.id
            widgetLyrics = null
            widgetLyricsJob?.cancel()
            widgetLyricsJob = scope.launch { widgetLyrics = repository.lyrics(song, c.duration.coerceAtLeast(0L)) }
        }
        val lyrics = widgetLyrics
        val synced = lyrics?.synced
        val pos = c.currentPosition + 250
        var key: Any? = song.id
        var progress: Float? = null
        val (prev, line, next) = when {
            synced != null && synced.isNotEmpty() -> {
                val i = synced.indexOfLast { it.timeMs <= pos }
                key = song.id to i
                if (i < 0) Triple("", "♪", synced.first().text)
                else {
                    // How far into the line we are, for the word-by-word fill: the line
                    // lasts until the next one starts (or ~5 s for the last).
                    val start = synced[i].timeMs
                    val end = synced.getOrNull(i + 1)?.timeMs ?: (start + 5_000)
                    val singing = (end - start).coerceAtMost(8_000).coerceAtLeast(500)
                    progress = ((pos - start).toFloat() / (singing * 0.85f)).coerceIn(0f, 1f)
                    Triple(
                        synced.getOrNull(i - 1)?.text.orEmpty(),
                        synced[i].text.ifBlank { "♪" },
                        synced.getOrNull(i + 1)?.text.orEmpty(),
                    )
                }
            }
            lyrics?.plain != null -> {
                val lines = lyrics.plain.orEmpty().lines().filter { it.isNotBlank() }
                Triple("", lines.firstOrNull().orEmpty(), lines.getOrNull(1).orEmpty())
            }
            widgetLyricsJob?.isActive == true -> Triple("", "…", "")
            else -> Triple("", tr("No lyrics for this song", "Bu şarkının sözleri yok"), "")
        }
        if (onWidget) widget.update(context, song.title, song.artists, song.thumbnailUrl, key, prev, line, next, progress, c.isPlaying)
        // Lock screen: only real sung lines (intros and gaps show the artist as usual).
        LockScreenLyrics.publish(
            if (onLockScreen && synced != null && line.isNotBlank() && line != "♪" && pos >= (synced.firstOrNull()?.timeMs ?: 0L)) "♪ $line" else null,
        )
    }

    /** Set by the playback service to refresh the lock-screen ★ button. */
    @Volatile var favoriteChanged: (() -> Unit)? = null

    fun isFavorite(id: String) = library.favorites.value.any { it.id == id }

    /** Playable without a connection: downloaded, or fully in the listening cache. */
    fun isOffline(song: SongItem) = downloads.downloads.value[song.id]?.isDone == true || playCache.isCached(song.id)

    internal companion object {
        const val TAG = "LiquidTube"
        const val KEY_PAUSED_AT = "paused_at"
        /** Outputs you listen on: headphones, Bluetooth audio (incl. cars), USB headsets. */
        val LISTENING_DEVICES = setOf(
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_AUX_LINE,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )
        /** Playback errors that mean "couldn't reach the server". */
        val NETWORK_ERRORS = setOf(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        const val KEY_QUEUE = "queue"
        const val KEY_INDEX = "index"
        const val KEY_POSITION = "position"
        const val KEY_SHUFFLE = "shuffle"
        const val KEY_REPEAT = "repeat"
        const val KEY_DURATION = "duration"
        const val KEY_ALBUM = "album"
        const val KEY_ARTIST_ID = "artistId"
        const val KEY_ALBUM_ID = "albumId"

        fun SongItem.toMediaItem(): MediaItem =
            MediaItem.Builder()
                .setMediaId(id)
                .setUri(PlaybackService.placeholderUri(id))
                .setCustomCacheKey(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artists)
                        .setAlbumTitle(album)
                        .setArtworkUri(thumbnailUrl?.toUri())
                        .setExtras(Bundle().apply {
                            putString(KEY_DURATION, durationText)
                            putString(KEY_ALBUM, album)
                            putString(KEY_ARTIST_ID, artistId)
                            putString(KEY_ALBUM_ID, albumId)
                        })
                        .build()
                )
                .build()

        fun MediaItem.toSong(): SongItem {
            val m = mediaMetadata
            return SongItem(
                id = mediaId,
                title = m.title?.toString() ?: "Unknown",
                artists = m.artist?.toString() ?: "Unknown Artist",
                album = m.albumTitle?.toString() ?: m.extras?.getString(KEY_ALBUM),
                durationText = m.extras?.getString(KEY_DURATION),
                thumbnailUrl = m.artworkUri?.toString(),
                artistId = m.extras?.getString(KEY_ARTIST_ID),
                albumId = m.extras?.getString(KEY_ALBUM_ID),
            )
        }
    }
}
