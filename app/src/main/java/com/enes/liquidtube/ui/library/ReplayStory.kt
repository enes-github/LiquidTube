package com.enes.liquidtube.ui.library

import com.enes.liquidtube.ui.theme.tr
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.enes.liquidtube.R
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.BlurredImage
import com.enes.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything a month's story shows. */
internal data class ReplayStats(
    val monthTitle: String,
    val minutes: Long,
    val songs: List<Pair<SongItem, Long>>,      // most listened first
    val artists: List<Pair<String, Long>>,      // name → ms
    val artistArt: Map<String, String?>,
    val daypart: String?,                       // when you listen most
)

private val CARD_COLORS = listOf(
    listOf(Color(0xFFFA2D48), Color(0xFF7B1FA2)),
    listOf(Color(0xFFFF9F0A), Color(0xFFD7263D)),
    listOf(Color(0xFF0A84FF), Color(0xFF5E2CA5)),
    listOf(Color(0xFF30D158), Color(0xFF0B6E4F)),
    listOf(Color(0xFFBF5AF2), Color(0xFF3A1C71)),
    listOf(Color(0xFFFF375F), Color(0xFFFF9F0A)),
)

private const val CARD_MS = 6_000

/**
 * Replay as a story, like Instagram's: full screen cards that move on by themselves
 * (tap right for the next, left to go back), ending with a picture to share.
 */
@Composable
internal fun ReplayStory(stats: ReplayStats, onClose: () -> Unit) {
    val cards = buildList<@Composable () -> Unit> {
        add { IntroCard(stats) }
        add { MinutesCard(stats) }
        stats.artists.firstOrNull()?.let { (name, ms) -> add { TopArtistCard(name, ms, stats.artistArt[name]) } }
        if (stats.songs.isNotEmpty()) add { TopSongsCard(stats) }
        if (stats.artists.size > 1) add { TopArtistsCard(stats) }
        add { HabitsCard(stats) }
        add { OutroCard(stats) }
    }
    var index by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(CARD_MS, easing = LinearEasing))
        if (index < cards.lastIndex) index++ // the last card stays
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(CARD_COLORS[index % CARD_COLORS.size]))
                .pointerInput(cards.size) {
                    detectTapGestures { pos ->
                        if (pos.x < size.width * 0.3f) index = (index - 1).coerceAtLeast(0)
                        else if (index < cards.lastIndex) index++
                    }
                },
        ) {
            // The month's top cover, blurred, behind every card.
            BlurredImage(stats.songs.firstOrNull()?.first?.thumbnailUrl, Modifier.fillMaxSize()) { alpha = 0.35f; scaleX = 1.4f; scaleY = 1.4f }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.1f), Color.Black.copy(alpha = 0.45f)))))

            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 70.dp)) {
                androidx.compose.animation.AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(tween(420)) + androidx.compose.animation.scaleIn(tween(420), initialScale = 0.94f)) togetherWith
                            androidx.compose.animation.fadeOut(tween(220))
                    },
                    label = "story",
                ) { i -> cards[i]() }
            }

            // Progress bars + close / share.
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cards.indices.forEach { i ->
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replay · ${stats.monthTitle}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StoryButton(Icons.Rounded.Share, tr("Share", "Paylaş")) { scope.launch { shareStory(context, stats) } }
                    Spacer(Modifier.width(8.dp))
                    StoryButton(Icons.Rounded.Close, tr("Close", "Kapat"), onClose)
                }
            }
        }
    }
}

@Composable
private fun StoryButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

/** Grows into place as a card appears. */
@Composable
private fun Modifier.rise(delayMs: Int): Modifier {
    var shown by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(delayMs.toLong()); shown = true }
    val t by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 220f), label = "rise")
    return graphicsLayer { alpha = t; translationY = (1f - t) * 60f }
}

private val Big = 46.sp
private val Huge = 84.sp

