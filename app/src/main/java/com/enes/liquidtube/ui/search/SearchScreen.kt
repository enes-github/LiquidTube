package com.enes.liquidtube.ui.search

import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import com.enes.liquidtube.ui.components.FilterChips
import com.enes.liquidtube.ui.components.CollectionRow
import com.enes.innertube.models.SearchFilter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LargeTitle
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.pressMenu
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.CollectionShelf
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.SearchResults
import com.enes.innertube.models.SongItem
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.appear
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import com.enes.liquidtube.ui.glass.glassChip

private data class Category(val name: String, val query: String, val colors: List<Color>)

private val categories = listOf(
    Category("Pop", "pop hits", listOf(Color(0xFFFF2D92), Color(0xFFFF6B6B))),
    Category("Hip-Hop", "hip hop hits", listOf(Color(0xFF6A3093), Color(0xFFA044FF))),
    Category("Chill", "chill vibes", listOf(Color(0xFF2193B0), Color(0xFF6DD5ED))),
    Category("Rock", "rock hits", listOf(Color(0xFF373B44), Color(0xFF4286F4))),
    Category("Dance", "dance hits", listOf(Color(0xFFF953C6), Color(0xFFB91D73))),
    Category("R&B", "rnb hits", listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))),
    Category("Workout", "workout hits", listOf(Color(0xFFED213A), Color(0xFF93291E))),
    Category("Focus", "focus music", listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    Category("Indie", "indie hits", listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
    Category("Jazz", "jazz essentials", listOf(Color(0xFF1D2B64), Color(0xFFF8CDDA))),
    Category("Turkish", "türkçe pop hits", listOf(Color(0xFFE52D27), Color(0xFFB31217))),
    Category("Classical", "classical essentials", listOf(Color(0xFF603813), Color(0xFFB29F94))),
)

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val gridAcross = if (com.enes.liquidtube.ui.components.isLandscape()) 4 else 2
    val connection = LocalPlayerConnection.current ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val showCategories = state.query.isBlank()
    val nav = LocalNavActions.current
    // The field owns its text (synchronously): routing every keystroke through the view
    // model's flow and back can drop fast keystrokes. Outside changes (a category, a
    // recent search, the clear button) still flow in.
    var fieldText by rememberSaveable { mutableStateOf(state.query) }
    // What the field itself sent recently: those values coming back (possibly late) are
    // echoes, not outside changes, and must not overwrite newer typing.
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(state.query) {
        if (state.query != fieldText && state.query !in sent) {
            fieldText = state.query
            sent.clear()
        }
    }

    val history by viewModel.history.collectAsStateWithLifecycle()
    val c = AppTheme.colors

    // Your own music matching the search, by name or by a line from its lyrics; works offline.
    val favorites by connection.library.favorites.collectAsStateWithLifecycle()
    val recents by connection.library.recents.collectAsStateWithLifecycle()
    val downloadMap by connection.downloads.downloads.collectAsStateWithLifecycle()
    val cachedSongs by connection.playCache.cached.collectAsStateWithLifecycle()
    val libraryHits by androidx.compose.runtime.produceState<List<LibraryHit>>(emptyList(), fieldText) {
        val q = fieldText.trim()
        if (q.length < 2) { value = emptyList(); return@produceState }
        kotlinx.coroutines.delay(250)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val pool = (favorites + downloadMap.values.filter { it.isDone }.map { it.song } + cachedSongs + recents +
                connection.library.frequent.value).distinctBy { it.id }
            // The same song saved under two ids (a re-upload, a video version) shows once.
            fun same(x: com.enes.innertube.models.SongItem) =
                com.enes.liquidtube.data.FuzzySearch.normalize(x.title.substringBefore(" (")) + "|" +
                    com.enes.liquidtube.data.FuzzySearch.normalize(x.artists.substringBefore(" &").substringBefore(","))
            val byName = com.enes.liquidtube.data.FuzzySearch.filter(pool, q) { listOf(it.title, it.artists) }
                .distinctBy(::same).take(3)
            val taken = byName.map(::same).toSet()
            val byLyrics = com.enes.liquidtube.data.LyricsStore.search(q, limit = 12)
                .filterNot { same(it.song) in taken }.distinctBy { same(it.song) }.take(4)
            byName.map { LibraryHit(it, null) } + byLyrics.map { LibraryHit(it.song, it.line) }
        }
    }

    val listState = rememberLazyListState()
    val filtering = state.filter != SearchFilter.All
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.continuation, state.filter) {
        if (nearEnd && filtering && state.continuation != null) viewModel.loadMore()
    }
    val noResults = !state.isLoading && state.query.trim().length >= 2 &&
        (if (filtering) state.filtered.isEmpty() else state.results.isEmpty)

    ScreenScaffold("Search", state = listState) {
        item(key = "field") {
            SearchField(
                value = fieldText,
                onValueChange = {
                    fieldText = it
                    sent.addLast(it)
                    while (sent.size > 64) sent.removeFirst()
                    viewModel.onQueryChange(it)
                },
                onSearch = {
                    keyboard?.hide()
                    viewModel.searchNow()
                },
                modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
            )
        }
        if (!showCategories) {
            item(key = "filters") {
                FilterChips(
                    options = SearchFilter.entries,
                    selected = state.filter,
                    label = { it.label },
                    onSelect = viewModel::setFilter,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
            }
        }

        if (!showCategories && state.filter == SearchFilter.All && libraryHits.isNotEmpty()) {
            item(key = "lib-header") { SectionHeader("In Your Library") }
            itemsIndexed(libraryHits, key = { _, h -> "lib-${h.song.id}" }) { i, hit ->
                LibraryHitRow(hit, showDivider = i != libraryHits.lastIndex) {
                    keyboard?.hide()
                    connection.play(libraryHits.map { it.song }, i)
                }
            }
        }

        when {
            showCategories -> {
                if (history.isNotEmpty()) {
                    item(key = "recent-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 6.dp, top = 22.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Recently Searched", style = AppTheme.type.title3, color = c.label, modifier = Modifier.weight(1f))
                            Text(
                                "Clear",
                                style = AppTheme.type.subhead,
                                color = c.accent,
                                modifier = Modifier.clickable { viewModel.clearHistory() }.padding(6.dp),
                            )
                        }
                    }
                    itemsIndexed(history.take(6), key = { _, q -> "h-$q" }) { i, q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressMenu(onClick = { keyboard?.hide(); viewModel.searchNow(q) }, menu = null, highlight = true)
                                .padding(start = Gutter)
                                .appear(i),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.CenterStart) {
                                Text(q, style = AppTheme.type.body, color = c.label, maxLines = 1)
                                HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator)
                            }
                        }
                    }
                }
                item { SectionHeader("Browse Categories") }
                itemsIndexed(categories.chunked(gridAcross)) { row, pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp).appear(row),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        pair.forEach { cat ->
                            CategoryTile(cat, Modifier.weight(1f)) {
                                keyboard?.hide()
                                viewModel.searchNow(cat.query)
                            }
                        }
                    }
                }
            }
            state.isLoading && (filtering || state.results.isEmpty) -> item { LoadingBox() }
            state.error != null -> item {
                EmptyState(
                    Icons.Rounded.WifiOff, "Couldn't search",
                    state.error ?: "", action = "Try Again" to { viewModel.searchNow() },
                )
            }
            noResults -> item {
                EmptyState(Icons.Rounded.SearchOff, "No Results", "Try a new search or another filter.")
            }
            filtering -> filteredResults(
                state.filtered,
                loadingMore = state.loadingMore,
                onSong = { songs, i -> keyboard?.hide(); connection.play(songs, i) },
                onCollection = { keyboard?.hide(); nav.openCollection(it.browseId) },
            )
            else -> searchResults(state.results, onSong = {
                keyboard?.hide()
                connection.playSong(it)
            }, onCollection = {
                keyboard?.hide()
                nav.openCollection(it.browseId)
            }, onSeeAll = viewModel::setFilter)
        }
    }
}

