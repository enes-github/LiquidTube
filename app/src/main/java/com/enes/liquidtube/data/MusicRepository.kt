package com.enes.liquidtube.data

import android.util.Log
import com.enes.innertube.InnerTube
import com.enes.innertube.LrcLib
import com.enes.innertube.models.BrowsePage
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.FeedShelf
import com.enes.innertube.models.Lyrics
import com.enes.innertube.models.SearchResults
import com.enes.innertube.models.SongItem
import com.enes.innertube.models.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The app's single door to music data. Everything above this (ViewModels, UI,
 * playback service) depends only on this class.
 */
class MusicRepository {

    private val lyricsCache = ConcurrentHashMap<String, Lyrics>()
    private val noLyrics = ConcurrentHashMap.newKeySet<String>()

    private suspend fun <T> io(name: String, block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.onFailure {
                Log.e(TAG, "$name failed", it)
                // A network-level failure may mean the connection just dropped: have it checked.
                if (it is java.io.IOException || it.cause is java.io.IOException) onNetworkError?.invoke()
            }
        }

    suspend fun searchSongs(query: String): Result<List<SongItem>> = io("searchSongs") { InnerTube.searchSongs(query) }

    suspend fun search(query: String): Result<SearchResults> = io("search") { InnerTube.search(query) }

    suspend fun home(): Result<List<FeedShelf>> = io("home") { InnerTube.home() }

    /** Can we still get a playable stream? (YouTube changes break this without warning.) */
    suspend fun streamHealth(videoId: String): Boolean =
        io("streamHealth") { InnerTube.resolveStream(videoId) != null }.getOrDefault(false)

    suspend fun homeFeed(params: String?): Result<com.enes.innertube.models.HomeFeed> = io("homeFeed") { InnerTube.homeFeed(params) }

    suspend fun searchFiltered(query: String, filter: com.enes.innertube.models.SearchFilter) =
        io("searchFiltered") { InnerTube.searchFiltered(query, filter) }

    suspend fun searchMore(token: String) = io("searchMore") { InnerTube.searchMore(token) }

    suspend fun section(ref: com.enes.innertube.models.SectionRef) = io("section") { InnerTube.section(ref) }

    suspend fun sectionMore(token: String) = io("sectionMore") { InnerTube.sectionMore(token) }

    suspend fun explore(): Result<List<FeedShelf>> = io("explore") {
        coroutineScope {
            val explore = async { runCatching { InnerTube.explore() }.getOrDefault(emptyList()) }
            val charts = async { runCatching { InnerTube.charts() }.getOrDefault(emptyList()) }
            explore.await() + charts.await()
        }
    }

    suspend fun browse(browseId: String): Result<BrowsePage> = io("browse($browseId)") { InnerTube.browse(browseId) }

    suspend fun browseMore(token: String): Result<Pair<List<SongItem>, String?>> =
        io("browseMore") { InnerTube.browseContinuation(token) }

    suspend fun library(browseId: String): Result<List<CollectionItem>> = io("library($browseId)") { InnerTube.library(browseId) }

