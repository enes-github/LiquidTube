package com.enes.liquidtube.ui.collection

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import com.enes.liquidtube.ui.components.isLandscape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.CollectionShelf
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.GlassIconButton
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.TintedBackdrop
import com.enes.liquidtube.ui.glass.glassChip
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import com.enes.liquidtube.ui.components.InlineTitleBar
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.LocalOverlay
import com.enes.liquidtube.ui.components.MenuAction
import com.enes.liquidtube.ui.components.MenuSpec
import com.enes.liquidtube.ui.components.MixedShelf
import com.enes.liquidtube.ui.components.NavBarHeight
import com.enes.liquidtube.ui.components.PlayShuffleButtons
import com.enes.liquidtube.ui.components.PromptSpec
import com.enes.liquidtube.ui.components.QuickPicksShelf
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.components.SongShelf
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.menuButton
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.glassSource
import com.enes.liquidtube.ui.glass.rememberGlassBackdrop
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.BrowsePage
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionUiState(
    val page: BrowsePage? = null,
    val isLoading: Boolean = true,
    val error: Boolean = false,
    val loadingMore: Boolean = false,
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
    private val library: com.enes.liquidtube.data.LibraryStore,
    connection: com.enes.liquidtube.playback.PlayerConnection,
) : ViewModel() {
    val browseId: String = savedState.get<String>("browseId").orEmpty()
    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    /** Your own playlists: songs to add, following what's in it (null while loading). */
    private val _suggestions = MutableStateFlow<List<SongItem>?>(null)
    val suggestions: StateFlow<List<SongItem>?> = _suggestions.asStateFlow()
    private var suggestedFor: List<String>? = null

    init {
        load()
        // Songs added or removed anywhere (a song's menu, Add Songs, suggestions): reload.
        viewModelScope.launch {
            connection.playlistChanged.collect { id ->
                if (browseId.removePrefix("VL") != id) return@collect
                // Our own edits are on screen already; YouTube takes a moment to show them, so
                // wait for a pause in editing, then reload once, quietly.
                reloadJob?.cancel()
                reloadJob = viewModelScope.launch {
                    val sinceEdit = System.currentTimeMillis() - lastLocalEdit
                    if (sinceEdit < 3_000) kotlinx.coroutines.delay(3_000 - sinceEdit)
                    load()
                }
            }
        }
    }

    /** Recompute suggestions when the playlist's songs changed (or when asked to). */
    fun refreshSuggestions(force: Boolean = false) {
        val page = _state.value.page ?: return
        if (!page.editable) return
        val key = page.songs.map { it.id }
        if (!force && key == suggestedFor) return
        suggestedFor = key
        if (force) _suggestions.value = null
        viewModelScope.launch {
            val list = repository.suggestForPlaylist(page.title, page.songs, library.recents.value)
            _suggestions.value = if (force) list.shuffled() else list
        }
    }

    /** Hide a suggestion right away once it's been added. */
    fun dropSuggestion(id: String) {
        _suggestions.update { it?.filterNot { s -> s.id == id } }
    }

    /** Your playlist: show the song in it right away, then save (the reload reconciles). */
    fun addSong(playlistId: String, title: String, song: SongItem, connection: com.enes.liquidtube.playback.PlayerConnection) {
        lastLocalEdit = System.currentTimeMillis()
        synchronized(pendingAdds) { pendingAdds += song to lastLocalEdit }
        dropSuggestion(song.id)
        _state.update { st -> st.copy(page = st.page?.let { it.copy(songs = it.songs + song) }) }
        refreshSuggestions() // follow the new song now, not after YouTube answers
        connection.addToPlaylist(playlistId, title, listOf(song), quiet = true)
    }

    /** Your playlist: take the song out right away, then save. */
    fun removeSong(playlistId: String, song: SongItem, connection: com.enes.liquidtube.playback.PlayerConnection) {
        lastLocalEdit = System.currentTimeMillis()
        song.setVideoId?.let { synchronized(pendingRemoves) { pendingRemoves[it] = lastLocalEdit } }
        _state.update { st ->
            st.copy(page = st.page?.let { p -> p.copy(songs = p.songs.filterNot { it.setVideoId == song.setVideoId && it.id == song.id }) })
        }
        connection.removeFromPlaylist(playlistId, song) {}
        refreshSuggestions()
    }

    private var reloadJob: kotlinx.coroutines.Job? = null
    @Volatile private var lastLocalEdit = 0L
    /** Edits shown on screen that YouTube's copy may not include yet (with when they were made). */
    private val pendingAdds = ArrayList<Pair<SongItem, Long>>()
    private val pendingRemoves = HashMap<String, Long>()

    /** Lays our not-yet-visible edits over a fresh page, dropping each once YouTube shows it. */
    private fun withPending(page: BrowsePage): BrowsePage {
        val now = System.currentTimeMillis()
        var songs = page.songs
        synchronized(pendingRemoves) {
            pendingRemoves.entries.removeAll { (setId, at) -> now - at > 30_000 || songs.none { it.setVideoId == setId } }
            if (pendingRemoves.isNotEmpty()) songs = songs.filterNot { it.setVideoId in pendingRemoves }
        }
        synchronized(pendingAdds) {
            val counts = HashMap<String, Int>()
            songs.forEach { counts.merge(it.id, 1, Int::plus) }
            val iter = pendingAdds.iterator()
            val missing = ArrayList<SongItem>()
            while (iter.hasNext()) {
                val (song, at) = iter.next()
                val left = counts[song.id] ?: 0
                when {
                    left > 0 -> { counts[song.id] = left - 1; iter.remove() } // YouTube has it now
                    now - at > 30_000 -> iter.remove()                       // give up waiting
                    else -> missing += song
                }
            }
            songs = songs + missing
        }
        return page.copy(songs = songs)
    }

    fun load() {
        // Already showing the page (a reload after an edit): refresh it in place, quietly,
        // so nothing flashes or jumps.
        val shown = _state.value.page
        if (shown == null) _state.update { it.copy(isLoading = true, error = false) }
        viewModelScope.launch {
            repository.browse(browseId)
                .onSuccess { fresh ->
                    val page = withPending(if (shown != null) merge(shown, fresh) else fresh)
                    _state.update { CollectionUiState(page = page, isLoading = false) }
                    refreshSuggestions()
                    com.enes.liquidtube.data.ImagePrefetcher.prefetch(
                        page.songs.map { it.thumbnailUrl } + page.shelves.flatMap { sh -> sh.songs.map { it.thumbnailUrl } + sh.collections.map { it.thumbnailUrl } },
                    )
                    // Artist pages only ship 5 top songs; fetch the real list in the background
                    // so the Top Songs grid can page through ~20 of them, like Apple Music.
                    val more = page.songsMore
                    if (page.type == CollectionType.ARTIST && more != null) {
                        repository.section(more).onSuccess { section ->
                            val songs = section.items.filterIsInstance<SongItem>()
                            if (songs.size > page.songs.size) {
                                _state.update { st -> st.copy(page = st.page?.copy(songs = songs.take(20))) }
                            }
                        }
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, error = true) } }
        }
    }

    /**
     * A reload of a page already on screen: take the fresh details (positions for
     * removing, new artwork), but keep the songs already paged in beyond the first page.
     */
    private fun merge(shown: BrowsePage, fresh: BrowsePage): BrowsePage {
        if (fresh.continuation == null || fresh.songs.size >= shown.songs.size) return fresh
        val byId = fresh.songs.groupBy { it.id }.mapValues { it.value.toMutableList() }
        val kept = shown.songs.map { s -> byId[s.id]?.removeFirstOrNull() ?: s }
        return fresh.copy(songs = kept, continuation = shown.continuation)
    }

    fun loadMore() {
        val page = _state.value.page ?: return
        val token = page.continuation ?: return
        if (_state.value.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            val (more, next) = repository.browseMore(token).getOrDefault(emptyList<SongItem>() to null)
            _state.update {
                it.copy(
                    page = page.copy(songs = (page.songs + more).distinctBy { s -> s.id }, continuation = next),
                    loadingMore = false,
                )
            }
        }
    }
}

