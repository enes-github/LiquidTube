package com.enes.liquidtube.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enes.liquidtube.R
import com.enes.liquidtube.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** What the launch screen waits for. Each part of the app ticks its box when ready. */
object Startup {
    /** True once the launch screen has drawn: the system splash may hand over to it. */
    @Volatile var overlayDrawn = false

    private val _player = MutableStateFlow(false)
    private val _home = MutableStateFlow(false)
    val player: StateFlow<Boolean> = _player.asStateFlow()
    val home: StateFlow<Boolean> = _home.asStateFlow()

    fun playerReady() { _player.value = true }
    fun homeReady() { _home.value = true }
}

/**
 * The launch screen: the app icon on black, with a glowing line tracing its outline as
 * the app connects and loads Home. When ready, the icon zooms toward you and the app
 * fades in behind it. Never waits more than a few seconds; a tap skips it.
 */
@Composable
fun LaunchOverlay(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Connecting…") }
    val shown = remember { Animatable(1f) }      // icon visible from the first frame (continues the system splash)
    val exit = remember { Animatable(0f) }       // zoom + fade out
    val trace = remember { Animatable(0f) }      // drawn outline (follows progress)
    var skip by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shown.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) { Startup.overlayDrawn = true }
    LaunchedEffect(Unit) {
        // Steps: player service (35%), Home feed (90%), done (100%).
        // Home usually comes from the saved feed in a moment; never hold the app longer than this.
        withTimeoutOrNull(3_500) {
            while (!Startup.player.value && !skip) delay(40)
            progress = 0.35f
            status = "Loading your music…"
            while (!Startup.home.value && !skip) delay(40)
            progress = 0.9f
            status = ""
        }
        progress = 1f
        status = ""
        trace.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        exit.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        onFinished()
    }
    // Between steps the line keeps creeping forward a little, so it never looks frozen.
    LaunchedEffect(progress) {
        if (progress >= 1f) return@LaunchedEffect
        trace.animateTo(progress, tween(500, easing = FastOutSlowInEasing))
        while (trace.value < progress + 0.1f) {
            trace.animateTo(trace.value + 0.015f, tween(400, easing = LinearEasing))
        }
    }

    val glow = rememberInfiniteTransition(label = "glow")
    val pulse by glow.animateFloat(
        0.92f, 1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { skip = true } },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer {
                        val pop = 0.85f + 0.15f * shown.value
                        val zoom = 1f + exit.value * 2.4f
                        val breathe = if (progress < 1f) pulse else 1f
                        scaleX = pop * zoom * (0.97f + 0.03f * breathe)
                        scaleY = pop * zoom * (0.97f + 0.03f * breathe)
                        alpha = shown.value
                    }
                    .drawBehind {
                        // The outline being traced around the icon = loading progress.
                        val inset = -9.dp.toPx()
                        val r = 34.dp.toPx()
                        val path = Path().apply {
                            addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r)))
                        }
                        val length = PathMeasure().apply { setPath(path, false) }.length.coerceAtLeast(1f)
                        val done = length * trace.value.coerceIn(0f, 1f)
                        // Reveal the outline with a dash as long as the progress: robust on every device.
                        val reveal = PathEffect.dashPathEffect(floatArrayOf(done.coerceAtLeast(0.01f), length * 2), 0f)
                        val w = 3.dp.toPx()
                        // Faint track, then the bright trace with a soft glow under it.
                        drawPath(path, Color.White.copy(alpha = 0.08f), style = Stroke(w))
                        if (done > 1f) {
                            drawPath(path, Color(0xFFFF4D67).copy(alpha = 0.35f * pulse), style = Stroke(w * 4, cap = StrokeCap.Round, pathEffect = reveal))
                            drawPath(
                                path,
                                Brush.linearGradient(listOf(Color(0xFFFF8FA0), Color(0xFFFA2D48)), Offset.Zero, Offset(size.width, size.height)),
                                style = Stroke(w, cap = StrokeCap.Round, pathEffect = reveal),
                            )
                        }
                    },
            ) {
                // The app icon itself (adaptive layers, clipped like a launcher would).
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp))) {
                    Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                }
            }
            Spacer(Modifier.height(34.dp))
            Text(
                status,
                style = AppTheme.type.subhead.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.55f * (1f - exit.value)),
            )
        }
    }
}
