package com.enes.liquidtube.premium

import android.util.Log
import com.enes.liquidtube.data.SettingsStore
import com.enes.innertube.InnerTube
import com.enes.innertube.models.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Premium-quality audio (256kbps) for signed-in YouTube Music Premium accounts.
 *
 * Separate from the regular playback path on purpose: it only ever *adds* a better
 * stream. Any failure returns null and the normal resolver takes over unchanged.
 *
 *  1. signed-in WEB_REMIX player request (with the player's signatureTimestamp),
 *  2. pick the best audio: 774 (Opus 256) or 141 (AAC 256),
 *  3. decipher its signature with YouTube's own player code ([PlayerScript]),
 *  4. attach a PO token minted by BotGuard ([PoTokenMinter]),
 *  5. prove it works by fetching a byte from the middle of the file.
 */
@Singleton
class PremiumStreams @Inject constructor(
    private val player: PlayerScript,
    private val minter: PoTokenMinter,
    private val settings: SettingsStore,
) {
    @Volatile private var backoffUntil = 0L
    @Volatile private var failures = 0
    @Volatile private var dataSyncId: String? = null

    /** Whether to try at all for this request. */
    fun enabled(lowQuality: Boolean): Boolean =
        !lowQuality && settings.premiumAudio.value && InnerTube.isLoggedIn && System.currentTimeMillis() >= backoffUntil

    /** A 256kbps stream for [videoId], or null (caller falls back). Bounded to [budgetMs]. */
    suspend fun resolve(videoId: String, budgetMs: Long = if (warm) 10_000 else 30_000): StreamInfo? {
        val started = System.currentTimeMillis()
        val result = withTimeoutOrNull(budgetMs) {
            try {
                attempt(videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(JsHost.TAG, "Premium failed for $videoId: ${e.message?.take(300)}")
                null
            }
        }
        if (result == null) {
            if (System.currentTimeMillis() - started >= budgetMs - 50) Log.w(JsHost.TAG, "Premium timed out for $videoId after ${budgetMs}ms")
            failures++
            // Don't slow every song down when it isn't working: back off, growing up to 30 min.
            backoffUntil = System.currentTimeMillis() + (60_000L * failures * failures).coerceAtMost(30 * 60_000L)
        } else {
            failures = 0
            warm = true
        }
        return result
    }

    @Volatile private var warm = false

    /** Load the player and mint a token ahead of time, so the first song isn't kept waiting. */
    suspend fun warmUp() {
        if (!InnerTube.isLoggedIn || !settings.premiumAudio.value) return
        runCatching {
            player.ensure()
            val id = dataSyncId ?: InnerTube.dataSyncId()?.also { dataSyncId = it } ?: InnerTube.currentVisitorData() ?: return
            minter.mint(id)
            warm = true
            Log.i(JsHost.TAG, "Premium warmed up")
        }.onFailure { Log.w(JsHost.TAG, "Premium warm-up failed: ${it.message?.take(200)}") }
    }

    private suspend fun attempt(videoId: String): StreamInfo? {
        player.ensure()
        val res = InnerTube.webMusicPlayer(videoId, player.signatureTimestamp)
        val status = (res["playabilityStatus"] as? JsonObject)?.str("status")
        if (status != "OK") {
            Log.i(JsHost.TAG, "Premium: player status $status for $videoId")
            return null
        }
        val streaming = res["streamingData"] as? JsonObject ?: return null
        val formats = (streaming["adaptiveFormats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("mimeType")?.startsWith("audio/") == true }
        val best = PREFERRED.firstNotNullOfOrNull { itag -> formats.firstOrNull { it.str("itag") == itag } }
        if (best == null) {
            Log.i(JsHost.TAG, "Premium: no 256kbps format (${formats.map { it.str("itag") }}) — not a Premium account?")
            return null
        }

        var url = best.str("url") ?: best.str("signatureCipher")?.let { player.decipher(it) } ?: return null
        val identifier = dataSyncId ?: InnerTube.dataSyncId()?.also { dataSyncId = it } ?: InnerTube.currentVisitorData()
            ?: error("no identifier for PO token")
        url += "&pot=" + java.net.URLEncoder.encode(minter.mint(identifier), "UTF-8")

        val code = withContext(Dispatchers.IO) { probe(url) }
        if (code != 206 && code != 200) {
            Log.w(JsHost.TAG, "Premium: mid-file check HTTP $code for itag ${best.str("itag")}")
            if (code == 403) { minter.invalidate(); player.invalidate() } // stale token or player: rebuild next time
            return null
        }
        val bitrate = best.str("bitrate")?.toIntOrNull() ?: 256_000
        val expiresIn = streaming.str("expiresInSeconds")?.toLongOrNull() ?: 21_540L
        Log.i(JsHost.TAG, "Premium stream for $videoId: itag ${best.str("itag")} @ ${bitrate / 1000}kbps")
        return StreamInfo(
            url = url,
            mimeType = best.str("mimeType") ?: "audio/mp4",
            bitrate = bitrate,
            userAgent = JsHost.UA,
            clientName = "WEB_REMIX Premium",
            loudnessDb = InnerTube.loudnessOf(res, best),
            expiresAtMs = System.currentTimeMillis() + (expiresIn - 120).coerceAtLeast(60) * 1000,
        )
    }

    /** Fetch two bytes ~1.5MB in: plain URLs fail exactly there without a valid token. */
    private fun probe(url: String): Int = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", JsHost.UA)
        c.setRequestProperty("Range", "bytes=1500000-1500001")
        c.setRequestProperty("Origin", "https://music.youtube.com")
        c.setRequestProperty("Referer", "https://music.youtube.com/")
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        c.responseCode.also { c.disconnect() }
    }.getOrDefault(-1)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        /** Opus 256 first (best quality for size), then AAC 256. */
        val PREFERRED = listOf("774", "141")
    }
}
