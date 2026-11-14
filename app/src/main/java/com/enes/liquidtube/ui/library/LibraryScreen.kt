package com.enes.liquidtube.ui.library

import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import com.enes.liquidtube.ui.components.isLandscape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.CollectionCard
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LargeTitle
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.PlayShuffleButtons
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.SongCard
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.home.AccountButton
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.components.LocalOverlay
import com.enes.liquidtube.ui.components.MenuAction
import com.enes.liquidtube.ui.components.PromptSpec
import com.enes.liquidtube.ui.components.bounceClick
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.enes.liquidtube.ui.components.LocalMenus
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.pressMenu
import com.enes.liquidtube.ui.components.FilterChips
import com.enes.liquidtube.ui.components.CollectionRow
import com.enes.liquidtube.ui.components.GlassGroup
import com.enes.liquidtube.ui.components.GlassGroupRow
import com.enes.liquidtube.ui.components.SongShelf
import com.enes.innertube.models.SongItem
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ArrowCircleDown
import com.enes.liquidtube.ui.glass.glassChip
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Library list kinds backed by YouTube Music (signed in). */
enum class LibraryKind(val route: String, val title: String, val browseId: String) {
    Playlists("playlists", "Playlists", "FEmusic_liked_playlists"),
    Albums("albums", "Albums", "FEmusic_liked_albums"),
    Artists("artists", "Artists", "FEmusic_library_corpus_track_artists"),
}

