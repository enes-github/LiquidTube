package com.enes.liquidtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.FeedShelf
import com.enes.innertube.models.SongItem

/** App-wide navigation callbacks, so deep components can open pages. */
class NavActions(
    val openCollection: (String) -> Unit = {},
    val openAccount: () -> Unit = {},
    /** Opens a full section (all top songs, whole discography…) titled [title]. */
    val openSection: (com.enes.innertube.models.SectionRef, String) -> Unit = { _, _ -> },
    /** Add Songs for one of your playlists (id without "VL", and its name). */
    val openAddSongs: (String, String) -> Unit = { _, _ -> },
)

val LocalNavActions = staticCompositionLocalOf { NavActions() }

// ------------------------------------------------------------ collections

@Composable
fun CollectionCard(
    item: CollectionItem,
    modifier: Modifier = Modifier,
    menuExtra: List<MenuAction> = emptyList(),
    onClick: () -> Unit,
) {
    val isArtist = item.type == CollectionType.ARTIST
    val menus = LocalMenus.current
    Column(
        modifier.pressMenu(onClick = onClick, menu = { menus?.collection(item, menuExtra) }),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        if (isArtist) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape)) {
                Artwork(item.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 1000.dp)
            }
        } else {
            Artwork(item.thumbnailUrl, Modifier.fillMaxWidth().aspectRatio(1f), corner = 8.dp)
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Medium),
            color = AppTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        if (!isArtist) {
            item.subtitle?.let {
                Text(
                    it,
                    style = AppTheme.type.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun CollectionShelf(title: String, items: List<CollectionItem>, onHeaderClick: (() -> Unit)? = null) {
    val nav = LocalNavActions.current
    Column {
        SectionHeader(title, onClick = onHeaderClick)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (phoneWidth(maxWidth) - Gutter - 12.dp * 2) / 2.2f
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            ) {
                items(items, key = { it.browseId }) { item ->
                    CollectionCard(item, Modifier.width(cardWidth)) { nav.openCollection(item.browseId) }
                }
            }
        }
    }
}

/** Apple-style paged grid of compact song rows (4 per column). */
@Composable
fun QuickPicksShelf(title: String, songs: List<SongItem>, onPlay: (List<SongItem>, Int) -> Unit, onHeaderClick: (() -> Unit)? = null) {
    Column {
        SectionHeader(title, onClick = onHeaderClick)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = phoneWidth(maxWidth) - Gutter * 2 - 28.dp
            val state = rememberLazyGridState()
            LazyHorizontalGrid(
                rows = GridCells.Fixed(4),
                state = state,
                modifier = Modifier.height(64.dp * 4),
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, song ->
                    CompactSongRow(song, Modifier.width(itemWidth)) { onPlay(songs, i) }
                }
            }
        }
    }
}

@Composable
private fun CompactSongRow(song: SongItem, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val menus = LocalMenus.current
    Row(
        modifier.height(64.dp).pressMenu(onClick = onClick, menu = { menus?.song(song) }, highlight = true),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Artwork(song.thumbnailUrl, Modifier.size(50.dp), corner = 7.dp, small = true)
            NowPlayingBadge(song.id, Modifier.align(Alignment.Center))
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(64.dp), contentAlignment = Alignment.CenterStart) {
            Column {
                Text(song.title, style = AppTheme.type.callout, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists, style = AppTheme.type.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator)
        }
        SongMenuButton(song)
    }
}

enum class FeedStyle { Home, New }

/** Renders a list of feed shelves, choosing the best layout for each (Apple Music style). */
fun LazyListScope.feedShelves(
    shelves: List<FeedShelf>,
    onPlay: (List<SongItem>, Int) -> Unit,
    style: FeedStyle = FeedStyle.Home,
) {
    var featureUsed = false
    var heroUsed = style == FeedStyle.Home
    // "Made for You": YouTube Music's personal mixes (Supermix, My Mix 1–7, Discover Mix…)
    // lead Home, in the big Top Picks style.
    val mixPattern = Regex("""mixed for you|made for you|your mixes|supermix|my mix|discover mix""", RegexOption.IGNORE_CASE)
    val ordered = if (style == FeedStyle.Home) {
        val (mixes, rest) = shelves.partition { sh ->
            mixPattern.containsMatchIn(sh.title) ||
                (sh.collections.size >= 3 && sh.collections.count { mixPattern.containsMatchIn(it.title) } >= sh.collections.size / 2)
        }
        mixes.mapIndexed { i, m -> if (i == 0) m.copy(title = "Made for You") else m } + rest
    } else shelves
    ordered.forEachIndexed { index, shelf ->
        val key = "shelf-$index-${shelf.title}"
        val songsOnly = shelf.songs.isNotEmpty() && shelf.collections.isEmpty()
        val collectionsOnly = shelf.collections.isNotEmpty() && shelf.songs.isEmpty()
        when {
            collectionsOnly && !featureUsed && shelf.collections.none { it.type == CollectionType.ARTIST } -> {
                featureUsed = true
                item(key = key) {
                    Box(Modifier.appear(index)) {
                        if (style == FeedStyle.New) FeatureShelf(shelf.title, shelf.collections)
                        else TopPicksShelf(shelf.title, shelf.collections)
                    }
                }
            }
            songsOnly && shelf.songs.size >= 8 && index < 3 ->
                item(key = key) { Box(Modifier.appear(index)) { QuickPicksShelf(shelf.title, shelf.songs, onPlay) } }
            songsOnly && !heroUsed -> {
                heroUsed = true
                item(key = key) { Box(Modifier.appear(index)) { HeroShelf(shelf.title, shelf.songs, onPlay) } }
            }
            songsOnly -> item(key = key) { Box(Modifier.appear(index)) { SongShelf(shelf.title, shelf.songs, onPlay) } }
            shelf.songs.isNotEmpty() -> item(key = key) { Box(Modifier.appear(index)) { MixedShelf(shelf, onPlay) } }
            shelf.collections.isNotEmpty() ->
                item(key = key) { Box(Modifier.appear(index)) { CollectionShelf(shelf.title, shelf.collections) } }
        }
    }
}

