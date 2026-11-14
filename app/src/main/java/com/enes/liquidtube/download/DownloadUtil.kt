package com.enes.liquidtube.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.enes.liquidtube.playback.StreamResolver
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadInfo(
    val song: SongItem,
    val state: Int,          // Download.STATE_*
    val percent: Float,      // 0..100, or -1 if unknown
) {
    val isDone get() = state == Download.STATE_COMPLETED
    val isActive get() = state == Download.STATE_QUEUED || state == Download.STATE_DOWNLOADING || state == Download.STATE_RESTARTING
}

/**
 * Offline downloads (a Premium feature): songs are stored in a permanent cache
 * keyed by videoId. The player reads that cache first, so downloaded songs play
 * instantly and work without a connection.
 */
@OptIn(UnstableApi::class)
@Singleton
class DownloadUtil @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: StreamResolver,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val databaseProvider = StandaloneDatabaseProvider(context)

    val cache = SimpleCache(File(context.filesDir, "downloads"), NoOpCacheEvictor(), databaseProvider)

    /** The listening cache, once it exists (it's built after this class, so it's attached later). */
    @Volatile private var listening: androidx.media3.datasource.cache.Cache? = null

    /** Called by [PlaybackCache]: downloads then copy songs you've already heard from it. */
    fun attachListeningCache(cache: androidx.media3.datasource.cache.Cache) {
        listening = cache
    }

    // A download first reads whatever the listening cache already has (a song you've played
    // is copied locally, no network), and fetches only what's missing.
    private val sourceFactory = androidx.media3.datasource.DataSource.Factory {
        val heard = listening
        if (heard == null) {
            resolver.dataSourceFactory.createDataSource()
        } else {
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(heard)
                .setUpstreamDataSourceFactory(resolver.dataSourceFactory)
                .setCacheWriteDataSinkFactory(null) // read-only: never write into the listening cache
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        }
    }

    val manager: DownloadManager = DownloadManager(
        context, databaseProvider, cache, sourceFactory, Executors.newFixedThreadPool(3),
    ).apply {
        maxParallelDownloads = 3
        addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
                update(download)
                ensureProgressLoop()
            }

            override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
                _downloads.update { it - download.request.id }
            }
        })
    }

    private val _downloads = MutableStateFlow<Map<String, DownloadInfo>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadInfo>> = _downloads.asStateFlow()

    private var progressJob: Job? = null

    private val prefs = context.getSharedPreferences("downloads_meta", Context.MODE_PRIVATE)
    private val _autoIds = MutableStateFlow(prefs.getStringSet(KEY_AUTO, emptySet()).orEmpty().toSet())
    /** Songs downloaded automatically for the smart Offline Mix (not by you). */
    val autoIds: StateFlow<Set<String>> = _autoIds.asStateFlow()

    private fun setAuto(ids: Set<String>) {
        _autoIds.value = ids
        prefs.edit().putStringSet(KEY_AUTO, ids).apply()
    }

    /** Downloads [songs] for the smart mix; songs you downloaded yourself stay yours. */
    fun downloadAuto(songs: List<SongItem>) {
        val existing = _downloads.value
        val fresh = songs.filter { existing[it.id] == null }
        setAuto(_autoIds.value + fresh.map { it.id })
        download(fresh, manual = false)
    }

    /** Removes smart-mix downloads that aren't in [keep] any more. */
    fun pruneAuto(keep: Set<String>) {
        val drop = _autoIds.value - keep
        drop.forEach(::remove)
        if (drop.isNotEmpty()) setAuto(_autoIds.value - drop)
    }

    init {
        scope.launch {
            val all = withContext(Dispatchers.IO) {
                buildList {
                    manager.downloadIndex.getDownloads().use { c -> while (c.moveToNext()) add(c.download) }
                }
            }
            _downloads.value = all.mapNotNull { it.toInfo() }.associateBy { it.song.id }
            ensureProgressLoop()
            restoreDownloads()
        }
    }

    /**
     * Just restored from a backup: download its songs again, once. Yours as your own
     * downloads; the Offline Mix's as automatic ones, so the mix stays dynamic and
     * replaces them over time like any other.
     */
    private fun restoreDownloads() {
        val serializer = kotlinx.serialization.builtins.ListSerializer(SongItem.serializer())
        fun take(key: String): List<SongItem> {
            val raw = prefs.getString(key, null) ?: return emptyList()
            prefs.edit().remove(key).apply()
            return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        }
        val mine = take("restore_queue")
        val mix = take("restore_mix")
        if (mine.isNotEmpty()) download(mine, manual = true)
        if (mix.isNotEmpty()) downloadAuto(mix)
        if (mine.isNotEmpty() || mix.isNotEmpty()) {
            android.util.Log.i("LiquidTube", "Restore: downloading ${mine.size} songs + ${mix.size} Offline Mix songs")
        }
    }

    fun download(songs: List<SongItem>, manual: Boolean = true) {
        // Downloading something yourself makes it a regular download, even if it was auto.
        if (manual) setAuto(_autoIds.value - songs.map { it.id }.toSet())
        songs.filter { _downloads.value[it.id]?.let { d -> d.isDone || d.isActive } != true }.forEach { song ->
            val request = DownloadRequest.Builder(song.id, StreamResolver.placeholderUri(song.id).toUri())
                .setCustomCacheKey(song.id)
                .setData(json.encodeToString(SongItem.serializer(), song).toByteArray())
                .build()
            DownloadService.sendAddDownload(context, AppDownloadService::class.java, request, false)
        }
    }

    fun remove(videoId: String) {
        if (videoId in _autoIds.value) setAuto(_autoIds.value - videoId)
        DownloadService.sendRemoveDownload(context, AppDownloadService::class.java, videoId, false)
    }

    private fun update(download: Download) {
        val old = _downloads.value[download.request.id]
        // Progress ticks only count when the whole percent changes: every list showing
        // download badges redraws on each update, so don't wake them for nothing.
        if (old != null && old.state == download.state &&
            kotlin.math.floor(old.percent) == kotlin.math.floor(download.percentDownloaded)
        ) return
        val info = download.toInfo() ?: return
        _downloads.update { it + (info.song.id to info) }
        // Just finished: keep its cover and lyrics with it, for offline.
        if (info.isDone && old?.isDone != true) onSongSaved?.invoke(info.song)
    }

    /** Set by Smart Downloads: saves a finished song's cover and lyrics. */
    @Volatile var onSongSaved: ((SongItem) -> Unit)? = null

    private fun ensureProgressLoop() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                val active = manager.currentDownloads
                if (active.isEmpty()) break
                active.forEach(::update)
                delay(700)
            }
        }
    }

    private companion object { const val KEY_AUTO = "auto_ids" }

    private fun Download.toInfo(): DownloadInfo? {
        val song = runCatching {
            json.decodeFromString(SongItem.serializer(), String(request.data))
        }.getOrNull() ?: return null
        return DownloadInfo(song, state, percentDownloaded)
    }
}