/** One filter's results as a list: song rows and collection rows, paged. */
private fun LazyListScope.filteredResults(
    items: List<Any>,
    loadingMore: Boolean,
    onSong: (List<SongItem>, Int) -> Unit,
    onCollection: (CollectionItem) -> Unit,
) {
    val songs = items.filterIsInstance<SongItem>()
    itemsIndexed(items, key = { _, x -> (x as? SongItem)?.let { "s-${it.id}" } ?: "c-${(x as CollectionItem).browseId}" }) { i, x ->
        val last = i == items.lastIndex
        when (x) {
            is SongItem -> SongRow(x, onClick = { onSong(songs, songs.indexOf(x)) }, showDivider = !last)
            is CollectionItem -> CollectionRow(x, showDivider = !last) { onCollection(x) }
        }
    }
    if (loadingMore) item(key = "more") { LoadingBox(Modifier.padding(vertical = 0.dp)) }
}

private fun LazyListScope.searchResults(
    r: SearchResults,
    onSong: (SongItem) -> Unit,
    onCollection: (CollectionItem) -> Unit,
    onSeeAll: (SearchFilter) -> Unit,
) {
    if (r.topSong != null || r.topCollection != null) {
        item { SectionHeader("Top Result") }
        item {
            r.topCollection?.let { TopCollection(it) { onCollection(it) } }
                ?: r.topSong?.let { SongRow(it, onClick = { onSong(it) }, showDivider = false) }
        }
    }
    if (r.songs.isNotEmpty()) {
        item { SectionHeader("Songs", onClick = { onSeeAll(SearchFilter.Songs) }) }
        val songs = r.songs.take(8)
        itemsIndexed(songs, key = { _, s -> "song-${s.id}" }) { i, song ->
            SongRow(song, onClick = { onSong(song) }, showDivider = i != songs.lastIndex)
        }
    }
    if (r.artists.isNotEmpty()) item { CollectionShelf("Artists", r.artists, onHeaderClick = { onSeeAll(SearchFilter.Artists) }) }
    if (r.albums.isNotEmpty()) item { CollectionShelf("Albums", r.albums, onHeaderClick = { onSeeAll(SearchFilter.Albums) }) }
    if (r.playlists.isNotEmpty()) item { CollectionShelf("Playlists", r.playlists, onHeaderClick = { onSeeAll(SearchFilter.Playlists) }) }
}