@Composable
fun LibraryScreen(onOpenList: (String) -> Unit, onOpenLibrary: (String) -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val c = AppTheme.colors
    val recents by connection.library.recents.collectAsStateWithLifecycle()
    val favorites by connection.library.favorites.collectAsStateWithLifecycle()
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()
    val downloadMap by connection.downloads.downloads.collectAsStateWithLifecycle()
    val autoIds by connection.downloads.autoIds.collectAsStateWithLifecycle()
    // Everything with the downloaded badge, whether you or the Offline Mix downloaded it.
    val downloaded = downloadMap.values.filter { it.isDone }.map { it.song }
    val cachedSongs by connection.playCache.cached.collectAsStateWithLifecycle()
    val offlineMix = (downloadMap.values.filter { it.isDone && it.song.id in autoIds }.map { it.song } +
        cachedSongs.filter { downloadMap[it.id]?.isDone != true }).distinctBy { it.id }
    val smartOn by connection.settings.smartDownloads.collectAsStateWithLifecycle()
    val play: (List<SongItem>, Int) -> Unit = { songs, i -> connection.play(songs, i) }
    val listening by connection.library.listening.collectAsStateWithLifecycle()
    val replayArt = remember(listening) {
        listening.entries.maxByOrNull { it.key }?.value.orEmpty().entries.sortedByDescending { it.value }
            .mapNotNull { connection.library.songFor(it.key)?.thumbnailUrl }.distinct().take(4)
    }

    // Pinned cards: real artwork collages, like Apple Music's pinned items.
    val pins = buildList {
        add(PinCard("Favorite Songs", "${songCount(favorites.size)}", favorites.art(), Icons.Rounded.Star, PinRed) { onOpenList("favorites") })
        add(PinCard("Recently Played", "${songCount(recents.size)}", recents.art(), Icons.Rounded.History, PinBlue) { onOpenList("recents") })
        add(PinCard("Replay", "Your month in music", replayArt, Icons.Rounded.Insights, PinOrange) { onOpenList("replay") })
        if (loggedIn) add(PinCard("Liked Songs", "YouTube Music", emptyList(), Icons.Rounded.Favorite, PinPink) { nav.openCollection("VLLM") })
        add(PinCard("Downloaded", "${songCount(downloaded.size)}", downloaded.art(), Icons.Rounded.DownloadDone, PinGreen) { onOpenList("downloads") })
        if (smartOn || offlineMix.isNotEmpty()) {
            add(PinCard("Offline Mix", "${songCount(offlineMix.size)} \u00B7 Auto", offlineMix.art(), Icons.Rounded.AutoAwesome, PinPurple) { onOpenList("offline") })
        }
    }

    val across = if (isLandscape()) 6 else 2 // landscape: all the pins in one row
    ScreenScaffold("Library", trailing = { AccountButton() }) {
        itemsIndexed(pins.chunked(across), key = { i, _ -> "pins-$i" }) { row, pair ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = if (row == 0) 6.dp else 0.dp, bottom = 14.dp).appear(row),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pair.forEach { PinnedCard(it, Modifier.weight(1f)) }
                repeat(across - pair.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // Smart playlists: fill themselves from how you listen.
        val smart = com.enes.liquidtube.data.SmartPlaylists.all(connection.library, downloadMap).filter { it.songs.isNotEmpty() }
        if (smart.isNotEmpty()) {
            item(key = "smart") {
                Column(Modifier.appear(2)) {
                    SectionHeader("Smart Playlists")
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Gutter),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(smart.size, key = { smart[it].id }) { i ->
                            val sp = smart[i]
                            Column(Modifier.width(150.dp).bounceClick { onOpenList(sp.id) }) {
                                val shape = RoundedCornerShape(14.dp)
                                Box(Modifier.size(150.dp).clip(shape).background(Brush.linearGradient(smartGradient(sp.id)))) {
                                    CollageArt(sp.songs.art(), Icons.Rounded.AutoAwesome, iconSize = 48.dp)
                                    Box(
                                        Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .size(26.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(Brush.linearGradient(smartGradient(sp.id))),
                                        contentAlignment = Alignment.Center,
                                    ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(sp.title, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(songCount(sp.songs.size), style = AppTheme.type.caption, color = c.secondaryLabel, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        if (loggedIn) {
            item(key = "list") {
                Column(Modifier.padding(top = 8.dp).appear(2)) {
                    LibraryMenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists") { onOpenLibrary(LibraryKind.Playlists.route) }
                    LibraryMenuRow(Icons.Rounded.MicExternalOn, "Artists") { onOpenLibrary(LibraryKind.Artists.route) }
                    LibraryMenuRow(Icons.Rounded.Album, "Albums", showDivider = false) { onOpenLibrary(LibraryKind.Albums.route) }
                }
            }
        } else {
            item {
                Text(
                    "Sign in with Google to see your YouTube Music playlists, albums, artists and liked songs.",
                    style = AppTheme.type.footnote,
                    color = c.secondaryLabel,
                    modifier = Modifier
                        .clickable(onClick = nav.openAccount)
                        .padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                )
            }
        }

        if (recents.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.LibraryMusic,
                    "Nothing played yet",
                    "Songs you play and favorite will show up here.",
                )
            }
        } else {
            item(key = "recent-shelf") {
                Box(Modifier.appear(3)) {
                    SongShelf("Recently Played", recents.take(15), play, onHeaderClick = { onOpenList("recents") })
                }
            }
        }
    }
}

private val PinRed = listOf(Color(0xFFFF5E62), Color(0xFFD7263D))
private val PinBlue = listOf(Color(0xFF4FACFE), Color(0xFF2B5BD7))
private val PinPink = listOf(Color(0xFFFF6CAB), Color(0xFFB33AA8))
private val PinGreen = listOf(Color(0xFF43E97B), Color(0xFF1E9E63))
private val PinPurple = listOf(Color(0xFFB27CFF), Color(0xFF5B3CC4))
private val PinOrange = listOf(Color(0xFFFFB347), Color(0xFFE5532D))

private fun smartGradient(id: String) = when (id) {
    com.enes.liquidtube.data.SmartPlaylists.ON_REPEAT -> listOf(Color(0xFFFF7EB3), Color(0xFFFF4E50))
    com.enes.liquidtube.data.SmartPlaylists.MOST_PLAYED -> listOf(Color(0xFFF7B733), Color(0xFFFC4A1A))
    com.enes.liquidtube.data.SmartPlaylists.FORGOTTEN -> listOf(Color(0xFF7F7FD5), Color(0xFF5B6CC4))
    "smart-time-morning" -> listOf(Color(0xFFFFD86F), Color(0xFFFC6262))
    "smart-time-afternoon" -> listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))
    "smart-time-evening" -> listOf(Color(0xFFEE9CA7), Color(0xFF7B4397))
    "smart-time-night" -> listOf(Color(0xFF3A6073), Color(0xFF16222A))
    else -> listOf(Color(0xFF38EF7D), Color(0xFF11998E))
}

/** Up to four distinct covers for a collage. */
private fun List<SongItem>.art() = mapNotNull { it.thumbnailUrl }.distinct().take(4)

/** A 2×2 collage of the list's covers (one cover if fewer, or the list's icon if none). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CollageArt(art: List<String>, icon: ImageVector, iconSize: Dp) {
    when {
        art.size >= 4 -> Column(Modifier.fillMaxSize()) {
            art.take(4).chunked(2).forEach { pair ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    pair.forEach { url ->
                        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
        }
        art.isNotEmpty() -> AsyncImage(art.first(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else -> Icon(
            icon, null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.Center).size(iconSize),
        )
    }
}

/** The big cover at the top of Favorites / Downloaded / Offline Mix / Recently Played. */
@Composable
private fun ListCover(art: List<String>, icon: ImageVector, gradient: List<Color>, subtitle: String) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier
                .size(if (isLandscape()) 150.dp else 210.dp)
                .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(shape)
                .background(Brush.linearGradient(gradient)),
        ) {
            CollageArt(art, icon, iconSize = 84.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text(subtitle.uppercase(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = c.secondaryLabel)
    }
}

private class PinCard(
    val title: String,
    val subtitle: String,
    val art: List<String>,
    val icon: ImageVector,
    val gradient: List<Color>,
    val onClick: () -> Unit,
)

/**
 * A pinned library item: a square of real artwork (a 2×2 collage when there are four
 * covers, one cover otherwise, a colour gradient when empty), title and count on a
 * soft shade at the bottom, and a small glass badge with the item's symbol.
 */
@Composable
private fun PinnedCard(pin: PinCard, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .bounceClick(pressedScale = 0.96f, onClick = pin.onClick)
            .clip(shape)
            .background(Brush.linearGradient(pin.gradient)),
    ) {
        CollageArt(pin.art, pin.icon, iconSize = 52.dp)
        // Soft shade so the title always reads.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
        )
        if (pin.art.isNotEmpty()) {
            Box(
                Modifier
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Brush.linearGradient(pin.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(pin.icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 11.dp)) {
            // Narrow cards (six across in landscape) wrap the title instead of cutting it off.
            Text(pin.title, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.Bold, lineHeight = 17.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pin.subtitle, style = AppTheme.type.caption, color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Apple Music's library row: red symbol, large label, chevron, inset hairline. */
@Composable
private fun LibraryMenuRow(
    icon: ImageVector,
    label: String,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().pressMenu(onClick = onClick, menu = null, highlight = true).padding(start = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f).height(54.dp)) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(end = Gutter - 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = AppTheme.type.title3.copy(fontWeight = FontWeight.Normal),
                    color = c.label,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(24.dp))
            }
            if (showDivider) {
                HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator.copy(alpha = if (c.isDark) 0.55f else 0.7f))
            }
        }
    }
}

/**
 * Round back button for pages without a glass backdrop (Account, sign-in). Matches the
 * Liquid Glass back buttons elsewhere. [label] is used for accessibility only.
 */
@Composable
fun BackRow(label: String, onBack: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .statusBarsPadding()
            .padding(start = 14.dp, top = 5.dp)
            .size(44.dp)
            .bounceClick(pressedScale = 0.86f, onClick = onBack)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.ChevronLeft, label, tint = c.label, modifier = Modifier.size(30.dp))
    }
}

/** Recently Played: every device (YouTube Music history) or only this one. */
enum class RecentSource(val label: String) { All("All Devices"), Device("This Device") }

/** Downloaded list: everything, what you downloaded yourself, or what the Offline Mix did. */
enum class DownloadSource(val label: String) { All("All"), Mine("By You"), Auto("Automatic") }

/** Sorting for local song lists. */
enum class SongSort(val label: String) { Recent("Recent"), Title("Title"), Artist("Artist") }

/** Favorites / Downloaded / Recently Played, in the same style as the playlist pages. */
@Composable
fun SongListScreen(kind: String, onBack: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val favorites by connection.library.favorites.collectAsStateWithLifecycle()
    val recents by connection.library.recents.collectAsStateWithLifecycle()
    val downloadMap by connection.downloads.downloads.collectAsStateWithLifecycle()
    val autoIds by connection.downloads.autoIds.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf(DownloadSource.All) }
    // Recently Played: everywhere (your YouTube Music history) or just this device.
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()
    var recentSource by rememberSaveable { mutableStateOf(RecentSource.All) }
    val accountHistory by connection.accountHistory.collectAsStateWithLifecycle()
    val lastPlayed by connection.library.lastPlayed.collectAsStateWithLifecycle()
    if (kind == "recents" && loggedIn) {
        androidx.compose.runtime.LaunchedEffect(Unit) { connection.refreshAccountHistory() }
    }
    val allRecents = remember(recents, accountHistory, lastPlayed) {
        val remote = accountHistory
        if (remote == null) recents
        else {
            // Played here in the last few minutes may not be in YouTube's list yet: keep those on top.
            val fresh = System.currentTimeMillis() - 10 * 60_000L
            val top = remote.take(20).map { it.id }.toSet()
            (recents.filter { (lastPlayed[it.id] ?: 0L) > fresh && it.id !in top } + remote).distinctBy { it.id }
        }
    }
    val (title, songs) = when (kind) {
        "favorites" -> "Favorite Songs" to favorites
        "downloads" -> "Downloaded" to downloadMap.values.filter { d ->
            d.isDone && when (source) {
                DownloadSource.All -> true
                DownloadSource.Mine -> d.song.id !in autoIds
                DownloadSource.Auto -> d.song.id in autoIds
            }
        }.map { it.song }
        "offline" -> "Offline Mix" to (
            connection.playCache.cached.collectAsStateWithLifecycle().value.filter { downloadMap[it.id]?.isDone != true } +
                downloadMap.values.filter { it.isDone && it.song.id in autoIds }.map { it.song }
            ).distinctBy { it.id }
        else -> if (kind.startsWith("smart-")) {
            com.enes.liquidtube.data.SmartPlaylists.all(connection.library, downloadMap)
                .firstOrNull { it.id == kind }?.let { it.title to it.songs } ?: ("Smart Playlist" to emptyList())
        } else "Recently Played" to (if (loggedIn && recentSource == RecentSource.All) allRecents else recents)
    }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SongSort.Recent) }
    val shown = remember(songs, query, sort) {
        val q = query.trim()
        val list = if (q.isEmpty()) songs
        // Forgiving, like the main search: typos, accents, any word order; best match first.
        else com.enes.liquidtube.data.FuzzySearch.filter(songs, q) { listOf(it.title, it.artists, it.album) }
        when (sort) {
            SongSort.Recent -> list
            SongSort.Title -> list.sortedBy { it.title.lowercase() }
            SongSort.Artist -> list.sortedWith(compareBy({ it.artists.lowercase() }, { it.title.lowercase() }))
        }
    }
    val done = songs.count { downloadMap[it.id]?.isDone == true }
    val downloading = songs.any { downloadMap[it.id]?.isActive == true }
    val allDownloaded = songs.isNotEmpty() && done == songs.size

    val (coverIcon, coverGradient, coverText) = when (kind) {
        "favorites" -> Triple(Icons.Rounded.Star, PinRed, "Your favorites")
        "downloads" -> Triple(Icons.Rounded.DownloadDone, PinGreen, "On this device")
        "offline" -> Triple(Icons.Rounded.AutoAwesome, PinPurple, "Made for offline · Auto")
        else -> if (kind.startsWith("smart-")) Triple(
            Icons.Rounded.AutoAwesome, smartGradient(kind),
            com.enes.liquidtube.data.SmartPlaylists.all(connection.library, downloadMap).firstOrNull { it.id == kind }?.subtitle ?: "Smart playlist",
        ) else Triple(Icons.Rounded.History, PinBlue, "Your listening history")
    }
    ScreenScaffold(title, onBack = onBack, backdropUrl = songs.firstOrNull()?.thumbnailUrl, backdropGlow = true) {
        item(key = "cover") { ListCover(songs.art(), coverIcon, coverGradient, coverText) }
        if (kind == "recents" && loggedIn) {
            item(key = "recent-source") {
                FilterChips(
                    options = RecentSource.entries,
                    selected = recentSource,
                    label = { it.label },
                    onSelect = {
                        recentSource = it
                        if (it == RecentSource.All) connection.refreshAccountHistory()
                    },
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }
        }
        if (kind == "downloads" && autoIds.isNotEmpty()) {
            item(key = "source") {
                FilterChips(
                    options = DownloadSource.entries,
                    selected = source,
                    label = { it.label },
                    onSelect = { source = it },
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                )
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(
                    when (kind) {
                        "favorites" -> Icons.Rounded.Star
                        "downloads" -> Icons.Rounded.DownloadDone
                        "offline" -> Icons.Rounded.AutoAwesome
                        else -> Icons.Rounded.History
                    },
                    "Nothing here yet",
                    when (kind) {
                        "favorites" -> "Tap the star on the player to favorite a song."
                        "offline" -> "Songs you listen to are saved here as they play, plus a smart mix of your favorites, liked and most-played songs downloaded on Wi-Fi."
                        "downloads" -> "Download songs or whole albums from their ••• menu to listen offline."
                        else -> "Songs you play will show up here."
                    },
                )
            }
            return@ScreenScaffold
        }
        item(key = "actions") {
            com.enes.liquidtube.ui.collection.PlayActionRow(
                onPlay = { connection.play(shown) },
                left = com.enes.liquidtube.ui.collection.SideAction(Icons.Rounded.Shuffle, "Shuffle") { connection.shufflePlay(shown) },
                right = if (kind == "downloads" || kind == "offline") null else com.enes.liquidtube.ui.collection.SideAction(
                    if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.ArrowCircleDown,
                    when { allDownloaded -> "Downloaded"; downloading -> "Downloading"; else -> "Download All" },
                    enabled = !allDownloaded && !downloading,
                    active = allDownloaded || downloading,
                ) { connection.downloads.download(songs) },
            )
        }
        if (songs.size > 6) {
            item(key = "find") { FindField(query, "Find in $title") { query = it } }
        }
        if (songs.size > 1) {
            item(key = "sort") {
                FilterChips(
                    options = SongSort.entries,
                    selected = sort,
                    label = { it.label },
                    onSelect = { sort = it },
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
        }
        if (shown.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, "No matches", "Try another search.") }
        }
        itemsIndexed(shown, key = { _, s -> s.id }) { i, song ->
            SongRow(song, onClick = { connection.play(shown, i) }, showDivider = i != shown.lastIndex)
        }
        item(key = "count") {
            Text(
                if (songs.size == 1) "1 song" else "${songs.size} songs",
                style = AppTheme.type.footnote,
                color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = Gutter, vertical = 18.dp),
            )
        }
    }
}

