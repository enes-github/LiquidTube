package com.enes.liquidtube.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.tr
import kotlinx.coroutines.launch

/**
 * First-launch walkthrough: what the app does, and short looping demonstrations of the
 * gestures people miss (swipe the mini-player, drag to reorder the queue). Skippable.
 * Shown once per install; [onDone] records that and dismisses it.
 */
@Composable
fun OnboardingFlow(onDone: () -> Unit) {
    val pages = 4
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    val c = AppTheme.colors
    val last = pager.currentPage == pages - 1

    Box(Modifier.fillMaxSize().background(c.background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> WelcomeArt()
                        1 -> MiniPlayerSwipeDemo()
                        2 -> QueueDragDemo()
                        else -> HighlightsArt()
                    }
                }
                val (title, body) = when (page) {
                    0 -> tr("Welcome to LiquidTube", "LiquidTube'a Hoş Geldiniz") to
                        tr("Your YouTube Music, with the look and feel of Apple Music. Sign in from your profile for your library and the best audio quality.",
                           "YouTube Music'iniz, Apple Music'in görünümü ve hissiyle. Kitaplığınız ve en iyi ses kalitesi için profilinizden giriş yapın.")
                    1 -> tr("Slide the bottom pill", "Alttaki çubuğu kaydırın") to
                        tr("Slide the bar at the bottom left or right to skip songs. Tap it to open the full player.",
                           "Alttaki çubuğu sola veya sağa kaydırarak şarkı geçin. Dokunarak tam oynatıcıyı açın.")
                    2 -> tr("Reorder your queue", "Sıranızı düzenleyin") to
                        tr("In the player, tap ⋯ or press and hold a song to grab it, then drag to move it up or down.",
                           "Oynatıcıda ⋯ simgesine dokunun veya bir şarkıyı basılı tutun, sonra sürükleyerek yukarı/aşağı taşıyın.")
                    else -> tr("Everything you get", "Her şey elinizin altında") to
                        tr("Lyrics with translation, lyrics on your lock screen, smart playlists, offline downloads, casting and your yearly Replay.",
                           "Çevirili sözler, kilit ekranında sözler, akıllı çalma listeleri, çevrimdışı indirmeler, yayın ve yıllık Replay'iniz.")
                }
                Text(
                    title, color = c.label, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    body, color = c.secondaryLabel, style = AppTheme.type.subhead,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().height(84.dp),
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages) { i ->
                        Box(
                            Modifier.size(if (i == pager.currentPage) 9.dp else 7.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) c.accent else c.fill),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(c.accent)
                        .bounceClick {
                            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (last) tr("Get Started", "Başla") else tr("Next", "İleri"),
                        color = Color.White, style = AppTheme.type.body, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        // Skip, for people who'd rather not sit through it.
        if (!last) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text(
                    tr("Skip", "Atla"),
                    color = c.secondaryLabel, style = AppTheme.type.body, fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                        .clip(RoundedCornerShape(10.dp)).bounceClick(onClick = onDone).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ----------------------------------------------------------------- page art

@Composable
private fun WelcomeArt() {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "welcome")
    val float by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float",
    )
    Box(
        Modifier.size(150.dp).graphicsLayer { translationY = (float - 0.5f) * 18f }
            .shadow(28.dp, RoundedCornerShape(34.dp)).clip(RoundedCornerShape(34.dp)).background(c.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(78.dp))
    }
}

@Composable
private fun HighlightsArt() {
    val c = AppTheme.colors
    val items = listOf(
        Icons.Rounded.Translate to tr("Translated lyrics", "Çevrili sözler"),
        Icons.Rounded.Lock to tr("Lock-screen lyrics", "Kilit ekranı sözleri"),
        Icons.Rounded.AutoAwesome to tr("Smart playlists", "Akıllı listeler"),
        Icons.Rounded.Download to tr("Offline downloads", "Çevrimdışı indir"),
        Icons.Rounded.Cast to tr("Cast to devices", "Cihazlara yayın"),
        Icons.Rounded.GraphicEq to tr("Yearly Replay", "Yıllık Replay"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (icon, label) ->
                    Column(
                        Modifier.width(140.dp).height(90.dp).clip(RoundedCornerShape(20.dp)).background(c.elevated)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(icon, null, tint = c.accent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(7.dp))
                        Text(label, color = c.label, style = AppTheme.type.footnote, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------- gesture demos

/** A translucent fingertip, for the gesture demos. */
@Composable
private fun Fingertip(modifier: Modifier = Modifier, visible: Float = 1f) {
    Box(
        modifier.size(46.dp).alpha(visible).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
    }
}

/** Looping demo: a mini-player bar being swiped sideways to skip to the next song. */
@Composable
private fun MiniPlayerSwipeDemo() {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "swipe")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart), label = "p")

    // Press-and-hold (like the queue demo), then slide. 0.10–0.26 press, 0.26–0.56 slide.
    val finger = if (p in 0.06f..0.62f) 1f else 0f
    val press = when {
        p < 0.10f -> 0f
        p < 0.26f -> (p - 0.10f) / 0.16f          // finger pushes down, pill grabs
        p < 0.56f -> 1f                            // held through the slide
        p < 0.62f -> 1f - (p - 0.56f) / 0.06f      // release
        else -> 0f
    }.coerceIn(0f, 1f)
    val slide = FastOutSlowInEasing.transform(((p - 0.26f) / 0.30f).coerceIn(0f, 1f))
    val contentShift = -slide * 230f                 // the held card slides left, off-screen
    val secondTrack = p >= 0.56f                      // after the slide, the "next" song is shown

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(260.dp).height(96.dp), contentAlignment = Alignment.Center) {
            // The mini-player — lifts a little while it's held, then follows the finger.
            Row(
                Modifier.fillMaxWidth().height(64.dp)
                    .graphicsLayer {
                        translationX = if (secondTrack) 0f else contentShift
                        val s = 1f + 0.04f * press
                        scaleX = s; scaleY = s
                    }
                    .shadow((12 * press).dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)).background(c.elevated).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.85f)))
                Spacer(Modifier.width(12.dp))
                Column {
                    val title = if (secondTrack) tr("Next song", "Sonraki şarkı") else tr("Now playing", "Şimdi çalıyor")
                    AnimatedContent(title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "t") {
                        Text(it, color = c.label, style = AppTheme.type.subhead, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.padding(top = 6.dp).width(90.dp).height(6.dp).clip(CircleShape).background(c.fill))
                }
            }
            // The fingertip shrinks as it presses (held), then rides the pill as it slides.
            Fingertip(
                Modifier.align(Alignment.Center).graphicsLayer {
                    translationX = 60f + contentShift * 0.9f
                    val s = 1f - 0.14f * press
                    scaleX = s; scaleY = s
                },
                visible = finger,
            )
        }
    }
}

