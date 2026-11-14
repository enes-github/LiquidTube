package com.enes.innertube

import com.enes.innertube.models.LyricLine
import com.enes.innertube.models.Lyrics
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * Time-synced lyrics from LRCLIB (lrclib.net, free and open). Matches by title,
 * artist and duration; falls back to a fuzzy search.
 */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"
    private const val UA = "LiquidTube/0.3 (personal music player)"

    @Serializable
    private data class Track(
        val trackName: String? = null,
        val artistName: String? = null,
        val duration: Double? = null,
        val instrumental: Boolean = false,
        val plainLyrics: String? = null,
        val syncedLyrics: String? = null,
    )

    suspend fun lyrics(title: String, artists: String, durationSec: Int?): Lyrics? {
        val track = cleanTitle(title)
        val artist = primaryArtist(artists)

        val exact = runCatching {
            Http.client.get("$BASE/get") {
                header(HttpHeaders.UserAgent, UA)
                parameter("track_name", track)
                parameter("artist_name", artist)
                if (durationSec != null && durationSec > 0) parameter("duration", durationSec)
            }.body<Track>()
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        exact?.toLyrics()?.let { return it }

        val results = runCatching {
            Http.client.get("$BASE/search") {
                header(HttpHeaders.UserAgent, UA)
                parameter("track_name", track)
                parameter("artist_name", artist)
            }.body<List<Track>>()
        }.onFailure { if (it is CancellationException) throw it }.getOrNull().orEmpty()

        val best = results
            .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            .filter { durationSec == null || durationSec <= 0 || it.duration == null || abs(it.duration - durationSec) <= 6 }
            .sortedByDescending { !it.syncedLyrics.isNullOrBlank() }
            .firstOrNull()
        return best?.toLyrics()
    }

    private fun Track.toLyrics(): Lyrics? {
        val synced = syncedLyrics?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        val plain = plainLyrics?.takeIf { it.isNotBlank() }
        if (synced == null && plain == null) return null
        return Lyrics(synced = synced, plain = plain, source = "LRCLIB")
    }

    private val LINE = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    fun parseLrc(lrc: String): List<LyricLine> =
        lrc.lineSequence().flatMap { raw ->
            val stamps = LINE.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptySequence()
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.asSequence().map { m ->
                val (min, sec, frac) = m.destructured
                val ms = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + ms, text)
            }
        }.sortedBy { it.timeMs }.toList()

    private val NOISE = Regex(
        """\s*[(\[](official|lyric|lyrics|audio|video|music video|visualizer|explicit|clean|remaster|hd|4k|mv)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val FEAT = Regex("""\s*[(\[]?\s*(feat\.?|ft\.?|featuring)\s.*$""", RegexOption.IGNORE_CASE)

    private fun cleanTitle(title: String): String {
        var t = title.replace(NOISE, "")
        t = t.replace(FEAT, "")
        // "Artist - Title" uploads
        if (" - " in t) t = t.substringAfter(" - ")
        return t.trim().ifEmpty { title }
    }

    private fun primaryArtist(artists: String): String =
        artists.split(",", " & ", " x ", " X ", " feat", " ft.", " and ").first().trim()
}