@Composable
private fun IntroCard(s: ReplayStats) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        val covers = s.songs.map { it.first.thumbnailUrl }.distinct().take(4)
        if (covers.size >= 4) {
            Column(Modifier.size(220.dp).rise(0).clip(RoundedCornerShape(22.dp))) {
                covers.chunked(2).forEach { row ->
                    Row(Modifier.weight(1f)) { row.forEach { Artwork(it, Modifier.weight(1f).fillMaxHeight(), corner = 0.dp) } }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
        Text(tr("Your ${s.monthTitle}", "${s.monthTitle} Özetiniz"), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.rise(150))
        Text(tr("Replay", "Replay"), color = Color.White, fontSize = Huge, fontWeight = FontWeight.Black, lineHeight = 92.sp, maxLines = 1, softWrap = false, modifier = Modifier.rise(250))
        Text(tr("Here’s how your month sounded.", "İşte ayınızın sesi."), color = Color.White.copy(alpha = 0.8f), fontSize = 20.sp, modifier = Modifier.rise(450))
    }
}

@Composable
private fun MinutesCard(s: ReplayStats) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(tr("You listened for", "Dinlediğiniz süre"), color = Color.White.copy(alpha = 0.85f), fontSize = 24.sp, modifier = Modifier.rise(0))
        Text("%,d".format(s.minutes), color = Color.White, fontSize = 110.sp, fontWeight = FontWeight.Black, lineHeight = 112.sp, modifier = Modifier.rise(200))
        Text("minutes", color = Color.White, fontSize = Big, lineHeight = 52.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rise(350))
        Spacer(Modifier.height(24.dp))
        val hours = s.minutes / 60
        Text(
            if (hours >= 1) "That’s about $hours hour${if (hours == 1L) "" else "s"} of music, across ${s.songs.size} songs."
            else tr("Across ${s.songs.size} songs. Just getting started.", "${s.songs.size} şarkı boyunca. Daha yeni başlıyor."),
            color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, modifier = Modifier.rise(550),
        )
    }
}

@Composable
private fun TopArtistCard(name: String, ms: Long, art: String?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("Your top artist", "En çok dinlediğiniz sanatçı"), color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp, modifier = Modifier.rise(0))
        Spacer(Modifier.height(24.dp))
        Artwork(art, Modifier.size(230.dp).rise(150), corner = 115.dp)
        Spacer(Modifier.height(24.dp))
        Text(name, color = Color.White, fontSize = Big, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, lineHeight = 50.sp, modifier = Modifier.rise(300))
        Text(tr("${ms / 60_000} minutes together", "Birlikte ${ms / 60_000} dakika"), color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, modifier = Modifier.rise(450))
    }
}

