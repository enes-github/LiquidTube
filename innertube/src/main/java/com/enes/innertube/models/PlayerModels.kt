package com.enes.innertube.models

/** Result of resolving a videoId to a playable audio stream. */
data class StreamInfo(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    /** User-agent of the client that resolved it; sent with the media request. */
    val userAgent: String,
    val clientName: String,
    /** Wall-clock time after which the URL should be re-resolved. */
    val expiresAtMs: Long,
    /** YouTube's loudness of this track relative to its reference (dB); used by Sound Check. */
    val loudnessDb: Float? = null,
)
