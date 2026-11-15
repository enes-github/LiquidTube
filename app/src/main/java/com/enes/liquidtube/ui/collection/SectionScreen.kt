package com.enes.liquidtube.ui.collection

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.enes.liquidtube.ui.components.FilterChips
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.SectionChip
import com.enes.innertube.models.SectionRef
import com.enes.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionUiState(
    val items: List<Any> = emptyList(),
    val chips: List<SectionChip> = emptyList(),
    val continuation: String? = null,
    val isLoading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
)

@HiltViewModel
class SectionViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    val title: String = savedState.get<String>("title").orEmpty()
    private var ref = SectionRef(
        savedState.get<String>("browseId").orEmpty(),
        savedState.get<String>("params")?.takeIf { it.isNotBlank() },
    )
    private val _state = MutableStateFlow(SectionUiState())
    val state: StateFlow<SectionUiState> = _state.asStateFlow()

    init { load() }

    /** Switch to a server chip ("Singles & EPs"); keeps the chip row while loading. */
    fun select(chip: SectionChip) {
        if (chip.ref == ref) return
        ref = chip.ref
        _state.update { it.copy(chips = it.chips.map { c -> c.copy(selected = c.ref == chip.ref) }) }
        load(keepChips = true)
    }

    fun load(keepChips: Boolean = false) {
        _state.update { it.copy(isLoading = true, error = false, items = emptyList(), continuation = null) }
        viewModelScope.launch {
            repository.section(ref)
                .onSuccess { page ->
                    com.enes.liquidtube.data.ImagePrefetcher.prefetch(
                        page.items.map { (it as? SongItem)?.thumbnailUrl ?: (it as? CollectionItem)?.thumbnailUrl },
                    )
                    _state.update {
                        it.copy(
                            items = page.items,
                            chips = if (keepChips && it.chips.isNotEmpty()) it.chips else page.chips,
                            continuation = page.continuation,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(isLoading = false, error = true) } }
        }
    }

    fun loadMore() {
        val s = _state.value
        val token = s.continuation ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.sectionMore(token)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            items = (it.items + page.items).distinctBy { x -> (x as? SongItem)?.id ?: (x as CollectionItem).browseId },
                            continuation = page.continuation,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false, continuation = null) } }
        }
    }
}

/** Client-side sorting for a section. */
private enum class SectionSort(val label: String) {
    Popular("Popular"), Newest("Newest"), Oldest("Oldest"), AtoZ("A–Z");

    /** Shown on the chip, in the app's language. */
    val shown: String get() = when (this) {
        Popular -> tr("Popular", "Popüler"); Newest -> tr("Newest", "En Yeni"); Oldest -> tr("Oldest", "En Eski"); AtoZ -> "A–Z"
    }
}

private val YEAR = Regex("""\b(19|20)\d{2}\b""")
private fun CollectionItem.year() = subtitle?.let { YEAR.find(it)?.value?.toIntOrNull() } ?: 0

/**
 * A full artist section (all top songs, the whole discography, every video…), in the
 * library-page style, with YouTube's own type chips plus sorting and find.
 */
@Composable
fun SectionScreen(onBack: () -> Unit, viewModel: SectionViewModel = hiltViewModel()) {
    val gridAcross = if (com.enes.liquidtube.ui.components.isLandscape()) 4 else 2
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var sort by rememberSaveable { mutableStateOf(SectionSort.Popular) }

    val songs = state.items.filterIsInstance<SongItem>()
    val collections = state.items.filterIsInstance<CollectionItem>()
    val isSongs = songs.size >= collections.size
    val sorts = if (isSongs) listOf(SectionSort.Popular, SectionSort.AtoZ)
    else listOf(SectionSort.Popular, SectionSort.Newest, SectionSort.Oldest, SectionSort.AtoZ)

    val shownSongs = remember(songs, sort) {
        if (sort == SectionSort.AtoZ) songs.sortedBy { it.title.lowercase() } else songs
    }
    val shownCollections = remember(collections, sort) {
        when (sort) {
            SectionSort.Newest -> collections.sortedByDescending { it.year() }
            SectionSort.Oldest -> collections.sortedBy { it.year().takeIf { y -> y > 0 } ?: Int.MAX_VALUE }
            SectionSort.AtoZ -> collections.sortedBy { it.title.lowercase() }
            SectionSort.Popular -> collections
        }
    }

    // Load the next page as the end comes into view (sorting only reorders what's loaded).
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, state.continuation) { if (nearEnd && state.continuation != null) viewModel.loadMore() }

    val backdrop = (songs.firstOrNull()?.thumbnailUrl ?: collections.firstOrNull()?.thumbnailUrl)
    ScreenScaffold(viewModel.title, onBack = onBack, state = listState, backdropUrl = backdrop, backdropGlow = true) {
        if (state.chips.size > 1) {
            item(key = "type") {
                FilterChips(
                    options = state.chips,
                    selected = state.chips.firstOrNull { it.selected } ?: state.chips.first(),
                    label = { it.title },
                    onSelect = viewModel::select,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
        }
        if (isSongs && songs.isNotEmpty()) {
            item(key = "actions") {
                PlayActionRow(
                    onPlay = { connection.play(shownSongs) },
                    left = SideAction(Icons.Rounded.Shuffle, tr("Shuffle", "Karıştır")) { connection.shufflePlay(shownSongs) },
                    right = SideAction(Icons.AutoMirrored.Rounded.PlaylistAdd, tr("Add All to Queue", "Tümünü Sıraya Ekle")) {
                        shownSongs.forEach(connection::addToQueue)
                    },
                )
            }
        }
        if (state.items.size > 1) {
            item(key = "sort") {
                FilterChips(
                    options = sorts,
                    selected = if (sort in sorts) sort else SectionSort.Popular,
                    label = { it.shown },
                    onSelect = { sort = it },
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }
        }

        when {
            state.isLoading -> item { LoadingBox() }
            state.error -> item {
                EmptyState(Icons.Rounded.WifiOff, tr("Couldn't load", "Yüklenemedi"), tr("Check your connection and try again.", "Bağlantınızı kontrol edip tekrar deneyin."),
                    action = tr("Try Again", "Tekrar Dene") to { viewModel.load() })
            }
            state.items.isEmpty() -> item { EmptyState(Icons.Rounded.LibraryMusic, tr("Nothing here", "Burada bir şey yok"), tr("This section is empty.", "Bu bölüm boş.")) }
            isSongs -> {
                itemsIndexed(shownSongs, key = { _, s -> s.id }) { i, song ->
                    SongRow(
                        song,
                        onClick = { connection.play(shownSongs, i) },
                        showDivider = i != shownSongs.lastIndex,
                        leading = if (sort == SectionSort.Popular) {
                            {
                                Text(
                                    "${i + 1}",
                                    style = AppTheme.type.callout,
                                    color = AppTheme.colors.secondaryLabel,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.width(28.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
            else -> {
                itemsIndexed(shownCollections.chunked(gridAcross), key = { i, _ -> "row-$i" }) { row, pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 8.dp, bottom = 10.dp).appear(row.coerceAtMost(6)),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        pair.forEach { item ->
                            CollectionCard(item, Modifier.weight(1f)) { nav.openCollection(item.browseId) }
                        }
                        repeat(gridAcross - pair.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (state.loadingMore) item(key = "more") { LoadingBox(Modifier.padding(vertical = 0.dp)) }
    }
}
