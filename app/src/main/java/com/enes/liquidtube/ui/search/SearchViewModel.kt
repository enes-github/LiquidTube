package com.enes.liquidtube.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enes.liquidtube.data.MusicRepository
import com.enes.innertube.models.SearchFilter
import com.enes.innertube.models.SearchResults
import kotlinx.coroutines.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val filter: SearchFilter = SearchFilter.All,
    /** Results for a specific filter (songs/collections in order), paged. */
    val filtered: List<Any> = emptyList(),
    val continuation: String? = null,
    val loadingMore: Boolean = false,
)

/** Search-as-you-type with a short debounce, like the iOS app. */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("search", android.content.Context.MODE_PRIVATE)
    private val _history = MutableStateFlow(
        prefs.getString("history", null)?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
    )
    /** Recently searched queries, newest first. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun remember(q: String) {
        _history.value = (listOf(q) + _history.value.filterNot { it.equals(q, ignoreCase = true) }).take(10)
        prefs.edit().putString("history", _history.value.joinToString("\n")).apply()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.edit().remove("history").apply()
    }

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queries
                .debounce(350)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { q ->
                    if (q.length < 2) {
                        _state.update { it.copy(results = SearchResults(), isLoading = false, error = null) }
                    } else {
                        run(q)
                    }
                }
        }
    }

    private var job: Job? = null

    fun setFilter(filter: SearchFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter, filtered = emptyList(), continuation = null) }
        val q = _state.value.query.trim()
        if (q.length >= 2) {
            job?.cancel()
            job = viewModelScope.launch { run(q) }
        }
    }

    fun loadMore() {
        val s = _state.value
        val token = s.continuation ?: return
        if (s.loadingMore || s.filter == SearchFilter.All) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repository.searchMore(token)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            filtered = (it.filtered + page.items).distinctBy { x -> key(x) },
                            continuation = page.continuation,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(loadingMore = false, continuation = null) } }
        }
    }

    private fun key(x: Any) = (x as? com.enes.innertube.models.SongItem)?.id ?: (x as com.enes.innertube.models.CollectionItem).browseId

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
        queries.value = value
    }

    fun searchNow(query: String = _state.value.query) {
        _state.update { it.copy(query = query) }
        val q = query.trim()
        if (q.isNotEmpty()) {
            remember(q)
            viewModelScope.launch { run(q) }
        }
    }

    private suspend fun run(q: String) {
        val filter = _state.value.filter
        _state.update { it.copy(isLoading = true, error = null) }
        if (filter == SearchFilter.All) {
            repository.search(q)
                .onSuccess { r ->
                    com.enes.liquidtube.data.ImagePrefetcher.prefetch(
                        r.songs.map { it.thumbnailUrl } + (r.artists + r.albums + r.playlists).map { it.thumbnailUrl },
                    )
                    _state.update { it.copy(isLoading = false, results = r) }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") } }
        } else {
            repository.searchFiltered(q, filter)
                .onSuccess { page ->
                    _state.update {
                        if (it.filter != filter) it
                        else it.copy(isLoading = false, filtered = page.items, continuation = page.continuation)
                    }
                }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") } }
        }
    }
}