    suspend fun playlistOptions(videoIds: List<String>) = io("playlistOptions") { InnerTube.addToPlaylistOptions(videoIds) }
    suspend fun createPlaylist(title: String, videoIds: List<String>) = io("createPlaylist") { InnerTube.createPlaylist(title, videoIds) }
    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) = io("addToPlaylist") { InnerTube.addToPlaylist(playlistId, videoIds) }
    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) =
        io("removeFromPlaylist") { InnerTube.removeFromPlaylist(playlistId, videoId, setVideoId) }
    suspend fun renamePlaylist(playlistId: String, name: String) = io("renamePlaylist") { InnerTube.renamePlaylist(playlistId, name) }
    suspend fun deletePlaylist(playlistId: String) = io("deletePlaylist") { InnerTube.deletePlaylist(playlistId) }
    suspend fun savePlaylist(playlistId: String, save: Boolean) = io("savePlaylist") { InnerTube.savePlaylist(playlistId, save) }

    suspend fun subscribe(channelId: String, subscribe: Boolean) = io("subscribe") { InnerTube.subscribe(channelId, subscribe) }

    suspend fun like(videoId: String, liked: Boolean) {
        io("like") { InnerTube.like(videoId, liked) }
    }

    suspend fun resolveStream(videoId: String, lowQuality: Boolean = false): StreamInfo? =
        io("resolveStream($videoId)") { InnerTube.resolveStream(videoId, lowQuality) }.getOrNull()

    /**
     * Songs to suggest for one of your playlists: radios of the songs added last (so the
     * suggestions follow what you're putting in), or for an empty playlist, its name as a
     * search plus your recent listening. Never suggests what's already in it.
     */
    suspend fun suggestForPlaylist(title: String, songs: List<SongItem>, fallback: List<SongItem>): List<SongItem> {
        val have = songs.map { it.id }.toSet()
        // Other versions of songs already in it ("No Air (Official Video)") don't count as new.
        fun base(s: SongItem) = s.title.substringBefore(" (").substringBefore(" [").substringBefore(" - ").trim().lowercase()
        val haveTitles = songs.map(::base).toSet()
        val lists = ArrayList<List<SongItem>>()
        // The name only says something before there are songs to go on.
        if (songs.isEmpty() && title.isNotBlank()) {
            searchSongs(title).getOrNull()?.let { lists += it.take(10) }
        }
        val seeds = songs.takeLast(3).reversed().ifEmpty { fallback.take(2) }
        seeds.forEach { seed -> lists += radio(seed.id).filterNot { it.id == seed.id }.take(15) }
        // Interleave, so every seed is represented near the top.
        val out = LinkedHashMap<String, SongItem>()
        var i = 0
        while (out.size < 20 && lists.any { i < it.size }) {
            lists.forEach { l ->
                l.getOrNull(i)?.let {
                    val t = base(it)
                    if (it.id !in have && t !in haveTitles && out.values.none { o -> base(o) == t }) out.putIfAbsent(it.id, it)
                }
            }
            i++
        }
        return out.values.take(20)
    }

    /** Your YouTube Music history (every device), newest first. */
    suspend fun history(): Result<List<SongItem>> = io("history") { InnerTube.history() }

    /** Adds a play to your YouTube Music history. */
    suspend fun reportPlayback(videoId: String): Boolean = io("reportPlayback") { InnerTube.reportPlayback(videoId) }.getOrDefault(false)

    suspend fun radio(videoId: String): List<SongItem> = io("radio($videoId)") { InnerTube.radio(videoId) }.getOrDefault(emptyList())

    /** Synced lyrics from LRCLIB when possible, else YouTube Music's plain lyrics. */
    suspend fun lyrics(song: SongItem, durationMs: Long): Lyrics? {
        lyricsCache[song.id]?.let { return it }
        // Fetched before: straight from the phone (works offline too).
        withContext(Dispatchers.IO) { LyricsStore.read(song.id) }?.let {
            lyricsCache[song.id] = it
            return it
        }
        if (song.id in noLyrics) return null
        val durationSec = (durationMs / 1000).toInt().takeIf { it > 0 }
            ?: song.durationText?.let(::parseDuration)
        val lrcResult = io("lrclib") { LrcLib.lyrics(song.title, song.artists, durationSec) }
        val lrclib = lrcResult.getOrNull()
        var failed = lrcResult.isFailure
        val result = if (lrclib?.synced != null) {
            lrclib
        } else {
            val yt = io("ytLyrics") { InnerTube.lyrics(song.id) }
            failed = failed || yt.isFailure
            yt.getOrNull() ?: lrclib
        }
        // Only remember "no lyrics" when both sources actually answered: a network failure
        // (e.g. offline) must not stick, or lyrics never show once you're back online.
        if (result != null) {
            lyricsCache[song.id] = result
            withContext(Dispatchers.IO) { LyricsStore.write(song, result) }
        } else if (!failed) noLyrics += song.id
        return result
    }

    /** Builds shelves by running several seed searches in parallel. */
    suspend fun seedShelves(seeds: List<Pair<String, String>>): List<FeedShelf> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                seeds.map { (title, query) ->
                    async {
                        val songs = runCatching { InnerTube.searchSongs(query) }.getOrDefault(emptyList())
                        FeedShelf(title, songs = songs.take(15))
                    }
                }.awaitAll().filter { it.songs.isNotEmpty() }
            }
        }

    private fun parseDuration(text: String): Int? =
        text.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
            ?.fold(0) { acc, v -> acc * 60 + v }

    companion object {
        const val TAG = "LiquidTube"

        /** Set by the app: called when a request fails for network reasons. */
        @Volatile var onNetworkError: (() -> Unit)? = null
    }
}