@Composable
private fun TopSongsCard(s: ReplayStats) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(tr("Your top songs", "En çok dinlediğiniz şarkılar"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.rise(0))
        Spacer(Modifier.height(22.dp))
        s.songs.take(5).forEachIndexed { i, (song, ms) ->
            Row(Modifier.padding(vertical = 7.dp).rise(120 + i * 110), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                Artwork(song.thumbnailUrl, Modifier.size(60.dp), corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${song.artists} · ${ms / 60_000} " + tr("min", "dk"), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TopArtistsCard(s: ReplayStats) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(tr("Your top artists", "En çok dinlediğiniz sanatçılar"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, modifier = Modifier.rise(0))
        Spacer(Modifier.height(22.dp))
        s.artists.take(5).forEachIndexed { i, (name, ms) ->
            Row(Modifier.padding(vertical = 7.dp).rise(120 + i * 110), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(34.dp))
                Artwork(s.artistArt[name], Modifier.size(60.dp), corner = 30.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${ms / 60_000} " + tr("minutes", "dakika"), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun HabitsCard(s: ReplayStats) {
    val (emoji, phrase) = when (s.daypart) {
        "morning" -> "☀️" to "a morning person"
        "afternoon" -> "🌤️" to "an afternoon listener"
        "evening" -> "🌆" to "an evening listener"
        "night" -> "🌙" to "a night owl"
        else -> "🎧" to tr("always listening", "hep müzik dinliyorsunuz")
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 80.sp, modifier = Modifier.rise(0))
        Spacer(Modifier.height(16.dp))
        Text(tr("You’re", "Siz"), color = Color.White.copy(alpha = 0.85f), fontSize = 24.sp, modifier = Modifier.rise(150))
        Text(phrase, color = Color.White, fontSize = Big, fontWeight = FontWeight.Black, lineHeight = 50.sp, modifier = Modifier.rise(300))
        Spacer(Modifier.height(24.dp))
        Text(
            tr("${s.songs.size} different songs from ${s.artists.size} artists this month.", "Bu ay ${s.artists.size} sanatçıdan ${s.songs.size} farklı şarkı."),
            color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, modifier = Modifier.rise(450),
        )
    }
}

@Composable
private fun OutroCard(s: ReplayStats) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("That’s your", "İşte sizin"), color = Color.White.copy(alpha = 0.85f), fontSize = 24.sp, modifier = Modifier.rise(0))
        Text(s.monthTitle, color = Color.White, fontSize = Big, lineHeight = 52.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.rise(150))
        Spacer(Modifier.height(40.dp))
        Row(
            Modifier
                .rise(300)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { scope.launch { shareStory(context, s) } }
                .padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Share, null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("Share Your Replay", "Replay'inizi Paylaşın"), color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- sharing

/** A story-sized picture (1080×1920) of the month, for Instagram and the like. */
private suspend fun shareStory(context: Context, s: ReplayStats) {
    val file = withContext(Dispatchers.Default) {
        val bmp = renderStory(context, s)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        File(dir, "replay.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, tr("My ${s.monthTitle} on LiquidTube: ${"%,d".format(s.minutes)} minutes", "LiquidTube'da ${s.monthTitle}: ${"%,d".format(s.minutes)} dakika"))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, tr("Share Replay", "Replay'i Paylaş")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private suspend fun loadBitmap(context: Context, url: String?, size: Int): Bitmap? {
    url ?: return null
    val request = ImageRequest.Builder(context).data(url).size(size).allowHardware(false).build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}

private suspend fun renderStory(context: Context, s: ReplayStats): Bitmap {
    val w = 1080
    val h = 1920
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawPaint(Paint().apply {
        shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFFFA2D48.toInt(), 0xFF5E2CA5.toInt(), Shader.TileMode.CLAMP)
    })
    val inter = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.DEFAULT
    fun paint(size: Float, weight: Int, alpha: Int = 255) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = inter
        textSize = size
        color = android.graphics.Color.argb(alpha, 255, 255, 255)
        fontVariationSettings = "'wght' $weight"
    }
    fun ellipsize(t: String, p: TextPaint, width: Float) =
        android.text.TextUtils.ellipsize(t, p, width, android.text.TextUtils.TruncateAt.END).toString()
    fun rounded(bmp: Bitmap?, x: Float, y: Float, size: Float, r: Float) {
        bmp ?: return
        val dst = RectF(x, y, x + size, y + size)
        c.save(); c.clipPath(Path().apply { addRoundRect(dst, r, r, Path.Direction.CW) })
        c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG)); c.restore()
    }

    val x = 90f
    var y = 230f
    c.drawText(tr("My ${s.monthTitle}", "${s.monthTitle}"), x, y, paint(56f, 600, 220)); y += 130f
    c.drawText(tr("Replay", "Replay"), x, y, paint(140f, 900)); y += 150f
    c.drawText(tr("%,d minutes", "%,d dakika").format(s.minutes), x, y, paint(84f, 800)); y += 70f
    s.artists.firstOrNull()?.let { (name, _) ->
        c.drawText(ellipsize(tr("Top artist: $name", "En çok dinlenen: $name"), paint(46f, 600, 230), w - 2 * x), x, y, paint(46f, 600, 230))
    }
    y += 110f
    c.drawText(tr("Top songs", "En çok dinlenen şarkılar"), x, y, paint(52f, 800)); y += 40f
    s.songs.take(5).forEachIndexed { i, (song, _) ->
        val art = loadBitmap(context, song.thumbnailUrl, 200)
        c.drawText("${i + 1}", x, y + 110f, paint(60f, 900))
        rounded(art, x + 80f, y + 30f, 130f, 18f)
        val tx = x + 240f
        val tw = w - tx - x
        c.drawText(ellipsize(song.title, paint(44f, 700), tw), tx, y + 90f, paint(44f, 700))
        c.drawText(ellipsize(song.artists, paint(34f, 500, 200), tw), tx, y + 140f, paint(34f, 500, 200))
        y += 170f
    }
    c.drawText("♪  LiquidTube", x, h - 110f, paint(40f, 700, 200))
    return out
}
