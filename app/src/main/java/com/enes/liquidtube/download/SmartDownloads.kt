package com.enes.liquidtube.download

import android.util.Log
import com.enes.liquidtube.data.AuthStore
import com.enes.liquidtube.data.LibraryStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.data.NetworkMonitor
import com.enes.liquidtube.data.SettingsStore
import com.enes.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps a smart "Offline Mix" downloaded while you're online, so there's always music
 * when the connection drops. It picks what you actually listen to:
 *
 *  - favorites (★), and Liked Songs from YouTube Music when signed in,
 *  - songs you play often (play counts), weighted by how recently,
 *  - your most recent plays,
 *
 * scores them, keeps the top N (Settings → Offline Mix size), downloads what's missing
 * (Wi-Fi only unless allowed on mobile data) and drops songs that fell out of the mix.
 * Songs you download yourself are never touched.
 */
@Singleton
class SmartDownloads @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val downloads: DownloadUtil,
    private val library: LibraryStore,
    private val settings: SettingsStore,
    private val network: NetworkMonitor,
    private val auth: AuthStore,
    private val repository: MusicRepository,
    private val playCache: PlaybackCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private var lastRun = 0L
    private var started = false

    private val prefs = context.getSharedPreferences("offline_mix", android.content.Context.MODE_PRIVATE)
    private val listSerializer = kotlinx.serialization.builtins.ListSerializer(SongItem.serializer())
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private val _mix = kotlinx.coroutines.flow.MutableStateFlow(
        prefs.getString("mix", null)?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() }.orEmpty()
    )
    /**
     * Every song the Offline Mix is made of, including those still downloading or waiting,
     * so the list shows the whole mix right away.
     */
    val mix: kotlinx.coroutines.flow.StateFlow<List<SongItem>> = _mix

    /** Smart Downloads turned off: there's no mix to show. */
    fun clearMix() = setMix(emptyList())

    private fun setMix(songs: List<SongItem>) {
        _mix.value = songs
        prefs.edit().putString("mix", json.encodeToString(listSerializer, songs)).apply()
    }

    fun start() {
        if (started) return
        started = true
        // A song just finished downloading, or a played song is now fully cached: keep
        // its cover and lyrics with it.
        downloads.onSongSaved = { song -> scope.launch { saveExtras(listOf(song)) } }
        scope.launch {
            playCache.cached.collect { cached -> saveExtras(cached.take(200)) }
        }
        // Back online: catch up on anything that couldn't be saved while offline.
        scope.launch {
            network.online.collect { online -> if (online) { delay(5_000); saveExtras(offlineSongs()) } }
        }
        scope.launch {
            delay(8_000) // let the app settle first
            combine(network.online, settings.smartDownloads, settings.smartSize) { online, on, size -> Triple(online, on, size) }
                .distinctUntilChanged()
                .collect { (online, on, _) -> if (online && on) refresh(force = false) }
        }
    }

    /** Recompute and sync the mix. Runs at most every 20 minutes unless [force]d. */
    fun refresh(force: Boolean = true) {
        scope.launch {
            lock.withLock {
                if (!settings.smartDownloads.value) return@withLock
                val now = System.currentTimeMillis()
                if (!force && now - lastRun < 20 * 60_000L) return@withLock
                if (!network.online.value) return@withLock
                if (!settings.smartOnCellular.value && !network.isUnmetered()) {
                    Log.i(TAG, "Offline Mix: waiting for Wi-Fi")
                    return@withLock
                }
                lastRun = now
                val mix = pick(settings.smartSize.value)
                setMix(mix)
                // Keep auto-downloads only for mix songs the cache doesn't already hold
                // (drops the duplicate copies earlier versions made, and anything over the size).
                downloads.pruneAuto(mix.filterNot { playCache.isCached(it.id) }.map { it.id }.toSet())
                // Songs in the listening cache are offline already: no second copy. If the
                // cache lets one go later, the next refresh downloads it.
                downloads.downloadAuto(mix.filterNot { playCache.isCached(it.id) })
                Log.i(TAG, "Offline Mix: ${mix.size} songs selected")
                saveExtras(offlineSongs())
            }
        }
    }

    private var lyricsJob: kotlinx.coroutines.Job? = null

    /**
     * Saves lyrics for offline songs in the background (a few at a time, gently), so
     * they show without a connection and can be found by a line in Search.
     */
    /** Everything playable offline: downloads and the listening cache. */
    private fun offlineSongs(): List<SongItem> =
        (downloads.downloads.value.values.filter { it.isDone }.map { it.song } + playCache.cached.value).distinctBy { it.id }

    private val extrasQueue = java.util.concurrent.ConcurrentLinkedQueue<SongItem>()
    private var extrasJob: kotlinx.coroutines.Job? = null

    /**
     * Keeps each offline song's cover and lyrics on the phone, so both show without a
     * connection. Runs gently in the background; songs whose extras are saved are skipped.
     */
    private fun saveExtras(songs: List<SongItem>) {
        songs.forEach { song ->
            val need = (song.thumbnailUrl != null && !com.enes.liquidtube.data.OfflineArt.has(song.thumbnailUrl!!)) ||
                !com.enes.liquidtube.data.LyricsStore.has(song.id)
            if (need && extrasQueue.none { it.id == song.id }) extrasQueue += song
        }
        if (extrasJob?.isActive == true) return
        extrasJob = scope.launch {
            var done = 0
            while (true) {
                if (!network.online.value) break
                val song = extrasQueue.poll() ?: break
                runCatching { com.enes.liquidtube.data.OfflineArt.save(context, song.thumbnailUrl) }
                if (!com.enes.liquidtube.data.LyricsStore.has(song.id)) {
                    repository.lyrics(song, 0L)
                    delay(1_200) // be gentle with the lyrics services
                }
                done++
            }
            if (done > 0) Log.i(TAG, "Offline extras: covers and lyrics saved for $done songs")
        }
    }

    private suspend fun pick(size: Int): List<SongItem> {
        val scores = HashMap<String, Double>()
        val songs = HashMap<String, SongItem>()
        fun add(song: SongItem, points: Double) {
            songs.putIfAbsent(song.id, song)
            scores[song.id] = (scores[song.id] ?: 0.0) + points
        }

        val counts = library.playCounts.value
        library.favorites.value.forEach { add(it, 6.0) }
        library.frequent.value.forEachIndexed { i, s ->
            val plays = counts[s.id] ?: 2
            add(s, kotlin.math.ln(1.0 + plays) * 3.0 + (40 - i).coerceAtLeast(0) * 0.05)
        }
        library.recents.value.take(40).forEachIndexed { i, s -> add(s, 2.5 - i * 0.05) }
        var liked = 0
        if (auth.isLoggedIn.value) {
            // Liked Songs, a couple of pages deep (the first page is only ~100).
            val page = repository.browse("VLLM").getOrNull()
            var likedSongs = page?.songs.orEmpty()
            var token = page?.continuation
            while (token != null && likedSongs.size < 250) {
                val (more, next) = repository.browseMore(token).getOrNull() ?: break
                if (more.isEmpty()) break
                likedSongs = likedSongs + more
                token = next
            }
            likedSongs.forEachIndexed { i, s ->
                add(s, 4.0 - i * 0.012)
                liked++
            }
        }
        Log.i(TAG, "Offline Mix sources: ${library.favorites.value.size} favorites, ${library.frequent.value.size} frequent, " +
            "${library.recents.value.size} recent, $liked liked (signed in: ${auth.isLoggedIn.value})")
        // The size is how many songs get downloaded. Songs already in the listening cache
        // are in the mix too but cost nothing, so recent plays don't crowd out the rest.
        val ranked = scores.entries.sortedByDescending { it.value }.mapNotNull { songs[it.key] }
        val picked = ArrayList<SongItem>()
        var fresh = 0
        for (song in ranked) {
            // Already in the listening cache: offline for free (no download, no extra space).
            // Anything else, downloaded already or not, counts toward the size you chose.
            if (!playCache.isCached(song.id)) {
                if (fresh >= size) continue
                fresh++
            }
            picked += song
        }
        return picked
    }

    private companion object { const val TAG = "LiquidTube" }
}