/** Looping demo: pressing and holding a queue row, then dragging it down one place. */
@Composable
private fun QueueDragDemo() {
    val c = AppTheme.colors
    val t = rememberInfiniteTransition(label = "drag")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "p")

    val rowH = 56f
    val held = p in 0.18f..0.78f                       // finger is pressing
    val drag = ((p - 0.28f) / 0.42f).coerceIn(0f, 1f)  // moves down one slot
    val eased = FastOutSlowInEasing.transform(drag)
    val draggedY = eased * rowH
    val otherY = -eased * rowH
    val fingerVisible = if (p in 0.12f..0.84f) 1f else 0f
    val lift = if (held) 1f else 0f

    val labels = listOf(
        tr("Song A", "Şarkı A"), tr("Song B", "Şarkı B"), tr("Song C", "Şarkı C"),
    )
    Box(Modifier.width(260.dp).height((rowH * 3 + 16).dp)) {
        // Row B (shifts up as A passes it)
        QueueRow(labels[1], c, Modifier.graphicsLayer { translationY = (rowH + otherY).dp.toPx() })
        // Row C (stays)
        QueueRow(labels[2], c, Modifier.graphicsLayer { translationY = (rowH * 2).dp.toPx() })
        // Row A (the dragged one, drawn last so it floats on top)
        QueueRow(
            labels[0], c,
            Modifier.graphicsLayer {
                translationY = draggedY.dp.toPx()
                val s = 1f + 0.04f * lift
                scaleX = s; scaleY = s
            }.shadow((10 * lift).dp, RoundedCornerShape(12.dp)),
            raised = lift > 0f,
        )
        Fingertip(
            Modifier.align(Alignment.TopEnd).graphicsLayer {
                translationX = (-34).dp.toPx()
                translationY = (14 + draggedY).dp.toPx()
            },
            visible = fingerVisible,
        )
    }
}

@Composable
private fun QueueRow(label: String, c: com.enes.liquidtube.ui.theme.AppColors, modifier: Modifier = Modifier, raised: Boolean = false) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
            .background(if (raised) c.elevated else c.fill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(c.accent.copy(alpha = 0.7f)))
        Spacer(Modifier.width(12.dp))
        Text(label, color = c.label, style = AppTheme.type.subhead, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.width(16.dp).height(2.dp).clip(CircleShape).background(c.secondaryLabel)) }
        }
    }
}
