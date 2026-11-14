package com.enes.innertube.models

import kotlinx.serialization.Serializable

enum class CollectionType { ALBUM, PLAYLIST, ARTIST }

/** An album, playlist or artist: anything that opens a browse page. */
@Serializable
data class CollectionItem(
    val browseId: String,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: CollectionType,
)

/** One horizontal shelf of a feed (home, explore, artist page). */
data class FeedShelf(
    val title: String,
    val songs: List<SongItem> = emptyList(),
    val collections: List<CollectionItem> = emptyList(),
    /** Songs and collections in their original order (for mixed shelves). */
    val items: List<Any> = songs + collections,
    /** "See all" target, when YouTube offers one. */
    val more: SectionRef? = null,
)

/** A browse target for a full section (an artist's albums, all top songs…). */
data class SectionRef(
    val browseId: String,
    val params: String? = null,
    /** Server chips can instead carry a "reload" token for the same page, filtered. */
    val reload: String? = null,
)

/** A server-side filter on a section page ("Albums" / "Singles & EPs"). */
data class SectionChip(val title: String, val ref: SectionRef, val selected: Boolean)

/** One page of a full section: songs and/or collections, server chips, next-page token. */
data class SectionPage(
    val title: String?,
    val items: List<Any>,
    val chips: List<SectionChip>,
    val continuation: String?,
)

/** An album / playlist / artist page. */
data class BrowsePage(
    val browseId: String,
    val type: CollectionType,
    val title: String,
    val subtitle: String? = null,
    val strapline: String? = null,      // album → artist name
    val straplineId: String? = null,    // album → artist browseId
    val meta: String? = null,           // "14 songs • 56 minutes"
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val songs: List<SongItem> = emptyList(),
    val shelves: List<FeedShelf> = emptyList(),
    val continuation: String? = null,
    /** Playlist id (albums: their OLAK5uy_ audio playlist) for library/save actions. */
    val playlistId: String? = null,
    /** True for the signed-in user's own playlists. */
    val editable: Boolean = false,
    /** Artists: the YouTube channel to subscribe to, and whether the user already is. */
    val channelId: String? = null,
    val subscribed: Boolean = false,
    /** Albums/playlists: already saved to the signed-in user's library. */
    val saved: Boolean = false,
    /** Artists: "223M monthly audience". */
    val audience: String? = null,
    /** Artists: the full Top Songs list. */
    val songsMore: SectionRef? = null,
)

/** A playlist the user can add songs to. */
data class PlaylistOption(
    val playlistId: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val containsSongs: Boolean = false,
)

/** YouTube Music's search filters (the chips above results). */
enum class SearchFilter(val label: String, val params: String?) {
    All("Top Results", null),
    Songs("Songs", "EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    Albums("Albums", "EgWKAQIYAWoMEA4QChADEAQQCRAF"),
    Artists("Artists", "EgWKAQIgAWoMEA4QChADEAQQCRAF"),
    Playlists("Playlists", "EgeKAQQoADgBagwQDhAKEAMQBBAJEAU%3D"),
    Community("Community Playlists", "EgeKAQQoAEABagwQDhAKEAMQBBAJEAU%3D"),
    Videos("Videos", "EgWKAQIQAWoMEA4QChADEAQQCRAF"),
}

/** One page of filtered results: songs and/or collections in order, plus the next page token. */
data class SearchPage(val items: List<Any>, val continuation: String?)

/** A mood/genre chip on Home ("Relax", "Workout"…); [params] re-browses Home filtered by it. */
data class HomeChip(val title: String, val params: String)

data class HomeFeed(val shelves: List<FeedShelf>, val chips: List<HomeChip>)

data class SearchResults(
    val topSong: SongItem? = null,
    val topCollection: CollectionItem? = null,
    val songs: List<SongItem> = emptyList(),
    val artists: List<CollectionItem> = emptyList(),
    val albums: List<CollectionItem> = emptyList(),
    val playlists: List<CollectionItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = topSong == null && topCollection == null && songs.isEmpty() &&
            artists.isEmpty() && albums.isEmpty() && playlists.isEmpty()
}

@Serializable
data class AccountInfo(
    val name: String,
    val handle: String? = null,
    val photoUrl: String? = null,
)

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    /** Time-synced lines, when available. */
    val synced: List<LyricLine>? = null,
    val plain: String? = null,
    val source: String,
)