@Composable
fun CollectionScreen(onBack: () -> Unit, viewModel: CollectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.page
    val backdrop = rememberGlassBackdrop()
    val bg = AppTheme.colors.background
    val title = remember(viewModel.browseId) { TitleState() }
    val artist = page?.takeIf { it.type == CollectionType.ARTIST }?.let { rememberArtistState(it) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(backdrop, bg)) {
            when {
                page != null && artist != null -> ArtistPage(page, title, artist)
                page != null -> AlbumPage(page, title, viewModel::loadMore, viewModel::load, viewModel)
                state.isLoading -> LoadingBox(Modifier.statusBarsPadding().padding(top = 80.dp))
                else -> EmptyState(
                    Icons.Rounded.WifiOff, tr("Couldn't load", "Yüklenemedi"), tr("Check your connection and try again.", "Bağlantınızı kontrol edip tekrar deneyin."),
                    modifier = Modifier.statusBarsPadding().padding(top = 60.dp),
                    action = tr("Try Again", "Tekrar Dene") to viewModel::load,
                )
            }
        }
        // Collapsing title is drawn by each page; toolbar buttons float above everything.
        if (page != null) InlineTitleBar(
            page.title, visible = title.collapsed, backdrop = backdrop, scrolled = title.scrolled,
            // Pages sit on a tinted artwork backdrop; keep its colour under the bar.
            tint = AppTheme.colors.background.copy(alpha = 0.3f),
        )
        Toolbar(page, title, artist, backdrop, onBack, onReload = viewModel::load)
    }
}

