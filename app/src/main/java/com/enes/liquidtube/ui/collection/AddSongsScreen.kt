package com.enes.liquidtube.ui.collection

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.enes.liquidtube.data.LibraryStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.library.FindField
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddSongsState(
    val query: String = "",
    val results: List<SongItem>? = null,      // null while searching
    val suggestions: List<SongItem>? = null,  // null while loading
    val searchFailed: Boolean = false,
    /** Already in the playlist, or added from here. */
    val inPlaylist: Set<String> = emptySet(),
)

/**
 * Add Songs, for your own playlist: search YouTube Music and add with one tap. With an
 * empty search it shows suggestions, which re-plan as you add songs, so they follow the
 * direction the playlist is taking.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class AddSongsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: MusicRepository,
    private val library: LibraryStore,
    private val connection: PlayerConnection,
) : ViewModel() {
    val playlistId: String = savedState.get<String>("playlistId").orEmpty()
    val title: String = savedState.get<String>("title").orEmpty()

    private val _state = MutableStateFlow(AddSongsState())
    val state: StateFlow<AddSongsState> = _state.asStateFlow()

    /** The playlist's songs, oldest first, plus what's been added here (newest last). */
    private var songs: List<SongItem> = emptyList()
    private val queries = MutableStateFlow("")
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            songs = repository.browse("VL$playlistId").getOrNull()?.songs.orEmpty()
            _state.update { it.copy(inPlaylist = it.inPlaylist + songs.map { s -> s.id }) }
            suggest()
        }
        viewModelScope.launch {
            queries.debounce(350).distinctUntilChanged().collect { q -> search(q) }
        }
    }

    fun onQuery(q: String) {
        _state.update { it.copy(query = q) }
        queries.value = q
    }

    private suspend fun search(q: String) {
        if (q.isBlank()) {
            _state.update { it.copy(results = null, searchFailed = false) }
            return
        }
        _state.update { it.copy(results = null, searchFailed = false) }
        repository.searchSongs(q.trim())
            .onSuccess { r -> if (_state.value.query == q) _state.update { it.copy(results = r) } }
            .onFailure { if (_state.value.query == q) _state.update { it.copy(results = emptyList(), searchFailed = true) } }
    }

    fun add(song: SongItem) {
        if (song.id in _state.value.inPlaylist) return
        _state.update { it.copy(inPlaylist = it.inPlaylist + song.id) }
        songs = songs + song
        connection.addToPlaylist(playlistId, title, listOf(song), quiet = true)
        // Re-plan the suggestions around what was just added (after a short pause, so a
        // quick run of adds re-plans once).
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(900)
            suggest()
        }
    }

    private suspend fun suggest() {
        val list = repository.suggestForPlaylist(title, songs, library.recents.value)
        _state.update { st -> st.copy(suggestions = list.filterNot { it.id in st.inPlaylist }) }
    }
}

@Composable
fun AddSongsScreen(onBack: () -> Unit, viewModel: AddSongsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val searching = state.query.isNotBlank()

    ScreenScaffold("Add Songs", onBack = onBack) {
        item(key = "field") {
            FindField(state.query, "Search for songs") { viewModel.onQuery(it) }
        }
        if (!searching) {
            item(key = "suggest-title") {
                Text(
                    if (viewModel.title.isNotBlank()) "Suggested for “${viewModel.title}”" else "Suggested",
                    style = AppTheme.type.title3.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = c.label,
                    modifier = Modifier.padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 2.dp),
                )
            }
            item(key = "suggest-sub") {
                Text(
                    "These change as you add songs.",
                    style = AppTheme.type.footnote,
                    color = c.secondaryLabel,
                    modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 6.dp),
                )
            }
        }
        val list = if (searching) state.results else state.suggestions
        when {
            list == null -> item(key = "loading") { LoadingBox(Modifier.padding(vertical = 24.dp)) }
            searching && state.searchFailed -> item(key = "failed") {
                EmptyState(Icons.Rounded.WifiOff, "Couldn’t search", "Check your connection and try again.")
            }
            list.isEmpty() -> item(key = "none") {
                EmptyState(Icons.Rounded.Search, if (searching) "No songs found" else "No suggestions yet", if (searching) "Try another search." else "Search for a song to get started.")
            }
            else -> list.forEachIndexed { i, song ->
                item(key = (if (searching) "r-" else "s-") + song.id) {
                    val added = song.id in state.inPlaylist
                    SongRow(
                        song,
                        onClick = { viewModel.add(song) },
                        showDivider = i != list.lastIndex,
                        trailing = { AddCircle(added) { viewModel.add(song) } },
                    )
                }
            }
        }
    }
}
