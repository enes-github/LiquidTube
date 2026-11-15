package com.enes.liquidtube.ui.components

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.liquidGlass
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.chromeGlass

/** Floating Liquid Glass capsule mini-player. */
@Composable
fun MiniPlayer(
    connection: PlayerConnection,
    backdrop: GlassBackdrop,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val song by connection.current.collectAsStateWithLifecycle()
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val isBuffering by connection.isBuffering.collectAsStateWithLifecycle()
    val current = song ?: return
    val c = AppTheme.colors
    val menus = LocalMenus.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val swipe = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val swipeThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.toPx() }

    Box(
        modifier
            .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.35f))
            .liquidGlass(backdrop, chromeGlass(c.isDark))
            .pressMenu(onClick = onExpand, menu = { menus?.song(current) })
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> if (dragAmount < -10f) onExpand() }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dx = swipe.value
                        scope.launch {
                            when {
                                dx < -swipeThreshold -> { swipe.animateTo(-size.width.toFloat() * 0.6f, tween(140)); connection.skipNext(); swipe.snapTo(size.width * 0.4f) }
                                dx > swipeThreshold -> { swipe.animateTo(size.width.toFloat() * 0.6f, tween(140)); connection.skipPrevious(); swipe.snapTo(-size.width * 0.4f) }
                            }
                            swipe.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + dragAmount) }
                }
            }
            .padding(start = 9.dp, end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            Modifier.graphicsLayer {
                translationX = swipe.value
                alpha = 1f - (kotlin.math.abs(swipe.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.7f)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(current.thumbnailUrl, artModifier.size(if (compact) 40.dp else 44.dp), corner = if (compact) 20.dp else 10.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        current.artists,
                        style = AppTheme.type.footnote,
                        color = c.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isBuffering && isPlaying) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.label, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            } else {
                MiniButton(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isPlaying) tr("Pause", "Duraklat") else tr("Play", "Çal"),
                    size = 34.dp,
                ) { connection.togglePlayPause() }
            }
            if (!compact) {
                MiniButton(Icons.Rounded.FastForward, tr("Next", "Sonraki"), size = 32.dp) { connection.skipNext() }
            }
        }
    }
}

@Composable
private fun MiniButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).bounceClick(pressedScale = 0.8f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = AppTheme.colors.label, modifier = Modifier.size(size))
    }
}
