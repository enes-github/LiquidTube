package com.enes.liquidtube.ui.collection

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.CollectionShelf
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.TintedBackdrop
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.LocalMenus
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.MenuAction
import com.enes.liquidtube.ui.components.MenuSpec
import com.enes.liquidtube.ui.components.MixedShelf
import com.enes.liquidtube.ui.components.QuickPicksShelf
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.SongShelf
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.menuButton
import com.enes.liquidtube.ui.components.pressMenu
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.glassChip
import com.enes.liquidtube.ui.glass.liquidGlass
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.buttonGlass
import com.enes.innertube.models.BrowsePage
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.SongItem
import kotlinx.coroutines.launch

/*
 * iOS 27-style artist page: the artist photo melts into a blurred, tinted copy of
 * itself that fills the whole page; the name sits centred over the photo with an
 * info / Play / follow row underneath, then a glass "latest release" card.
 */

/** Follow state shared between the page's star and the toolbar menu. */
internal class ArtistState(initial: Boolean) {
    var following by mutableStateOf(initial)
    var busy by mutableStateOf(false)
}

@Composable
internal fun rememberArtistState(page: BrowsePage) = remember(page.browseId) { ArtistState(page.subscribed) }

@Composable
internal fun ArtistPage(page: BrowsePage, title: TitleState, artist: ArtistState) {
    val connection = LocalPlayerConnection.current ?: return
    val c = AppTheme.colors
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    val play: (List<SongItem>, Int) -> Unit = { songs, i -> connection.play(songs, i) }
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()
    val nav = LocalNavActions.current
    val latest = remember(page.browseId) { latestRelease(page) }
    val about = page.description?.takeIf { it.isNotBlank() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape: a wide banner instead of a portrait photo taller than the screen.
        val heroHeight = if (maxWidth > maxHeight) maxHeight * 0.72f else maxWidth * 1.1f
        val heroPx = with(density) { heroHeight.toPx() }
        val collapsed by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > heroPx * 0.72f }
        }
        LaunchedEffect(collapsed) { title.collapsed = collapsed; title.scrolled = collapsed }

        TintedBackdrop(page.thumbnailUrl) {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else heroPx
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomInset.current),
        ) {
            item(key = "hero") { Hero(page, heroHeight, heroPx, listState) }

            item(key = "actions") {
                PlayActionRow(
                    onPlay = if (page.songs.isNotEmpty()) ({ connection.play(page.songs) }) else null,
                    left = SideAction(Icons.Outlined.Info, "About", enabled = about != null) {
                        aboutExpanded = true
                        scope.launch { listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1) }
                    },
                    right = SideAction(
                        if (artist.following) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        if (artist.following) "Unfollow" else "Follow",
                        enabled = loggedIn && page.channelId != null,
                        active = artist.following,
                    ) { toggleFollow(page, artist, connection) },
                )
            }

            latest?.let { release ->
                item(key = "latest") {
                    Box(Modifier.appear(0)) { LatestReleaseCard(release, canSave = loggedIn) }
                }
            }

            if (page.songs.isNotEmpty()) {
                item(key = "top") {
                    Box(Modifier.appear(1)) {
                        QuickPicksShelf(
                            "Top Songs", page.songs, play,
                            onHeaderClick = page.songsMore?.let { ref -> { nav.openSection(ref, "Top Songs") } },
                        )
                    }
                }
            }

            page.shelves.forEachIndexed { i, shelf ->
                item(key = "shelf-$i") {
                    Box(Modifier.appear(i + 2)) {
                        // Headings open the whole section (every album, all videos…).
                        val seeAll = shelf.more?.let { ref -> { nav.openSection(ref, shelf.title) } }
                        when {
                            shelf.collections.isNotEmpty() && shelf.songs.isNotEmpty() -> MixedShelf(shelf, play, seeAll)
                            shelf.collections.isNotEmpty() -> CollectionShelf(shelf.title, shelf.collections, seeAll)
                            else -> SongShelf(shelf.title, shelf.songs, play, seeAll)
                        }
                    }
                }
            }

            about?.let { text ->
                item(key = "about") {
                    Column {
                        SectionHeader("About ${page.title}")
                        Column(
                            Modifier
                                .padding(horizontal = Gutter)
                                .fillMaxWidth()
                                .glassChip(tint = cardTint(c.isDark), corner = 22.dp, rim = if (c.isDark) 0.3f else 0.8f)
                                .clickable { aboutExpanded = !aboutExpanded }
                                .padding(18.dp),
                        ) {
                            page.audience?.let {
                                Text(it.uppercase(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp), color = c.secondaryLabel)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                text,
                                style = AppTheme.type.subhead,
                                color = c.label,
                                maxLines = if (aboutExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!aboutExpanded) {
                                Spacer(Modifier.height(6.dp))
                                Text("MORE", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = c.label)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

internal fun cardTint(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.5f)

private fun toggleFollow(page: BrowsePage, artist: ArtistState, connection: com.enes.liquidtube.playback.PlayerConnection) {
    val channel = page.channelId ?: return
    if (artist.busy) return
    val target = !artist.following
    artist.following = target // optimistic, like iOS
    artist.busy = true
    connection.followArtist(channel, target) { result ->
        artist.following = result
        artist.busy = false
    }
}

@Composable
private fun Hero(page: BrowsePage, heroHeight: androidx.compose.ui.unit.Dp, heroPx: Float, listState: LazyListState) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(heroHeight).clipToBounds()) {
        val heroWidth = maxWidth
        // A banner much wider than the photo (landscape): show the whole photo at full height
        // in the middle with its sides melting away, instead of cropping it to a thin strip.
        val wide = heroWidth > heroHeight * 1.9f
        // The photo drifts (parallax) inside a frame whose lower part melts into the
        // blurred backdrop. The fade belongs to the frame, so it never turns into a hard edge.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black,
                            0.82f to Color.Black.copy(alpha = 0.55f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    if (wide) {
                        val photo = size.height * 1.25f
                        val l = ((size.width - photo) / 2f / size.width).coerceIn(0f, 0.45f)
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent,
                                l to Color.Transparent,
                                (l + 0.12f).coerceAtMost(0.5f) to Color.Black,
                                (1f - l - 0.12f).coerceAtLeast(0.5f) to Color.Black,
                                1f - l to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        ) {
            AsyncImage(
                model = page.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = androidx.compose.ui.BiasAlignment(0f, -0.35f), // faces sit high in artist photos
                modifier = Modifier
                    .then(if (wide) Modifier.fillMaxHeight().width(heroHeight * 1.25f).align(Alignment.Center) else Modifier.fillMaxSize())
                    .graphicsLayer {
                        val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else heroPx
                        translationY = scrolled * 0.4f
                        val s = 1f + (scrolled / heroPx).coerceIn(0f, 1f) * 0.06f
                        scaleX = s
                        scaleY = s
                    },
            )
        }
        // Soft dark veil at the top so the status bar and toolbar stay legible.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            page.audience?.let { audience ->
                Row(
                    Modifier
                        .glassChip(tint = Color.Black.copy(alpha = 0.22f), rim = 0.35f)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        audience,
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(
                page.title,
                style = AppTheme.type.largeTitle.copy(
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 18f),
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One of the round glass buttons either side of the big Play button. */
internal class SideAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val enabled: Boolean = true,
    /** Highlighted (accent tint + a little pop), e.g. following / added. */
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/** iOS 27 action row: glass side buttons around a big white Play button. */
@Composable
internal fun PlayActionRow(onPlay: (() -> Unit)?, left: SideAction?, right: SideAction?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (left != null) SideButton(left) else Spacer(Modifier.size(46.dp))
        Spacer(Modifier.width(30.dp))
        if (onPlay != null) {
            Box(
                Modifier
                    .size(66.dp)
                    .shadow(18.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .bounceClick(pressedScale = 0.9f, onClick = onPlay)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(38.dp))
            }
        } else {
            Spacer(Modifier.size(66.dp))
        }
        Spacer(Modifier.width(30.dp))
        if (right != null) SideButton(right) else Spacer(Modifier.size(46.dp))
    }
}

@Composable
private fun SideButton(action: SideAction) {
    val c = AppTheme.colors
    val pop by animateFloatAsState(if (action.active) 1f else 0.94f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "pop")
    Box(
        Modifier
            .size(46.dp)
            .graphicsLayer {
                alpha = if (action.enabled) 1f else 0.35f
                scaleX = pop
                scaleY = pop
            }
            .then(if (action.enabled) Modifier.bounceClick(pressedScale = 0.85f, onClick = action.onClick) else Modifier)
            .glassChip(tint = cardTint(c.isDark), rim = if (c.isDark) 0.35f else 0.85f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(action.icon, action.description, tint = if (action.active) c.accent else c.label, modifier = Modifier.size(24.dp))
    }
}

/** The newest album/single from the artist's shelves (by year), for the glass card. */
private fun latestRelease(page: BrowsePage): CollectionItem? {
    val year = Regex("""\b(19|20)\d{2}\b""")
    val candidates = page.shelves
        .flatMap { it.collections.take(3) }
        .filter { it.type == CollectionType.ALBUM }
    return candidates.maxByOrNull { item -> item.subtitle?.let { year.find(it)?.value?.toIntOrNull() } ?: 0 }
}

@Composable
private fun LatestReleaseCard(item: CollectionItem, canSave: Boolean) {
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val menus = LocalMenus.current
    val c = AppTheme.colors
    var saved by remember(item.browseId) { mutableStateOf(false) }
    Row(
        Modifier
            .padding(horizontal = Gutter)
            .padding(bottom = 6.dp)
            .widthIn(max = 560.dp) // landscape: a card, not a full-width strip
            .fillMaxWidth()
            .glassChip(tint = cardTint(c.isDark), corner = 22.dp, rim = if (c.isDark) 0.3f else 0.8f)
            .pressMenu(onClick = { nav.openCollection(item.browseId) }, menu = { menus?.collection(item) }, pressedScale = 0.98f)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.thumbnailUrl, Modifier.size(78.dp), corner = 10.dp, small = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "LATEST RELEASE",
                style = AppTheme.type.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                color = c.secondaryLabel,
            )
            Spacer(Modifier.height(2.dp))
            Text(item.title, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let {
                Text(it, style = AppTheme.type.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canSave) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(34.dp)
                    .bounceClick(pressedScale = 0.85f) {
                        if (!saved) {
                            saved = true
                            connection.savePlaylist(item.browseId, save = true)
                        }
                    }
                    .clip(CircleShape)
                    .background(if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, if (saved) "Added" else "Add to Library", tint = c.accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Top-right glass capsule on artist pages: Share and •••. */
@Composable
internal fun ArtistToolbarActions(page: BrowsePage, artist: ArtistState, backdrop: GlassBackdrop, tint: Color) {
    val connection = LocalPlayerConnection.current ?: return
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()
    val shareUrl = "https://music.youtube.com/channel/${page.browseId}"
    GlassActionsCapsule(backdrop, tint, onShare = { connection.share(page.title, shareUrl) }) {

                    MenuSpec(
                        title = page.title,
                        subtitle = page.audience,
                        artworkUrl = page.thumbnailUrl,
                        roundArtwork = true,
                        quick = buildList {
                            if (page.songs.isNotEmpty()) {
                                add(MenuAction("Play", Icons.Rounded.PlayArrow) { connection.play(page.songs) })
                                add(MenuAction("Shuffle", Icons.Rounded.Shuffle) { connection.shufflePlay(page.songs) })
                                add(MenuAction("Station", Icons.Rounded.Sensors) { connection.playSong(page.songs.first()) })
                            }
                            add(MenuAction("Share", Icons.Rounded.Share) { connection.share(page.title, shareUrl) })
                        },
                        groups = listOf(
                            buildList {
                                if (loggedIn && page.channelId != null) {
                                    add(MenuAction(
                                        if (artist.following) "Unfollow" else "Follow",
                                        if (artist.following) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    ) { toggleFollow(page, artist, connection) })
                                }
                                if (page.songs.isNotEmpty()) {
                                    add(MenuAction("Add Top Songs to Queue", Icons.AutoMirrored.Rounded.PlaylistAdd) {
                                        page.songs.forEach(connection::addToQueue)
                                    })
                                }
                            },
                        ).filter { it.isNotEmpty() },
                    )
    }
}

/** Glass capsule with Share and a ••• menu (tap, or hold and slide). */
@Composable
internal fun GlassActionsCapsule(backdrop: GlassBackdrop, tint: Color, onShare: () -> Unit, menu: () -> MenuSpec?) {
    Row(
        Modifier
            .height(44.dp)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f), clip = false)
            .liquidGlass(backdrop, buttonGlass(AppTheme.colors.isDark)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 50.dp, height = 44.dp).bounceClick(pressedScale = 0.85f, onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Share, "Share", tint = tint, modifier = Modifier.size(21.dp))
        }
        Box(
            Modifier.size(width = 50.dp, height = 44.dp).menuButton(Offset(-180f, 140f), menu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreHoriz, "More", tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}
