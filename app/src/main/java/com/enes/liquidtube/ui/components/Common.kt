package com.enes.liquidtube.ui.components

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.glass.glassChip
import androidx.compose.animation.core.animateFloat
import com.enes.innertube.models.SongItem

/** Standard horizontal margin, the same as iOS's readable content inset. */
val Gutter = 20.dp

/** Height of the floating chrome (tab bar + mini-player) that content must clear. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** True when the screen is wider than tall: the side rail and two-column layouts take over. */
@Composable
fun isLandscape(): Boolean {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    return config.screenWidthDp > config.screenHeightDp
}

/**
 * The width card sizes are based on. On a wide screen cards keep their phone size, so more
 * of them fit across instead of each one growing huge.
 */
fun phoneWidth(width: Dp): Dp = when {
    width > 600.dp -> LandscapeCards // a phone on its side: the screen is short, keep cards compact
    width > PhoneWidth -> PhoneWidth
    else -> width
}
private val LandscapeCards = 370.dp
val PhoneWidth = 430.dp

fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Press-to-shrink feedback instead of Material ripples, like UIKit buttons. */
fun Modifier.bounceClick(pressedScale: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, corner: Dp = 8.dp, small: Boolean = false) {
    // Small thumbnails (rows, menus) ask YouTube for a 226px image instead of 544px:
    // a quarter of the bytes, so long lists fill in much faster.
    val model = if (small && url != null) remember(url) { com.enes.innertube.hiResThumbnail(url, 226) } else url
    val c = AppTheme.colors
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(c.elevated)
            .border(0.5.dp, c.separator.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote, null,
            tint = c.tertiaryLabel,
            modifier = Modifier.fillMaxSize(0.38f),
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun LargeTitle(
    text: String,
    modifier: Modifier = Modifier,
    withStatusBar: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (withStatusBar) Modifier.statusBarsPadding() else Modifier)
            .padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = AppTheme.type.largeTitle,
            color = AppTheme.colors.label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke(this)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = Gutter, end = Gutter, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.title2, color = AppTheme.colors.label)
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint = AppTheme.colors.secondaryLabel,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Vertical list row — artwork, title, artist, hairline divider. */
@Composable
fun SongRow(
    song: SongItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    showDuration: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    menuExtra: List<MenuAction> = emptyList(),
    /** Replaces the ••• button (e.g. an add button in Add Songs). */
    trailing: (@Composable () -> Unit)? = null,
    /** Not on the phone yet ("Downloading 40%", "Waiting for Wi-Fi"): the row is greyed out. */
    status: String? = null,
) {
    val c = AppTheme.colors
    val menus = LocalMenus.current
    val connection = LocalPlayerConnection.current
    val current = connection?.current?.collectAsStateWithLifecycle()?.value
    val isCurrent = current?.id == song.id
    val online = connection?.network?.online?.collectAsStateWithLifecycle()?.value ?: true
    val cachedList = connection?.playCache?.cached?.collectAsStateWithLifecycle()?.value.orEmpty()
    val downloaded = connection?.downloads?.downloads?.collectAsStateWithLifecycle()?.value?.get(song.id)?.isDone == true ||
        cachedList.any { it.id == song.id }
    val unavailable = (!online && !downloaded) || status != null

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = when { status != null -> 0.55f; unavailable -> 0.38f; else -> 1f } }
            .pressMenu(onClick = onClick, menu = { menus?.song(song, menuExtra) }, highlight = true)
            .padding(start = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else Artwork(song.thumbnailUrl, Modifier.size(46.dp), corner = 6.dp, small = true)
        Spacer(Modifier.width(13.dp))
        Box(Modifier.weight(1f).height(if (leading != null) 50.dp else 60.dp)) {
            Row(
                Modifier.fillMaxSize().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = AppTheme.type.callout,
                        color = if (isCurrent) c.accent else c.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        if (status != null) "${song.artists} · $status" else song.artists,
                        style = AppTheme.type.subhead.copy(fontSize = 14.sp),
                        color = c.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                val downloadInfo = connection?.downloads?.downloads?.collectAsStateWithLifecycle()?.value?.get(song.id)
                if (downloadInfo != null) {
                    DownloadBadge(downloadInfo)
                    Spacer(Modifier.width(6.dp))
                }
                if (isCurrent) {
                    val playing = connection?.isPlaying?.collectAsStateWithLifecycle()?.value == true
                    Equalizer(playing, c.accent, Modifier.size(width = 16.dp, height = 14.dp))
                } else if (showDuration) {
                    song.durationText?.let {
                        Text(it, style = AppTheme.type.footnote, color = c.tertiaryLabel)
                    }
                }
                if (trailing != null) trailing() else SongMenuButton(song, extra = menuExtra)
            }
            if (showDivider) {
                HorizontalDivider(
                    Modifier.align(Alignment.BottomStart),
                    thickness = 0.5.dp,
                    color = c.separator.copy(alpha = if (c.isDark) 0.55f else 0.7f),
                )
            }
        }
    }
}

