package com.enes.liquidtube.premium

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.CacheWriter
import androidx.core.net.toUri
import com.enes.liquidtube.data.LibraryStore
import com.enes.liquidtube.data.NetworkMonitor
import com.enes.liquidtube.data.SettingsStore
import com.enes.liquidtube.download.DownloadUtil
import com.enes.liquidtube.download.PlaybackCache
import com.enes.liquidtube.playback.StreamResolver
import com.enes.innertube.InnerTube
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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Upgrades stored songs (listening cache and downloads) to Premium 256kbps in the
 * background. On Wi-Fi, while Premium Audio is on and working, it takes songs whose stored
 * copy is still standard quality, most-played first, gets the verified 256kbps stream,
 * and rewrites the stored copy with it, one song at a time. It skips whatever is playing
 * and stops at the first sign that premium isn't available right now.
 */
@OptIn(UnstableApi::class)
@Singleton
class QualityUpgrader @Inject constructor(
    private val premium: PremiumStreams,
    private val resolver: StreamResolver,
    private val quality: QualityStore,
    private val downloads: DownloadUtil,
    private val playCache: PlaybackCache,
    private val network: NetworkMonitor,
    private val settings: SettingsStore,
    private val library: LibraryStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var currentId: () -> String? = { null }

    /** Songs left to upgrade (for Settings). */
    private val _pending = MutableStateFlow(0)
    val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val keyFactory = CacheKeyFactory { spec -> spec.uri.getQueryParameter("v") ?: spec.key ?: spec.uri.toString() }

    fun start(currentSong: () -> String?) {
        currentId = currentSong
        if (job?.isActive == true) return
        job = scope.launch {
            delay(60_000) // let playback and the Offline Mix settle first
            while (isActive) {
                runCatching { pass() }.onFailure { Log.w(JsHost.TAG, "Upgrade pass failed: ${it.message}") }
                delay(20 * 60_000L)
            }
        }
    }

    /** Songs stored at standard quality, most-played first. */
    private fun candidates(): List<Pair<String, Cache>> {
        val premiumIds = quality.premiumIds.value
        val counts = library.playCounts.value
        val downloaded = downloads.downloads.value.values.filter { it.isDone }.map { it.song.id to downloads.cache }
        val cached = playCache.cached.value.map { it.id to playCache.cache }
        return (downloaded + cached)
            .distinctBy { it.first }
            .filter { it.first !in premiumIds }
            .sortedByDescending { counts[it.first] ?: 0 }
    }

    private suspend fun pass() {
        _pending.value = candidates().size
        if (!settings.premiumAudio.value || !InnerTube.isLoggedIn || settings.dataSaver.value) return
        if (!network.isUnmetered()) return
        val todo = candidates()
        if (todo.isEmpty()) return
        _running.value = true
        try {
            var done = 0
            for ((id, cache) in todo.take(BATCH)) {
                if (!network.isUnmetered() || !settings.premiumAudio.value) break
                if (id == currentId()) continue
                val stream = premium.resolve(id, budgetMs = 20_000)
                if (stream == null) {
                    Log.i(JsHost.TAG, "Upgrade paused: premium unavailable for $id")
                    break // no point hammering YouTube; try again next pass
                }
                if (rewrite(id, cache, stream)) done++
                _pending.value = candidates().size
                delay(1500)
            }
            Log.i(JsHost.TAG, "Upgraded $done songs to 256kbps (${candidates().size} left)")
            playCache.refresh()
        } finally {
            _running.value = false
        }
    }

    /** Replaces the stored copy of [id] in [cache] with the premium stream. */
    private suspend fun rewrite(id: String, cache: Cache, stream: com.enes.innertube.models.StreamInfo): Boolean =
        withContext(Dispatchers.IO) {
            resolver.prime(id, stream) // the writer will fetch exactly this verified stream
            val source = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(resolver.dataSourceFactory)
                .setCacheKeyFactory(keyFactory)
                .createDataSource()
            val spec = DataSpec.Builder().setUri(StreamResolver.placeholderUri(id).toUri()).setKey(id).build()
            runCatching {
                cache.removeResource(id)
                CacheWriter(source, spec, null, null).cache()
                quality.mark(id, premium = true)
                Log.i(JsHost.TAG, "Upgraded $id to ${stream.bitrate / 1000}kbps")
                true
            }.getOrElse {
                Log.w(JsHost.TAG, "Upgrade of $id failed: ${it.message}")
                false
            }
        }

    private companion object { const val BATCH = 25 }
}