/** Home's "Top Picks": tall cards whose artwork melts into a frosted caption band. */
@Composable
fun TopPicksShelf(title: String, items: List<CollectionItem>) {
    val nav = LocalNavActions.current
    val menus = LocalMenus.current
    Column {
        SectionHeader(title)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (phoneWidth(maxWidth) - Gutter * 2 - 12.dp) / 1.45f
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            ) {
                items(items, key = { it.browseId }) { item ->
                    TopPickCard(
                        item,
                        Modifier
                            .width(cardWidth)
                            .pressMenu(onClick = { nav.openCollection(item.browseId) }, menu = { menus?.collection(item) }),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPickCard(item: CollectionItem, modifier: Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(0.74f)
            .clip(shape)
            .background(AppTheme.colors.elevated),
    ) {
        coil3.compose.AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        // Frosted band: a blurred copy of the art fills the lower part and fades upward.
        BlurredImage(
            item.thumbnailUrl,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Transparent,
                            0.52f to androidx.compose.ui.graphics.Color.Transparent,
                            0.72f to androidx.compose.ui.graphics.Color.Black,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                    )
                },
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.55f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                    )
                ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            item.subtitle?.substringBefore(" · ")?.let {
                Text(
                    it.uppercase(),
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
            Text(
                item.title,
                style = AppTheme.type.title3,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The New tab's editorial hero: label, title, subtitle, then a wide glowing card. */
@Composable
fun FeatureShelf(title: String, items: List<CollectionItem>) {
    val nav = LocalNavActions.current
    val menus = LocalMenus.current
    val c = AppTheme.colors
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val cardWidth = phoneWidth(maxWidth) - Gutter * 2 - 20.dp
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(items, key = { it.browseId }) { item ->
                val kind = item.subtitle?.substringBefore(" · ")?.takeIf { it.length < 14 } ?: title
                Column(
                    Modifier
                        .width(cardWidth)
                        .pressMenu(onClick = { nav.openCollection(item.browseId) }, menu = { menus?.collection(item) }),
                ) {
                    Text(
                        "NEW ${kind.uppercase()}",
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                        color = c.secondaryLabel,
                    )
                    Text(item.title, style = AppTheme.type.title3, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.subtitle?.let { sub -> sub.substringAfter(" · ", sub) } ?: "",
                        style = AppTheme.type.subhead,
                        color = c.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.55f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(c.elevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        BlurredImage(item.thumbnailUrl, Modifier.matchParentSize()) {
                            scaleX = 1.3f
                            scaleY = 1.3f
                        }
                        Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)))
                        Box(
                            Modifier
                                .fillMaxHeight(0.78f)
                                .aspectRatio(1f)
                                .shadow(24.dp, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        ) {
                            Artwork(item.thumbnailUrl, Modifier.fillMaxSize(), corner = 10.dp)
                        }
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------- song menu

/** The ••• button: opens the same iOS context menu as a long press. */
@Composable
fun SongMenuButton(song: SongItem, modifier: Modifier = Modifier, extra: List<MenuAction> = emptyList()) {
    val menus = LocalMenus.current ?: return
    Box(
        modifier
            .size(40.dp)
            .menuButton { menus.song(song, extra) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MoreHoriz, "More", tint = AppTheme.colors.secondaryLabel, modifier = Modifier.size(22.dp))
    }
}

/** A shelf mixing songs, albums, playlists and artists (e.g. "Listen again"). */
@Composable
fun MixedShelf(shelf: FeedShelf, onPlay: (List<SongItem>, Int) -> Unit, onHeaderClick: (() -> Unit)? = null) {
    val nav = LocalNavActions.current
    Column {
        SectionHeader(shelf.title, onClick = onHeaderClick)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (phoneWidth(maxWidth) - Gutter - 12.dp * 2) / 2.2f
            val state = rememberLazyListState()
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
            ) {
                items(shelf.items.size) { i ->
                    when (val item = shelf.items[i]) {
                        is SongItem -> SongCard(item, Modifier.width(cardWidth)) {
                            onPlay(shelf.songs, shelf.songs.indexOf(item).coerceAtLeast(0))
                        }
                        is CollectionItem -> CollectionCard(item, Modifier.width(cardWidth)) {
                            nav.openCollection(item.browseId)
                        }
                    }
                }
            }
        }
    }
}
