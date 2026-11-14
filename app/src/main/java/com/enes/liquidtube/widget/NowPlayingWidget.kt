package com.enes.liquidtube.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.enes.liquidtube.MainActivity
import com.enes.liquidtube.R
import com.enes.liquidtube.playback.PlayerConnection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home screen widget: artwork, title, artist, previous / play-pause / next and the
 * favorite star. Buttons are broadcasts back to this provider, which forwards them to
 * the player through [PlayerConnection] (it connects to the playback service itself).
 */
@AndroidEntryPoint
class NowPlayingWidget : AppWidgetProvider() {

    @Inject lateinit var connection: PlayerConnection

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        connection.initialize()
        WidgetState.push(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY -> connection.togglePlayPause()
            ACTION_NEXT -> connection.skipNext()
            ACTION_PREV -> connection.skipPrevious()
            ACTION_FAV -> connection.current.value?.let(connection::toggleFavorite)
            else -> return
        }
    }

    companion object {
        const val ACTION_PLAY = "com.enes.liquidtube.widget.PLAY"
        const val ACTION_NEXT = "com.enes.liquidtube.widget.NEXT"
        const val ACTION_PREV = "com.enes.liquidtube.widget.PREV"
        const val ACTION_FAV = "com.enes.liquidtube.widget.FAV"
    }
}

/** What the widget shows; pushed whenever the song, play state or favorite changes. */
object WidgetState {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var title: String? = null
    private var artist: String? = null
    private var artUrl: String? = null
    private var art: Bitmap? = null
    private var playing = false
    private var favorite = false

    fun update(context: Context, title: String?, artist: String?, artUrl: String?, playing: Boolean, favorite: Boolean) {
        val app = context.applicationContext
        val artChanged = artUrl != this.artUrl
        this.title = title
        this.artist = artist
        this.playing = playing
        this.favorite = favorite
        if (artChanged) {
            this.artUrl = artUrl
            art = null
            if (artUrl != null) {
                scope.launch {
                    val request = ImageRequest.Builder(app)
                        .data(com.enes.innertube.hiResThumbnail(artUrl, 226))
                        .size(226)
                        .allowHardware(false)
                        .build()
                    val result = SingletonImageLoader.get(app).execute(request)
                    if (result is SuccessResult && this@WidgetState.artUrl == artUrl) {
                        art = result.image.toBitmap()
                        push(app)
                    }
                }
            }
        }
        push(app)
    }

    fun push(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
            setTextViewText(R.id.widget_title, title ?: "Not Playing")
            setTextViewText(R.id.widget_artist, artist ?: "LiquidTube")
            val a = art
            if (a != null) setImageViewBitmap(R.id.widget_art, a) else setImageViewResource(R.id.widget_art, android.R.color.transparent)
            setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            setImageViewResource(R.id.widget_fav, if (favorite) R.drawable.ic_w_star else R.drawable.ic_w_star_outline)
            setOnClickPendingIntent(R.id.widget_play, action(context, NowPlayingWidget.ACTION_PLAY, 1))
            setOnClickPendingIntent(R.id.widget_next, action(context, NowPlayingWidget.ACTION_NEXT, 2))
            setOnClickPendingIntent(R.id.widget_prev, action(context, NowPlayingWidget.ACTION_PREV, 3))
            setOnClickPendingIntent(R.id.widget_fav, action(context, NowPlayingWidget.ACTION_FAV, 4))
            setOnClickPendingIntent(
                R.id.widget_art,
                PendingIntent.getActivity(
                    context, 5,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        manager.updateAppWidget(ids, views)
    }

    private fun action(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, NowPlayingWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
