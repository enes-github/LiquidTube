package com.enes.liquidtube.dream

import android.service.dreams.DreamService
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.playback.PlayerEntryPoint
import com.enes.liquidtube.ui.components.BlurredImage
import com.enes.innertube.models.Lyrics
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LiquidTube as the phone's screen saver (Settings → Display → Screen saver): while it
 * charges, the cover of what's playing, the song, and the line being sung, over the
 * blurred artwork. Everything drifts slowly so nothing burns into the screen; with
 * nothing playing, a quiet clock.
 */
class NowPlayingDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val connection = EntryPointAccessors.fromApplication(applicationContext, PlayerEntryPoint::class.java).playerConnection()
        connection.initialize()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@NowPlayingDream)
            setViewTreeSavedStateRegistryOwner(this@NowPlayingDream)
            setContent { DreamContent(connection) }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

@Composable
private fun DreamContent(connection: PlayerConnection) {
    val song by connection.current.collectAsStateWithLifecycle()
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    val position by connection.positionMs.collectAsStateWithLifecycle()

    // Burn-in protection: the whole picture wanders a little every minute.
    var drift by remember { mutableStateOf(0f to 0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            drift = (kotlin.random.Random.nextFloat() - 0.5f) * 60f to (kotlin.random.Random.nextFloat() - 0.5f) * 80f
        }
    }
    val now by produceState(Date()) { while (true) { value = Date(); delay(15_000) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val current = song
        if (current == null) {
            Clock(now, Modifier.align(Alignment.Center).offset(drift.first.dp, drift.second.dp))
            return@Box
        }
        BlurredImage(current.thumbnailUrl, Modifier.fillMaxSize()) { scaleX = 1.3f; scaleY = 1.3f; alpha = 0.55f }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

        val lyrics by produceState<Lyrics?>(null, current.id) { value = connection.lyrics(current, 0L) }
        val synced = lyrics?.synced
        val line = synced?.let { s ->
            val i = s.indexOfLast { it.timeMs <= position + 250 }
            s.getOrNull(i)?.text?.takeIf { it.isNotBlank() }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(48.dp).offset(drift.first.dp, drift.second.dp)) {
            val wide = maxWidth > maxHeight
            val art = (if (wide) maxHeight * 0.7f else maxWidth * 0.82f)
            val details: @Composable () -> Unit = {
                Column(horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
                    Text(current.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(current.artists, color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(28.dp))
                    // The line being sung, gliding to the next one.
                    AnimatedContent(
                        targetState = line,
                        transitionSpec = {
                            (slideInVertically(tween(600)) { it / 2 } + fadeIn(tween(600))) togetherWith
                                (slideOutVertically(tween(500)) { -it / 2 } + fadeOut(tween(400)))
                        },
                        label = "dreamLine",
                    ) { l ->
                        Text(
                            l ?: if (playing) "" else "Paused",
                            color = Color.White.copy(alpha = if (l != null) 0.95f else 0.5f),
                            fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold,
                            textAlign = if (wide) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.45f), fontSize = 20.sp)
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                    Cover(current.thumbnailUrl, art)
                    Spacer(Modifier.width(56.dp))
                    details()
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Cover(current.thumbnailUrl, art)
                    Spacer(Modifier.height(40.dp))
                    details()
                }
            }
        }
    }
}

@Composable
private fun Cover(url: String?, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.size(size).shadow(40.dp, shape).clip(shape).background(Color.DarkGray)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun Clock(now: Date, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.8f), fontSize = 96.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), color = Color.White.copy(alpha = 0.5f), fontSize = 20.sp)
    }
}
