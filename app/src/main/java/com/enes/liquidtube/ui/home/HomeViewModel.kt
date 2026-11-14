package com.enes.liquidtube.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enes.liquidtube.data.AuthStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.innertube.models.FeedShelf
import com.enes.innertube.models.HomeChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedUiState(
    val shelves: List<FeedShelf> = emptyList(),
    val isLoading: Boolean = true,
    /** Mood/genre filters (Home only) and the selected one; null = everything. */
    val chips: List<HomeChip> = emptyList(),
    val selectedChip: HomeChip? = null,
)

/** Feed loader: a primary source, topped up with seed-search shelves when thin. */
abstract class FeedViewModel(
    protected val repository: MusicRepository,
    private val auth: AuthStore,
    private val seeds: List<Pair<String, String>>,
    /** Home only: the last feed on disk, shown instantly at launch. */
    private val cache: com.enes.liquidtube.data.FeedCache? = null,
) : ViewModel() {
    protected val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    /** Seed-search shelves only make sense for the unfiltered feed. */
    protected open val topUpWithSeeds: Boolean get() = true
    private var job: Job? = null

    protected abstract suspend fun primary(): List<FeedShelf>

    init {
        // Open on the last feed straight away; the fresh one replaces it when it arrives.
        if (cache != null) viewModelScope.launch {
            val saved = cache.read(auth.isLoggedIn.value) ?: return@launch
            if (_state.value.shelves.isEmpty() && saved.first.isNotEmpty()) {
                _state.update { it.copy(shelves = saved.first, chips = saved.second, isLoading = false) }
                onLoaded(saved.first)
            }
        }
        // Reload when signing in/out so Home becomes personal immediately.
        viewModelScope.launch { auth.isLoggedIn.collect { load() } }
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(isLoading = true) }
        job = viewModelScope.launch {
            val shelves = primary()
            if (shelves.isEmpty() && _state.value.shelves.isNotEmpty() && _state.value.selectedChip == null) {
                // Offline or failed: keep showing what's there (e.g. the saved feed).
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            // Show the real feed as soon as it's here; filler shelves come after.
            _state.update { it.copy(shelves = shelves, isLoading = false) }
            onLoaded(shelves)
            var all = shelves
            if (shelves.size < 5 && topUpWithSeeds) {
                all = shelves + repository.seedShelves(seeds)
                _state.update { it.copy(shelves = all) }
            }
            if (cache != null && _state.value.selectedChip == null && all.isNotEmpty()) {
                cache.write(all, _state.value.chips, auth.isLoggedIn.value)
            }
        }
    }

    /** Warm the image cache for everything on the page, so fast scrolling never waits. */
    protected open fun onLoaded(shelves: List<FeedShelf>) {
        com.enes.liquidtube.data.ImagePrefetcher.prefetch(
            shelves.flatMap { sh -> sh.songs.mapNotNull { it.thumbnailUrl } + sh.collections.mapNotNull { it.thumbnailUrl } },
        )
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: MusicRepository,
    auth: AuthStore,
    cache: com.enes.liquidtube.data.FeedCache,
) : FeedViewModel(
    repository, auth,
    listOf(
        "Top Picks for You" to "top hits this week",
        "Chill" to "chill vibes",
        "Pop" to "pop hits",
        "Hip-Hop" to "hip hop hits",
        "Throwbacks" to "2010s throwback hits",
    ),
    cache,
) {
    override val topUpWithSeeds get() = _state.value.selectedChip == null

    override fun onLoaded(shelves: List<FeedShelf>) {
        super.onLoaded(shelves)
        com.enes.liquidtube.ui.launch.Startup.homeReady() // the launch screen can go
    }

    override suspend fun primary(): List<FeedShelf> {
        val selected = _state.value.selectedChip
        val feed = repository.homeFeed(selected?.params).getOrNull() ?: return emptyList()
        // Keep the full chip list from the unfiltered feed.
        if (selected == null || _state.value.chips.isEmpty()) _state.update { it.copy(chips = feed.chips) }
        return feed.shelves
    }

    fun selectChip(chip: HomeChip?) {
        val next = if (chip == _state.value.selectedChip) null else chip
        _state.update { it.copy(selectedChip = next, shelves = emptyList()) }
        load()
    }
}
