package com.enes.liquidtube.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.SimpleCache
import com.enes.liquidtube.data.SettingsStore
import com.enes.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.TreeSet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The listening cache: every song you stream is saved as it plays (up to a size limit,
 * least-recently-played songs go first). Once a song has played through, it's fully in
 * the cache and works offline just like a download. It shows up in the Offline Mix.
 */
@OptIn(UnstableApi::class)
@Singleton
class PlaybackCache @Inject constructor(
    @ApplicationContext context: Context,
    downloads: DownloadUtil,
    private val settings: SettingsStore,
) {
    private val evictor = SizedLruEvictor { settings.cacheSizeMb.value * 1024L * 1024L }
    val cache: Cache = SimpleCache(File(context.filesDir, "listening_cache"), evictor, downloads.databaseProvider)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("listening_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(SongItem.serializer())
    private val known = LinkedHashMap<String, SongItem>().apply {
        prefs.getString(KEY_SONGS, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?.forEach { put(it.id, it) }
    }

    private val _cached = MutableStateFlow<List<SongItem>>(emptyList())
    /** Songs that are fully in the cache (playable offline), most recent first. */
    val cached: StateFlow<List<SongItem>> = _cached.asStateFlow()

    private val _bytes = MutableStateFlow(0L)
    /** How much space the cache is using. */
    val bytesUsed: StateFlow<Long> = _bytes.asStateFlow()

    init {
        downloads.attachListeningCache(cache)
        refresh()
    }

    /** Remember what a song is, so the cache can be listed with titles and artwork. */
    fun remember(song: SongItem) {
        synchronized(known) {
            known.remove(song.id)
            known[song.id] = song
            while (known.size > 2000) known.remove(known.keys.first())
            prefs.edit().putString(KEY_SONGS, json.encodeToString(serializer, known.values.toList())).apply()
        }
    }

    fun isCached(id: String) = _cached.value.any { it.id == id }

    /** Re-scan which songs are complete. Cheap; called as songs play. */
    fun refresh() {
        scope.launch {
            val complete = runCatching {
                cache.keys.filter { key ->
                    val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
                    length > 0 && cache.isCached(key, 0, length)
                }.toSet()
            }.getOrDefault(emptySet())
            val songs = synchronized(known) { known.values.reversed().filter { it.id in complete } }
            _cached.value = songs
            _bytes.value = runCatching { cache.cacheSpace }.getOrDefault(0L)
        }
    }

    /** Make room after the size limit was lowered. */
    fun trim() {
        scope.launch {
            runCatching { evictor.evictNow(cache) }
            refresh()
        }
    }

    fun clear() {
        scope.launch {
            runCatching { cache.keys.toList().forEach { cache.removeResource(it) } }
            refresh()
        }
    }

    private companion object { const val KEY_SONGS = "songs" }
}

/** Least-recently-used eviction with a limit that can change at runtime (Settings). */
@OptIn(UnstableApi::class)
private class SizedLruEvictor(private val maxBytes: () -> Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan>(compareBy<CacheSpan>({ it.lastTouchTimestamp }, { it.key }, { it.position }))
    private var size = 0L

    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun evictNow(cache: Cache) = evict(cache, 0)

    private fun evict(cache: Cache, required: Long) {
        while (size + required > maxBytes() && spans.isNotEmpty()) {
            cache.removeSpan(spans.first())
        }
    }
}