// ------------------------------------------------ YouTube Music library grids

data class LibraryGridState(val items: List<CollectionItem> = emptyList(), val isLoading: Boolean = true, val error: Boolean = false)

@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    val kind: LibraryKind = LibraryKind.entries.firstOrNull { it.route == savedState.get<String>("kind") } ?: LibraryKind.Playlists
    private val _state = MutableStateFlow(LibraryGridState())
    val state: StateFlow<LibraryGridState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = LibraryGridState()
        viewModelScope.launch {
            repository.library(kind.browseId)
                .onSuccess { _state.value = LibraryGridState(items = it, isLoading = false) }
                .onFailure { _state.value = LibraryGridState(isLoading = false, error = true) }
        }
    }
}

@Composable
fun LibraryGridScreen(onBack: () -> Unit, viewModel: LibraryGridViewModel = hiltViewModel()) {
    val nav = LocalNavActions.current
    val overlay = LocalOverlay.current
    val connection = LocalPlayerConnection.current ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val kind = viewModel.kind
    val across = if (isLandscape()) 4 else 2
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    val account by connection.auth.account.collectAsStateWithLifecycle()
    val filters = LibraryFilter.forKind(kind)
    val shown = remember(state.items, query, filter, account) {
        val q = query.trim()
        val me = account?.name
        var list = if (q.isEmpty()) state.items
        else com.enes.liquidtube.data.FuzzySearch.filter(state.items, q) { listOf(it.title, it.subtitle) }
        list = when (filter) {
            // Your own playlists show your name first in the subtitle ("Estan · 59 tracks").
            LibraryFilter.ByYou -> list.filter { me != null && it.subtitle?.startsWith(me) == true }
            LibraryFilter.Saved -> list.filter { me == null || it.subtitle?.startsWith(me) != true }
            LibraryFilter.AtoZ -> list.sortedBy { it.title.lowercase() }
            LibraryFilter.All -> list
        }
        list
    }

    fun newPlaylist() = overlay.prompt(
        PromptSpec(
            title = "New Playlist",
            message = "It’s created in your YouTube Music library.",
            placeholder = "Playlist Name",
            confirm = "Create",
            onConfirm = { name ->
                connection.createPlaylist(name, emptyList()) { id ->
                    viewModel.load()
                    nav.openCollection("VL$id")
                }
            },
        )
    )

    fun removeAction(item: CollectionItem) = if (item.type != CollectionType.ARTIST) listOf(
        MenuAction("Remove from Library", Icons.Rounded.RemoveCircleOutline, destructive = true) {
            connection.savePlaylist(item.browseId, save = false)
            viewModel.load()
        },
    ) else emptyList()

    ScreenScaffold(kind.title, onBack = onBack, backdropUrl = state.items.firstOrNull()?.thumbnailUrl, backdropGlow = true) {
        if (state.items.size > 6) {
            item(key = "find") { FindField(query, "Find in ${kind.title}") { query = it } }
        }
        if (state.items.size > 1) {
            item(key = "filters") {
                FilterChips(
                    options = filters,
                    selected = filter,
                    label = { it.label },
                    onSelect = { filter = it },
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
        }
        when {
            state.isLoading -> item { LoadingBox() }
            state.error -> item {
                EmptyState(Icons.Rounded.WifiOff, "Couldn't load", "Check your connection and try again.",
                    action = "Try Again" to viewModel::load)
            }
            kind == LibraryKind.Albums -> {
                if (shown.isEmpty()) item { EmptyState(Icons.Rounded.Album, "No albums", "Albums you add on YouTube Music show up here.") }
                itemsIndexed(shown.chunked(across), key = { i, _ -> "row-$i" }) { row, pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 10.dp, bottom = 8.dp).appear(row),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        pair.forEach { item ->
                            CollectionCard(item, Modifier.weight(1f), menuExtra = removeAction(item)) { nav.openCollection(item.browseId) }
                        }
                        repeat(across - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            else -> {
                if (kind == LibraryKind.Playlists && query.isBlank() && filter != LibraryFilter.Saved) {
                    item(key = "new") { NewPlaylistRow(::newPlaylist) }
                }
                if (shown.isEmpty() && kind == LibraryKind.Artists) {
                    item { EmptyState(Icons.Rounded.MicExternalOn, "No artists", "Artists from songs in your library show up here.") }
                }
                if (shown.isEmpty() && kind == LibraryKind.Playlists && filter != LibraryFilter.All) {
                    item {
                        EmptyState(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            if (filter == LibraryFilter.ByYou) "No playlists by you" else "No saved playlists",
                            if (filter == LibraryFilter.ByYou) "Playlists you create show up here." else "Playlists you add from others show up here.",
                        )
                    }
                }
                itemsIndexed(shown, key = { _, it -> it.browseId }) { i, item ->
                    CollectionRow(
                        item,
                        showDivider = i != shown.lastIndex,
                        modifier = Modifier.appear(i.coerceAtMost(8)),
                        menuExtra = removeAction(item),
                    ) { nav.openCollection(item.browseId) }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressMenu(onClick = onClick, menu = null, highlight = true)
            .padding(start = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(7.dp)).background(if (c.isDark) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(74.dp), contentAlignment = Alignment.CenterStart) {
            Text("New Playlist…", style = AppTheme.type.callout.copy(fontSize = 17.sp), color = c.accent)
            HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator.copy(alpha = if (c.isDark) 0.55f else 0.7f))
        }
    }
}

/** iOS search-in-list field. */
@Composable
fun FindField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .padding(horizontal = Gutter, vertical = 6.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (c.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.055f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = AppTheme.type.body, color = c.secondaryLabel, maxLines = 1)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = AppTheme.type.body.copy(color = c.label),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel, "Clear",
                tint = c.tertiaryLabel,
                modifier = Modifier.size(20.dp).clickable { onChange("") },
            )
        }
    }
}

/** Filters for library lists: ownership for playlists, sorting for albums and artists. */
enum class LibraryFilter(val label: String) {
    All("All"), ByYou("By You"), Saved("Saved"), AtoZ("A–Z");

    companion object {
        fun forKind(kind: LibraryKind) =
            if (kind == LibraryKind.Playlists) listOf(All, ByYou, Saved, AtoZ) else listOf(All, AtoZ)
    }
}

/** "1 song", "12 songs". */
private fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"