/** Shared between the pages and the screen: whether the large title has scrolled away. */
internal class TitleState {
    var collapsed by mutableStateOf(false)
    /** Content has scrolled under the toolbar (shows the frosted edge). */
    var scrolled by mutableStateOf(false)
}


@Composable
private fun Toolbar(page: BrowsePage?, title: TitleState, artist: ArtistState?, backdrop: GlassBackdrop, onBack: () -> Unit, onReload: () -> Unit) {
    val overArtwork = page?.type == CollectionType.ARTIST && !title.scrolled
    val tint = if (overArtwork) Color.White else AppTheme.colors.label
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(backdrop, Icons.Rounded.ChevronLeft, tr("Back", "Geri"), onBack, tint = tint, iconSize = 30.dp)
        Spacer(Modifier.weight(1f))
        if (page != null && artist != null) {
            ArtistToolbarActions(page, artist, backdrop, tint)
        } else if (page != null) {
            PageActions(page, backdrop, tint, onChanged = onReload, onDeleted = onBack)
        }
    }
}

// --------------------------------------------------------- album / playlist

@Composable
private fun AlbumPage(page: BrowsePage, title: TitleState, onLoadMore: () -> Unit, onReload: () -> Unit, viewModel: CollectionViewModel) {
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val overlay = LocalOverlay.current
    // Your own playlist: add songs, edit (remove, rename), and suggestions that follow it.
    val ownId = page.playlistId?.takeIf { page.editable }
    var editing by rememberSaveable(page.browseId) { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val c = AppTheme.colors
    val isAlbum = page.type == CollectionType.ALBUM
    val listState = rememberLazyListState()
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    val barBottom = with(density) {
        (WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + NavBarHeight).toPx()
    }
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()
    val downloads by connection.downloads.downloads.collectAsStateWithLifecycle()
    var saved by remember(page.browseId, page.saved) { mutableStateOf(page.saved) }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(nearEnd, page.continuation) { if (nearEnd && page.continuation != null) onLoadMore() }
    // If the header is scrolled off entirely, the title is certainly gone (upright only: in
    // landscape the header stays in its side pane).
    val landscapeNow = isLandscape()
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { if (it > 0 && !landscapeNow) title.collapsed = true }
    }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4 }
            .collect { title.scrolled = it }
    }

    // Right-hand action: add to library for other people's albums/playlists, else download.
    val done = page.songs.count { downloads[it.id]?.isDone == true }
    val downloading = page.songs.any { downloads[it.id]?.isActive == true }
    val allDownloaded = page.songs.isNotEmpty() && done == page.songs.size
    // Your own auto playlists (Liked Music, Episodes for Later) are already yours: no "Add".
    val ownAuto = page.browseId.removePrefix("VL") in setOf("LM", "SE")
    val canSave = loggedIn && !page.editable && !ownAuto && (page.playlistId != null || page.type == CollectionType.ALBUM)
    val right = if (canSave) {
        SideAction(
            if (saved) Icons.Rounded.Check else Icons.Rounded.Add,
            if (saved) tr("Added to Library", "Kitaplığa Eklendi") else tr("Add to Library", "Kitaplığa Ekle"),
            active = saved,
        ) {
            saved = !saved
            connection.savePlaylist(page.playlistId ?: page.browseId, saved)
        }
    } else {
        SideAction(
            if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.ArrowCircleDown,
            when { allDownloaded -> tr("Downloaded", "İndirildi"); downloading -> tr("Downloading", "İndiriliyor"); else -> tr("Download", "İndir") },
            enabled = page.songs.isNotEmpty() && !allDownloaded && !downloading,
            active = allDownloaded || downloading,
        ) { connection.downloads.download(page.songs) }
    }

    // Cover, title, buttons and notes: on top of the list upright, in a side pane in landscape.
    val header: @Composable (Dp) -> Unit = { coverSize ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val size = coverSize
                        Box(
                            Modifier
                                .size(size)
                                .graphicsLayer {
                                    // The cover recedes a touch as it scrolls away.
                                    val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                    val s = 1f - (scrolled / 2400f).coerceIn(0f, 0.08f)
                                    scaleX = s
                                    scaleY = s
                                }
                                .shadow(34.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(0.35f), spotColor = Color.Black.copy(0.5f)),
                        ) {
                            Artwork(page.thumbnailUrl, Modifier.fillMaxSize(), corner = 14.dp)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Text(
                        page.title,
                        style = AppTheme.type.title1.copy(fontSize = 26.sp, lineHeight = 31.sp),
                        color = c.label,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = Gutter + 8.dp)
                            .onGloballyPositioned { coords ->
                                title.collapsed = coords.positionInRoot().y + coords.size.height < barBottom
                            },
                    )
                    page.strapline?.let { artist ->
                        Text(
                            artist,
                            style = AppTheme.type.title3.copy(fontWeight = FontWeight.Medium),
                            color = c.accent,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = Gutter, vertical = 3.dp)
                                .then(page.straplineId?.let { id -> Modifier.clickable { nav.openCollection(id) } } ?: Modifier),
                        )
                    }
                    page.subtitle?.let {
                        Text(
                            it.uppercase(),
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                            color = c.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Gutter, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    PlayActionRow(
                        onPlay = if (page.songs.isNotEmpty()) ({ connection.play(page.songs) }) else null,
                        left = SideAction(Icons.Rounded.Shuffle, tr("Shuffle", "Karıştır"), enabled = page.songs.isNotEmpty()) {
                            connection.shufflePlay(page.songs)
                        },
                        right = right,
                    )
                    if (ownId != null) {
                        Row(
                            Modifier.padding(horizontal = Gutter).padding(bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlaylistPill(Icons.Rounded.Add, tr("Add Songs", "Şarkı Ekle"), accent = true) { nav.openAddSongs(ownId, page.title) }
                            PlaylistPill(if (editing) Icons.Rounded.Check else Icons.Rounded.Edit, if (editing) tr("Done", "Bitti") else tr("Edit", "Düzenle"), active = editing) {
                                editing = !editing
                            }
                            if (editing) {
                                PlaylistPill(Icons.Rounded.Edit, tr("Rename", "Yeniden Adlandır")) {
                                    overlay.prompt(
                                        com.enes.liquidtube.ui.components.PromptSpec(
                                            title = tr("Rename Playlist", "Çalma Listesini Yeniden Adlandır"),
                                            placeholder = page.title,
                                            initial = page.title,
                                            confirm = tr("Rename", "Yeniden Adlandır"),
                                            onConfirm = { name -> connection.renamePlaylist(ownId, name, onReload) },
                                        )
                                    )
                                }
                            }
                        }
                    }
                    page.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Column(
                            Modifier
                                .padding(horizontal = Gutter)
                                .padding(bottom = 14.dp)
                                .fillMaxWidth()
                                .glassChip(tint = cardTint(c.isDark), corner = 20.dp, rim = if (c.isDark) 0.3f else 0.8f)
                                .clickable { descriptionExpanded = !descriptionExpanded }
                                .padding(16.dp),
                        ) {
                            Text(
                                desc,
                                style = AppTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 19.sp),
                                color = c.label.copy(alpha = 0.85f),
                                maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!descriptionExpanded && desc.length > 120) {
                                Spacer(Modifier.height(4.dp))
                                Text(tr("MORE", "DAHA FAZLA"), style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = c.label)
                            }
                        }
                    }
                }
    }
    val landscape = isLandscape()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        TintedBackdrop(page.thumbnailUrl) {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 2000f
        }
        val topBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + NavBarHeight
        val pane = if (landscape) (maxWidth * 0.4f).coerceIn(260.dp, 400.dp) else 0.dp
        val fullHeight = maxHeight
        if (landscape) {
            Column(
                Modifier
                    .width(pane)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topBar, bottom = LocalBottomInset.current, start = 8.dp),
            ) {
                header(minOf(pane - 90.dp, fullHeight - topBar - 200.dp).coerceAtLeast(130.dp))
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(start = pane),
            contentPadding = PaddingValues(top = if (landscape) topBar else 0.dp, bottom = LocalBottomInset.current),
        ) {
            if (!landscape) item(key = "header") {
                BoxWithConstraints(Modifier.fillMaxWidth().statusBarsPadding().padding(top = NavBarHeight + 8.dp)) {
                    header(maxWidth * 0.7f)
                }
            }

            if (page.songs.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.MusicNote,
                        if (page.editable) tr("No songs yet", "Henüz şarkı yok") else tr("Nothing to play", "Çalacak bir şey yok"),
                        if (page.editable) tr("Tap Add Songs to search for music, or pick from the suggestions below.", "Müzik aramak için Şarkı Ekle'ye dokunun veya aşağıdaki önerilerden seçin.")
                        else tr("This page doesn’t have any songs.", "Bu sayfada şarkı yok."),
                    )
                }
            }

            // Keys that survive reloads (a song's nth copy keeps its key), so rows animate
            // instead of being rebuilt when the playlist changes.
            val rowKeys = run {
                val seen = HashMap<String, Int>()
                page.songs.map { s -> val n = seen.merge(s.id, 1, Int::plus)!!; "${s.id}#$n" }
            }
            itemsIndexed(page.songs, key = { i, _ -> rowKeys[i] }) { i, song ->
                SongRow(
                    song,
                    modifier = Modifier.animateItem(fadeInSpec = tween(320), placementSpec = spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold), fadeOutSpec = tween(220)),
                    onClick = { connection.play(page.songs, i) },
                    showDivider = i != page.songs.lastIndex,
                    showDuration = isAlbum,
                    menuExtra = if (page.editable && page.playlistId != null && song.setVideoId != null) listOf(
                        MenuAction(tr("Remove from Playlist", "Çalma Listesinden Kaldır"), Icons.Rounded.RemoveCircleOutline, destructive = true) {
                            connection.removeFromPlaylist(page.playlistId!!, song, onReload)
                        },
                    ) else emptyList(),
                    leading = if (editing && ownId != null && song.setVideoId != null) {
                        {
                            // Edit mode: a red minus removes the song.
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .clickable { viewModel.removeSong(ownId, song, connection) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(24.dp).clip(CircleShape).background(Color(0xFFFF3B30)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size(width = 11.dp, height = 2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                                }
                            }
                        }
                    } else if (isAlbum) {
                        {
                            Text(
                                "${i + 1}",
                                style = AppTheme.type.callout,
                                color = c.secondaryLabel,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(26.dp),
                            )
                        }
                    } else null,
                )
            }

            (page.meta ?: summary(page.songs))?.let { meta ->
                item(key = "meta") {
                    Text(
                        meta,
                        style = AppTheme.type.footnote,
                        color = c.secondaryLabel,
                        modifier = Modifier.animateItem().padding(horizontal = Gutter, vertical = 18.dp),
                    )
                }
            }

            if (ownId != null) {
                item(key = "suggest-header") {
                    Row(
                        Modifier.animateItem().fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Suggested Songs", "Önerilen Şarkılar"), style = AppTheme.type.title3.copy(fontWeight = FontWeight.Bold), color = c.label)
                            Text(
                                if (page.songs.isEmpty()) tr("Based on the name and what you listen to", "Ada ve dinlediklerinize göre") else tr("Based on what you’ve added", "Eklediklerinize göre"),
                                style = AppTheme.type.footnote, color = c.secondaryLabel,
                            )
                        }
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable { viewModel.refreshSuggestions(force = true) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Refresh, tr("More Suggestions", "Daha Fazla Öneri"), tint = c.accent, modifier = Modifier.size(22.dp)) }
                    }
                }
                val list = suggestions
                if (list == null) {
                    item(key = "suggest-loading") { LoadingBox(Modifier.padding(vertical = 20.dp)) }
                } else {
                    val shown = list.take(10)
                    itemsIndexed(shown, key = { _, s -> "sg-${s.id}" }) { i, song ->
                        SongRow(
                            song,
                            modifier = Modifier.animateItem(fadeInSpec = tween(360, delayMillis = 120), placementSpec = spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = IntOffset.VisibilityThreshold), fadeOutSpec = tween(200)),
                            onClick = { connection.play(shown, i) },
                            showDivider = i != shown.lastIndex,
                            trailing = {
                                AddCircle {
                                    val newIndex = page.songs.size
                                    viewModel.addSong(ownId, page.title, song, connection)
                                    // Glide to the song just added, with the suggestions right under it.
                                    scope.launch {
                                        kotlinx.coroutines.delay(260)
                                        val target = newIndex + if (landscape) 0 else 1
                                        val third = listState.layoutInfo.viewportSize.height / 3
                                        listState.animateScrollToItem(target, -third)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            page.shelves.forEachIndexed { i, shelf ->
                item(key = "shelf-$i") {
                    if (shelf.collections.isNotEmpty()) CollectionShelf(shelf.title, shelf.collections)
                    else SongShelf(shelf.title, shelf.songs, { songs, idx -> connection.play(songs, idx) })
                }
            }
        }
    }
}

/** A small glass button under your playlist's play buttons. */
@Composable
private fun PlaylistPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier
            .height(38.dp)
            .bounceClick(pressedScale = 0.94f, onClick = onClick)
            .glassChip(tint = if (active) c.accent.copy(alpha = 0.9f) else cardTint(c.isDark), corner = 19.dp, rim = if (c.isDark) 0.3f else 0.8f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = when { active -> Color.White; accent -> c.accent; else -> c.label }
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = tint)
    }
}

