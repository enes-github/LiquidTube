package com.enes.liquidtube.playback.cast

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.enes.liquidtube.playback.PlaybackService
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Tells the Cast library to use Google's standard media player on the speaker/TV. */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setStopReceiverApplicationWhenEndingSession(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}

/**
 * Cast (beta): what the UI needs to know and do. Discovery, the device list, which device
 * is playing, its volume. The playback service does the actual hand-over of the queue.
 */
object Cast {
    data class Device(val id: String, val name: String, val description: String?)

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    /** Cast speakers/TVs found on this network. */
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _castingTo = MutableStateFlow<String?>(null)
    /** Name of the device playing right now, or null when playing on the phone. */
    val castingTo: StateFlow<String?> = _castingTo.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    private var context: CastContext? = null
    private var router: MediaRouter? = null
    private var activeScan = false

    private val selector: MediaRouteSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
    }

    /** Null on phones without Google Play services (Cast can't work there). */
    fun castContext(app: Context): CastContext? {
        context?.let { return it }
        val ok = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) == ConnectionResult.SUCCESS
        if (!ok) return null
        return runCatching { CastContext.getSharedInstance(app) }
            .onFailure { Log.w(TAG, "Cast unavailable: ${it.message}") }
            .getOrNull()
            ?.also { context = it }
    }

    /** Start looking for devices quietly (the Output button only offers Cast when some exist). */
    fun startDiscovery(app: Context) {
        castContext(app) ?: return
        val r = router ?: MediaRouter.getInstance(app).also { router = it }
        r.addCallback(selector, callback, 0)
        refresh()
    }

    /** Look harder while the device picker is open. */
    fun scan(app: Context, on: Boolean) {
        val r = router ?: return
        if (on == activeScan) return
        activeScan = on
        r.addCallback(selector, callback, if (on) MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN else 0)
    }

    fun select(app: Context, device: Device) {
        val r = router ?: return
        val route = r.routes.firstOrNull { it.id == device.id } ?: return
        _connecting.value = true
        r.selectRoute(route)
    }

    /** Back to the phone; the song carries on here from the same spot. */
    fun stop(app: Context) {
        castContext(app)?.sessionManager?.endCurrentSession(true)
    }

    internal fun sessionStarted(name: String?) {
        _connecting.value = false
        _castingTo.value = name ?: "Cast"
    }

    internal fun sessionEnded() {
        _connecting.value = false
        _castingTo.value = null
    }

    /** The speaker's volume, 0–1, or null when not casting. */
    fun volume(): Float? = context?.sessionManager?.currentCastSession?.takeIf { it.isConnected }?.volume?.toFloat()

    fun setVolume(value: Float) {
        runCatching { context?.sessionManager?.currentCastSession?.volume = value.toDouble().coerceIn(0.0, 1.0) }
    }

    private val callback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) {
            _connecting.value = false
            refresh()
        }
    }

    private fun refresh() {
        val r = router ?: return
        _devices.value = r.routes
            .filter { !it.isDefaultOrBluetooth && it.isEnabled && it.matchesSelector(selector) }
            .map { Device(it.id, it.name, it.description) }
    }

    private const val TAG = "LiquidTube"
}

/**
 * Turns queue items into what the speaker needs: a link to the phone's [CastServer]
 * instead of YouTube, plus the title, artist and cover; and back again, keeping
 * everything the app stores with a song.
 */
@OptIn(UnstableApi::class)
class CastItemConverter(private val server: CastServer) : MediaItemConverter {
    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val m = mediaItem.mediaMetadata
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            m.title?.let { putString(MediaMetadata.KEY_TITLE, it.toString()) }
            m.artist?.let { putString(MediaMetadata.KEY_ARTIST, it.toString()) }
            m.albumTitle?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
            m.artworkUri?.let { addImage(WebImage(Uri.parse(com.enes.innertube.hiResThumbnail(it.toString(), 720)))) }
        }
        val extras = JSONObject().apply {
            put("id", mediaItem.mediaId)
            m.artworkUri?.let { put("art", it.toString()) }
            m.extras?.let { b -> b.keySet().forEach { k -> b.getString(k)?.let { put("x_$k", it) } } }
        }
        val url = server.urlFor(mediaItem.mediaId) ?: PlaybackService.placeholderUri(mediaItem.mediaId)
        val info = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mp4")
            .setMetadata(meta)
            .setCustomData(extras)
            .build()
        return MediaQueueItem.Builder(info).setCustomData(extras).build()
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val info = mediaQueueItem.media
        val data = mediaQueueItem.customData ?: info?.customData ?: JSONObject()
        val meta = info?.metadata
        val id = data.optString("id").ifBlank { info?.contentId?.substringAfterLast('/').orEmpty() }
        val extras = Bundle().apply {
            data.keys().forEach { k -> if (k.startsWith("x_")) putString(k.removePrefix("x_"), data.optString(k)) }
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(PlaybackService.placeholderUri(id))
            .setCustomCacheKey(id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(meta?.getString(MediaMetadata.KEY_TITLE))
                    .setArtist(meta?.getString(MediaMetadata.KEY_ARTIST))
                    .setAlbumTitle(meta?.getString(MediaMetadata.KEY_ALBUM_TITLE))
                    .setArtworkUri(data.optString("art").takeIf { it.isNotBlank() }?.toUri())
                    .setExtras(extras)
                    .build(),
            )
            .build()
    }
}