/** Square artwork card used in horizontal shelves and grids. */
@Composable
fun SongCard(song: SongItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val menus = LocalMenus.current
    Column(modifier.pressMenu(onClick = onClick, menu = { menus?.song(song) })) {
        Box {
            Artwork(song.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 10.dp)
            NowPlayingBadge(song.id, Modifier.align(Alignment.BottomStart).padding(8.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            song.title,
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            song.artists,
            style = AppTheme.type.footnote,
            color = AppTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Header + horizontal row of cards; ~2.2 cards visible so the next one peeks. */
@Composable
fun SongShelf(
    title: String,
    songs: List<SongItem>,
    onPlay: (List<SongItem>, Int) -> Unit,
    onHeaderClick: (() -> Unit)? = null,
) {
    Column {
        SectionHeader(title, onClick = onHeaderClick)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (phoneWidth(maxWidth) - Gutter - 12.dp * 2) / 2.2f
            val state = rememberLazyListState()
            // A song added at the front (Recently Played) would otherwise leave the row anchored
            // on the old first card, half scrolled. If you were at the start, stay at the start.
            val first = songs.firstOrNull()?.id
            LaunchedEffect(first) {
                if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
            }
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            ) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    SongCard(song, Modifier.width(cardWidth)) { onPlay(songs, i) }
                }
            }
        }
    }
}

/** Big featured cards ("Top Picks" style): artwork with a caption over a scrim. */
@Composable
fun HeroShelf(
    title: String,
    songs: List<SongItem>,
    onPlay: (List<SongItem>, Int) -> Unit,
) {
    Column {
        SectionHeader(title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = phoneWidth(maxWidth) - Gutter * 2 - 36.dp
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            ) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    HeroCard(song, Modifier.width(cardWidth)) { onPlay(songs, i) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(song: SongItem, modifier: Modifier, onClick: () -> Unit) {
    val menus = LocalMenus.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .pressMenu(onClick = onClick, menu = { menus?.song(song) })
            .clip(shape)
            .background(AppTheme.colors.elevated),
    ) {
        AsyncImage(
            model = song.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
        )
        NowPlayingBadge(song.id, Modifier.align(Alignment.TopEnd).padding(12.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                song.title,
                style = AppTheme.type.title3,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                song.artists,
                style = AppTheme.type.subhead,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PlayShuffleButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PillButton(Icons.Rounded.PlayArrow, tr("Play", "Çal"), onPlay, Modifier.weight(1f))
        PillButton(Icons.Rounded.Shuffle, tr("Shuffle", "Karıştır"), onShuffle, Modifier.weight(1f))
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    val c = AppTheme.colors
    Row(
        modifier
            .height(48.dp)
            .bounceClick(onClick = onClick)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (c.isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.055f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = AppTheme.type.headline, color = c.accent)
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 80.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = AppTheme.colors.secondaryLabel,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    val c = AppTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = c.tertiaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = AppTheme.type.title3, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = AppTheme.type.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            Text(
                action.first,
                style = AppTheme.type.headline,
                color = c.accent,
                modifier = Modifier.clickable(onClick = action.second).padding(8.dp),
            )
        }
    }
}

@Composable
fun DownloadBadge(info: com.enes.liquidtube.download.DownloadInfo) {
    val c = AppTheme.colors
    if (info.isDone) {
        Icon(
            androidx.compose.material.icons.Icons.Rounded.DownloadDone, tr("Downloaded", "İndirildi"),
            tint = c.secondaryLabel, modifier = Modifier.size(16.dp),
        )
    } else {
        androidx.compose.material3.CircularProgressIndicator(
            progress = { (info.percent.coerceAtLeast(0f) / 100f) },
            color = c.accent,
            trackColor = c.fill,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Apple's animated "now playing" bars. */
@Composable
fun Equalizer(playing: Boolean, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
    val heights = listOf(520, 380, 610).mapIndexed { i, ms ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(ms, delayMillis = i * 90),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "bar$i",
        )
    }
    val still = listOf(0.45f, 0.8f, 0.6f)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(if (playing) h.value else still[i])
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}

/** Red equalizer chip shown on artwork of the song that's playing. */
@Composable
fun NowPlayingBadge(songId: String, modifier: Modifier = Modifier) {
    val connection = LocalPlayerConnection.current ?: return
    val current by connection.current.collectAsStateWithLifecycle()
    if (current?.id != songId) return
    val playing by connection.isPlaying.collectAsStateWithLifecycle()
    Box(
        modifier
            .size(26.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(AppTheme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Equalizer(playing, androidx.compose.ui.graphics.Color.White, Modifier.size(width = 12.dp, height = 11.dp))
    }
}
