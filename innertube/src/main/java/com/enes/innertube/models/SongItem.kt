package com.enes.innertube.models

import kotlinx.serialization.Serializable

/**
 * A clean, UI-facing model. The parser flattens YouTube's nested renderers into
 * this; the Compose layer only ever sees this type. Serializable so the app can
 * persist recents / favorites.
 */
@Serializable
data class SongItem(
    val id: String,              // videoId — used for playback
    val title: String,
    val artists: String,
    val album: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
    /** Position id inside a playlist; needed to remove it from that playlist. */
    val setVideoId: String? = null,
)