/** Round "+" that adds a song to your playlist (a check once added). */
@Composable
internal fun AddCircle(added: Boolean = false, onAdd: () -> Unit) {
    val c = AppTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(if (added) Modifier else Modifier.clickable(onClick = onAdd)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (added) c.fill else c.accent.copy(alpha = if (c.isDark) 0.22f else 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                if (added) tr("Added", "Eklendi") else tr("Add", "Ekle"),
                tint = if (added) c.secondaryLabel else c.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** "24 songs, 1 hour 32 minutes", from the rows' durations. */
private fun summary(songs: List<SongItem>): String? {
    if (songs.isEmpty()) return null
    var seconds = 0
    for (s in songs) {
        val parts = s.durationText?.split(":")?.mapNotNull { it.trim().toIntOrNull() } ?: continue
        seconds += parts.fold(0) { acc, v -> acc * 60 + v }
    }
    val count = if (songs.size == 1) "1 song" else "${songs.size} songs"
    if (seconds <= 0) return count
    val h = seconds / 3600
    val m = (seconds % 3600 + 30) / 60
    val length = when {
        h > 0 && m > 0 -> "$h hour${if (h > 1) "s" else ""} $m minute${if (m != 1) "s" else ""}"
        h > 0 -> "$h hour${if (h > 1) "s" else ""}"
        else -> "$m minute${if (m != 1) "s" else ""}"
    }
    return "$count, $length"
}

// ------------------------------------------------------------------ toolbar

/** Top-right glass capsule on album/playlist pages: Share and ••• (library, editing). */
@Composable
private fun PageActions(page: BrowsePage, backdrop: GlassBackdrop, tint: Color, onChanged: () -> Unit, onDeleted: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val overlay = LocalOverlay.current
    val nav = LocalNavActions.current
    val loggedIn by connection.auth.isLoggedIn.collectAsStateWithLifecycle()

    fun spec(): MenuSpec {
        val playlistId = page.playlistId ?: page.browseId.takeIf { page.type == CollectionType.ALBUM }
        val quick = buildList {
            if (page.songs.isNotEmpty()) {
                add(MenuAction(tr("Play", "Çal"), Icons.Rounded.PlayArrow) { connection.play(page.songs) })
                add(MenuAction(tr("Shuffle", "Karıştır"), Icons.Rounded.Shuffle) { connection.shufflePlay(page.songs) })
                add(MenuAction(tr("Play Last", "Sona Ekle"), Icons.AutoMirrored.Rounded.PlaylistAdd) { page.songs.forEach(connection::addToQueue) })
            }
            add(MenuAction(tr("Share", "Paylaş"), Icons.Rounded.Share) {
                val url = if (page.type == CollectionType.ALBUM) "https://music.youtube.com/browse/${page.browseId}"
                else "https://music.youtube.com/playlist?list=${page.browseId.removePrefix("VL")}"
                connection.share(page.title, url)
            })
        }
        val library = buildList {
            if (loggedIn && playlistId != null && !page.editable && page.browseId.removePrefix("VL") !in setOf("LM", "SE")) {
                add(MenuAction(tr("Add to Library", "Kitaplığa Ekle"), Icons.Rounded.LibraryAdd) { connection.savePlaylist(playlistId, true) })
                add(MenuAction(tr("Remove from Library", "Kitaplıktan Kaldır"), Icons.Rounded.RemoveCircleOutline) { connection.savePlaylist(playlistId, false) })
            }
            if (loggedIn && page.songs.isNotEmpty()) {
                add(MenuAction(tr("Add All to a Playlist…", "Tümünü Çalma Listesine Ekle…"), Icons.AutoMirrored.Rounded.PlaylistAdd) { overlay.addToPlaylist(page.songs) })
            }
            if (page.songs.isNotEmpty()) {
                add(MenuAction(tr("Download", "İndir"), Icons.Rounded.ArrowCircleDown) { connection.downloads.download(page.songs) })
            }
        }
        val edit = buildList {
            if (page.editable && playlistId != null) {
                add(MenuAction(tr("Rename Playlist", "Çalma Listesini Yeniden Adlandır"), Icons.Rounded.Edit) {
                    overlay.prompt(
                        PromptSpec(
                            title = tr("Rename Playlist", "Çalma Listesini Yeniden Adlandır"),
                            initial = page.title,
                            placeholder = tr("Playlist Name", "Çalma Listesi Adı"),
                            confirm = tr("Save", "Kaydet"),
                            onConfirm = { name -> connection.renamePlaylist(playlistId, name, onChanged) },
                        )
                    )
                })
                add(MenuAction(tr("Delete Playlist", "Çalma Listesini Sil"), Icons.Rounded.Delete, destructive = true) {
                    overlay.prompt(
                        PromptSpec(
                            title = "Delete “${page.title}”?",
                            message = tr("Type DELETE to confirm. This removes it from your YouTube Music account.", "Onaylamak için DELETE yazın. Bu, listeyi YouTube Music hesabınızdan kaldırır."),
                            placeholder = "DELETE",
                            confirm = tr("Delete", "Sil"),
                            destructive = true,
                            onConfirm = { typed -> if (typed.equals("delete", true)) connection.deletePlaylist(playlistId, onDeleted) },
                        )
                    )
                })
            }
        }
        val go = buildList {
            page.straplineId?.let { id ->
                add(MenuAction(tr("Go to Artist", "Sanatçıya Git"), Icons.Rounded.Person) { nav.openCollection(id) })
            }
        }
        return MenuSpec(
            title = page.title,
            subtitle = page.strapline ?: page.subtitle,
            artworkUrl = page.thumbnailUrl,
            quick = quick,
            groups = listOf(library, edit, go).filter { it.isNotEmpty() },
        )
    }

    val shareUrl = if (page.type == CollectionType.ALBUM) "https://music.youtube.com/browse/${page.browseId}"
    else "https://music.youtube.com/playlist?list=${page.browseId.removePrefix("VL")}"
    GlassActionsCapsule(backdrop, tint, onShare = { connection.share(page.title, shareUrl) }, menu = { spec() })
}
