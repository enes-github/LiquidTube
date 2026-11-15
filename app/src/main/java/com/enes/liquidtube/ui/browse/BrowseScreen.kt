package com.enes.liquidtube.ui.browse

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.enes.liquidtube.data.AuthStore
import com.enes.liquidtube.data.MusicRepository
import com.enes.liquidtube.ui.home.FeedScreen
import com.enes.liquidtube.ui.home.FeedViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BrowseViewModel @Inject constructor(
    repository: MusicRepository,
    auth: AuthStore,
) : FeedViewModel(
    repository, auth,
    listOf(
        "Rock" to "rock hits",
        "R&B / Soul" to "rnb soul hits",
        "Electronic" to "electronic dance hits",
    ),
) {
    override suspend fun primary() = repository.explore().getOrDefault(emptyList())
}

/** The "New" tab: new releases, trending, charts. */
@Composable
fun BrowseScreen(viewModel: BrowseViewModel = hiltViewModel()) {
    FeedScreen(tr("New", "Yeni"), viewModel, showRecents = false, style = com.enes.liquidtube.ui.components.FeedStyle.New)
}
