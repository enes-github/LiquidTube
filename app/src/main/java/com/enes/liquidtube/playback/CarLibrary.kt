package com.enes.liquidtube.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.enes.liquidtube.data.LibraryStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.download.DownloadUtil
import com.enes.liquidtube.download.PlaybackCache
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.SongItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The browsable library Android Auto (and other media browsers) shows:
 *
 *   Home        – your YouTube Music mixes and recommendations
 *   Library     – Favorites, Liked Songs, Playlists, Downloaded
 *   Offline Mix – downloads + listening cache (works without signal)
 *   Recently Played
 *
 * Tapping a song plays the whole list it's in, starting there. Voice search ("play X on
 * LiquidTube") plays the top result as a station.
 */
@OptIn(UnstableApi::class)
class CarLibrary(
    private val repository: MusicRepository,
    private val library: LibraryStore,
    private val downloads: DownloadUtil,
    private val playCache: PlaybackCache,
    private val scope: CoroutineScope,
) {
    /** Songs we've listed, and the list each came from (to queue the rest after it). */
    private val songs = ConcurrentHashMap<String, SongItem>()
    private val lists = ConcurrentHashMap<String, List<SongItem>>()
    private val searchResults = ConcurrentHashMap<String, List<SongItem>>()

    fun root(params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
        done(LibraryResult.ofItem(folder(ROOT, "LiquidTube"), params))

    fun item(id: String): ListenableFuture<LibraryResult<MediaItem>> {
        val song = songs[id] ?: return done(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE))
        return done(LibraryResult.ofItem(playable(song), null))
    }

    fun children(parent: String, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        scope.launch {
            val items = runCatching { load(parent) }.getOrDefault(emptyList())
            future.set(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }
        return future
    }

    private suspend fun load(parent: String): List<MediaItem> = when (parent) {
        ROOT -> listOf(
            folder(HOME, "Home"),
            folder(LIBRARY, "Library"),
            folder(OFFLINE, "Offline Mix"),
            folder(RECENTS, "Recently Played"),
        )
        LIBRARY -> listOf(
            folder(FAVORITES, "Favorite Songs"),
            folder("$COLLECTION:VLLM", "Liked Songs"),
            folder(PLAYLISTS, "Playlists"),
            folder(DOWNLOADS, "Downloaded"),
        )
        HOME -> {
            val feed = repository.homeFeed(null).getOrNull()?.shelves.orEmpty()
            feed.flatMap { it.collections }.distinctBy { it.browseId }.take(24).map(::collectionFolder)
        }
        PLAYLISTS -> repository.library("FEmusic_liked_playlists").getOrNull().orEmpty().map(::collectionFolder)
        FAVORITES -> songList(parent, library.favorites.value)
        RECENTS -> songList(parent, library.recents.value)
        DOWNLOADS -> songList(parent, downloads.downloads.value.values.filter { it.isDone }.map { it.song })
        OFFLINE -> songList(
            parent,
            (downloads.downloads.value.values.filter { it.isDone }.map { it.song } + playCache.cached.value).distinctBy { it.id },
        )
        else -> if (parent.startsWith("$COLLECTION:")) {
            val page = repository.browse(parent.removePrefix("$COLLECTION:")).getOrNull()
            songList(parent, page?.songs.orEmpty())
        } else emptyList()
    }

    private fun songList(parent: String, list: List<SongItem>): List<MediaItem> {
        lists[parent] = list
        list.forEach { songs[it.id] = it; parentOf[it.id] = parent }
        return list.map(::playable)
    }

    private val parentOf = ConcurrentHashMap<String, String>()

    // ------------------------------------------------------------------ search

    fun search(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String, params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
        scope.launch {
            val results = repository.searchSongs(query).getOrNull().orEmpty().take(30)
            results.forEach { songs[it.id] = it }
            searchResults[query] = results
            lists["search:$query"] = results
            results.forEach { parentOf[it.id] = "search:$query" }
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        return done(LibraryResult.ofVoid())
    }

    fun searchResult(query: String, page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val all = searchResults[query].orEmpty().map(::playable)
        val from = (page * pageSize).coerceAtMost(all.size)
        val to = (from + pageSize).coerceAtMost(all.size)
        return done(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
    }

    // --------------------------------------------------------- playing items

    /**
     * Items from a browser arrive as bare ids. Turn them into playable items; a single tapped
     * song expands into its whole list. A voice query ("play X") becomes a search.
     */
    fun resolve(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        scope.launch {
            val query = items.singleOrNull()?.requestMetadata?.searchQuery
            if (query != null && items.single().localConfiguration == null) {
                val found = repository.searchSongs(query).getOrNull().orEmpty()
                val radio = found.firstOrNull()?.let { first -> listOf(first) + repository.radio(first.id).filterNot { it.id == first.id } }.orEmpty()
                future.set(MediaSession.MediaItemsWithStartPosition(radio.map { full(it) }, 0, 0L))
                return@launch
            }
            val single = items.singleOrNull()
            val list = single?.let { parentOf[it.mediaId] }?.let { lists[it] }
            if (single != null && single.localConfiguration == null && list != null) {
                val index = list.indexOfFirst { it.id == single.mediaId }.coerceAtLeast(0)
                future.set(MediaSession.MediaItemsWithStartPosition(list.map { full(it) }, index, startPositionMs))
                return@launch
            }
            future.set(MediaSession.MediaItemsWithStartPosition(items.map(::complete), startIndex, startPositionMs))
        }
        return future
    }

    /** Fill in a bare item from what we've listed (our own app sends complete items). */
    fun complete(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item else songs[item.mediaId]?.let { full(it) } ?: item

    private fun full(song: SongItem): MediaItem = with(PlayerConnection) { song.toMediaItem() }

    // ---------------------------------------------------------------- builders

    private fun folder(id: String, title: String, art: String? = null) = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .setArtworkUri(art?.toUri())
                .build()
        )
        .build()

    private fun collectionFolder(c: CollectionItem) = MediaItem.Builder()
        .setMediaId("$COLLECTION:${c.browseId}")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(c.title)
                .setSubtitle(c.subtitle)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .setArtworkUri(c.thumbnailUrl?.let { Uri.parse(it) })
                .build()
        )
        .build()

    private fun playable(song: SongItem) = MediaItem.Builder()
        .setMediaId(song.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artists)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(song.thumbnailUrl?.let { Uri.parse(it) })
                .setExtras(Bundle())
                .build()
        )
        .build()

    private fun <T> done(value: T): ListenableFuture<T> = SettableFuture.create<T>().apply { set(value) }

    private companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val LIBRARY = "library"
        const val OFFLINE = "offline"
        const val RECENTS = "recents"
        const val FAVORITES = "favorites"
        const val PLAYLISTS = "playlists"
        const val DOWNLOADS = "downloads"
        const val COLLECTION = "col"
    }
}
