package com.enes.liquidtube.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.data.SettingsStore
import com.enes.innertube.models.StreamInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns placeholder URIs (music.youtube.com/watch?v=ID) into real googlevideo URLs
 * right when ExoPlayer or the downloader opens them. Shared by playback and downloads.
 */
@OptIn(UnstableApi::class)
@Singleton
class StreamResolver @Inject constructor(
    private val repository: MusicRepository,
    private val settings: SettingsStore,
    private val premium: com.enes.liquidtube.premium.PremiumStreams,
    private val quality: com.enes.liquidtube.premium.QualityStore,
) {
    init {
        // The premium module replaces InnerTube's old probe (which couldn't decipher).
        com.enes.innertube.InnerTube.legacyPremiumProbe = false
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            kotlinx.coroutines.delay(6_000) // after sign-in state and the UI have settled
            premium.warmUp()
        }
    }

    private val cache = ConcurrentHashMap<String, StreamInfo>()

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)

    val dataSourceFactory: ResolvingDataSource.Factory =
        ResolvingDataSource.Factory(httpFactory) { spec -> resolve(spec) }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    /** Use this already-verified stream for [videoId] next (the quality upgrader). */
    fun prime(videoId: String, stream: StreamInfo) {
        cache[videoId] = stream
    }

    /** Runs on loader threads, so blocking is fine. */
    private fun resolve(spec: DataSpec): DataSpec {
        if (spec.uri.host != PLACEHOLDER_HOST) return spec
        val id = spec.uri.getQueryParameter("v") ?: return spec

        val now = System.currentTimeMillis()
        val lowQuality = settings.dataSaver.value
        val stream = cache[id]?.takeIf { it.expiresAtMs > now }
            // 1. Premium 256kbps (separate module); any failure falls through unchanged.
            ?: (if (premium.enabled(lowQuality)) runBlocking { premium.resolve(id) } else null)?.also {
                cache[id] = it
                Log.i(TAG, "Resolved $id via ${it.clientName}: ${it.mimeType} @ ${it.bitrate / 1000}kbps")
            }
            // 2. The regular path.
            ?: runBlocking { repository.resolveStream(id, lowQuality) }?.also {
                cache[id] = it
                Log.i(TAG, "Resolved $id via ${it.clientName}: ${it.mimeType} @ ${it.bitrate / 1000}kbps")
            }
            ?: throw IOException("No playable stream for $id")

        // Whatever is fetched now becomes the stored copy (cache/download): remember its quality.
        quality.mark(id, premium = stream.clientName.contains("Premium"))
        stream.loudnessDb?.let { quality.setLoudness(id, it) }
        val headers = if (stream.clientName.contains("Premium")) {
            mapOf("User-Agent" to stream.userAgent, "Origin" to "https://music.youtube.com", "Referer" to "https://music.youtube.com/")
        } else {
            mapOf("User-Agent" to stream.userAgent)
        }
        return spec.withUri(stream.url.toUri()).withAdditionalHeaders(headers)
    }

    companion object {
        const val TAG = "LiquidTube"
        const val PLACEHOLDER_HOST = "music.youtube.com"
        fun placeholderUri(videoId: String) = "https://$PLACEHOLDER_HOST/watch?v=$videoId"
    }
}