@Composable
private fun TopCollection(item: CollectionItem, onClick: () -> Unit) {
    val c = AppTheme.colors
    val menus = com.enes.liquidtube.ui.components.LocalMenus.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .then(Modifier.pressMenu(onClick = onClick, menu = { menus?.collection(item) }, pressedScale = 0.98f))
            .clip(RoundedCornerShape(12.dp))
            .background(c.elevated)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            item.thumbnailUrl,
            Modifier.size(84.dp),
            corner = if (item.type == CollectionType.ARTIST) 42.dp else 8.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = AppTheme.type.title3, color = c.label, maxLines = 2)
            Text(
                listOfNotNull(
                    item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    item.subtitle,
                ).joinToString(" · "),
                style = AppTheme.type.footnote,
                color = c.secondaryLabel,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(if (c.isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.055f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    "Artists, Songs, Lyrics, and More",
                    style = AppTheme.type.body,
                    color = c.secondaryLabel,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppTheme.type.body.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel, "Clear",
                tint = c.secondaryLabel,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

@Composable
private fun CategoryTile(category: Category, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1.6f)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(category.colors)),
    ) {
        Text(
            category.name,
            style = AppTheme.type.headline,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

/** A song from your own library matching the search; [line] = the lyric that matched. */
private data class LibraryHit(val song: com.enes.innertube.models.SongItem, val line: String?)

@Composable
private fun LibraryHitRow(hit: LibraryHit, showDivider: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val menus = com.enes.liquidtube.ui.components.LocalMenus.current
    Row(
        Modifier
            .fillMaxWidth()
            .pressMenu(onClick = onClick, menu = { menus?.song(hit.song) }, highlight = true)
            .padding(start = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.enes.liquidtube.ui.components.Artwork(hit.song.thumbnailUrl, Modifier.size(46.dp), corner = 6.dp, small = true)
        Spacer(Modifier.width(13.dp))
        Box(Modifier.weight(1f).height(if (hit.line != null) 70.dp else 60.dp), contentAlignment = Alignment.CenterStart) {
            Column(Modifier.padding(end = Gutter)) {
                Text(hit.song.title, style = AppTheme.type.callout, color = c.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(hit.song.artists, style = AppTheme.type.subhead.copy(fontSize = 14.sp), color = c.secondaryLabel, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                hit.line?.let {
                    Text(
                        "\u201C$it\u201D",
                        style = AppTheme.type.footnote.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = c.accent,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (showDivider) HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator)
        }
    }
}
