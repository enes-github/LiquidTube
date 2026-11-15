@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.enes.liquidtube.ui.home

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.FilterChips
import androidx.compose.foundation.layout.padding
import com.enes.liquidtube.ui.components.LargeTitle
import com.enes.liquidtube.ui.components.LoadingBox
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.SongShelf
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.feedShelves
import com.enes.liquidtube.ui.components.FeedStyle
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.glass.glassChip
import com.enes.innertube.models.SongItem

/** Shared feed screen for Home and New. [onChip] enables the mood filter row. */
@Composable
fun FeedScreen(
    title: String,
    viewModel: FeedViewModel,
    showRecents: Boolean,
    style: FeedStyle = FeedStyle.Home,
    onChip: ((com.enes.innertube.models.HomeChip?) -> Unit)? = null,
) {
    val connection = LocalPlayerConnection.current ?: return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by connection.library.recents.collectAsStateWithLifecycle()
    val play: (List<SongItem>, Int) -> Unit = { songs, i -> connection.play(songs, i) }

    val online by connection.network.online.collectAsStateWithLifecycle()
    val downloadMap by connection.downloads.downloads.collectAsStateWithLifecycle()
    val downloaded = downloadMap.values.filter { it.isDone }.map { it.song }

    val healthy by connection.playbackHealthy.collectAsStateWithLifecycle()
    val cachedNow by connection.playCache.cached.collectAsStateWithLifecycle()
    // Language changed: YouTube Music now answers in the new language, so reload.
    val language = com.enes.liquidtube.ui.theme.AppLanguage.effective
    var shownLanguage by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(language) }
    androidx.compose.runtime.LaunchedEffect(language) {
        if (language != shownLanguage) {
            shownLanguage = language
            viewModel.load()
        }
    }
    // Back online: load what couldn't be loaded while offline (or refresh a stale feed).
    androidx.compose.runtime.LaunchedEffect(online) {
        if (online && !state.isLoading && state.shelves.isEmpty()) viewModel.load()
    }

    // New tab: releases from your artists (checked in the background; at most twice a day).
    val releases by com.enes.liquidtube.data.NewReleases.recent.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (style == FeedStyle.New) {
        androidx.compose.runtime.LaunchedEffect(online) {
            if (online) runCatching { com.enes.liquidtube.data.NewReleases.checkIfStale(context) }
        }
    }

    // Pull to refresh: reloads the feed (and on New, looks for releases right away).
    var refreshing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.isLoading) { if (!state.isLoading) refreshing = false }
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            viewModel.load()
            if (style == FeedStyle.New) scope.launch { runCatching { com.enes.liquidtube.data.NewReleases.check(context) } }
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                containerColor = AppTheme.colors.elevated,
                color = AppTheme.colors.accent,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 44.dp),
            )
        },
    ) {
    ScreenScaffold(title, trailing = { AccountButton() }) {
        if (online && !healthy) {
            item(key = "health") {
                EmptyState(
                    Icons.Rounded.WifiOff,
                    tr("Playback may not work right now", "Oynatma şu anda çalışmayabilir"),
                    tr("YouTube seems to have changed something. Songs might skip until LiquidTube is updated.", "YouTube bir değişiklik yaptı. LiquidTube güncellenene kadar şarkılar atlanabilir."),
                    action = tr("Check Again", "Tekrar Kontrol Et") to connection::recheckPlayback,
                    modifier = Modifier.padding(vertical = 0.dp),
                )
            }
        }
        if (!online) {
            item(key = "offline") { OfflineCard((downloaded + cachedNow).distinctBy { it.id }.size) }
            val auto = connection.downloads.autoIds.value
            val mine = downloaded.filter { it.id !in auto }
            val mix = (downloaded.filter { it.id in auto } + cachedNow.filter { c -> mine.none { it.id == c.id } }).distinctBy { it.id }
            if (mix.isNotEmpty()) item(key = "offline-mix") { SongShelf(tr("Offline Mix", "Çevrimdışı Miks"), mix, play) }
            if (mine.isNotEmpty()) item(key = "offline-downloads") { SongShelf(tr("Downloaded", "İndirilenler"), mine, play) }
        }
        if (onChip != null && state.chips.isNotEmpty() && online) {
            item(key = "chips") {
                // "All" first, then YouTube Music's moods; tapping the selected one clears it.
                val allLabel = tr("All", "Tümü")
                val all = com.enes.innertube.models.HomeChip(allLabel, "")
                FilterChips(
                    options = listOf(all) + state.chips,
                    selected = state.selectedChip ?: all,
                    label = { it.title },
                    onSelect = { onChip(if (it === all) null else it) },
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                )
            }
        }
        // Offline, Home is only what can play: the online feed comes back with the connection.
        if (!online) return@ScreenScaffold
        if (style == FeedStyle.New && releases.isNotEmpty()) {
            item(key = "artist-releases") {
                Box(Modifier.appear(0)) {
                    com.enes.liquidtube.ui.components.CollectionShelf(
                        tr("New from Your Artists", "Takip Ettiğiniz Sanatçılardan Yeni"),
                        releases.map { r -> r.item.copy(subtitle = r.artist) },
                    )
                }
            }
        }
        if (showRecents && recents.isNotEmpty() && state.selectedChip == null) {
            item(key = "recents") { Box(Modifier.appear(0)) { SongShelf(tr("Recently Played", "Son Çalınanlar"), recents.take(15), play) } }
        }
        // Home: the mix for this time of day, from what you play at this hour.
        if (style == FeedStyle.Home && state.selectedChip == null) {
            val timeMix = com.enes.liquidtube.data.SmartPlaylists.timeMix(connection.library)
            if (timeMix != null) {
                item(key = "time-mix") {
                    Box(Modifier.appear(1)) { SongShelf(timeMix.title, timeMix.songs, play) }
                }
            }
        }

        when {
            state.isLoading && state.shelves.isEmpty() -> item { LoadingBox() }
            state.shelves.isEmpty() && !online -> Unit
            state.shelves.isEmpty() -> item {
                EmptyState(
                    Icons.Rounded.WifiOff,
                    tr("Couldn't load music", "Müzik yüklenemedi"),
                    tr("Check your connection and try again.", "Bağlantınızı kontrol edip tekrar deneyin."),
                    action = tr("Try Again", "Tekrar Deneyin") to viewModel::load,
                )
            }
            else -> feedShelves(state.shelves, play, style)
        }
    }
    }
}

/** Shown at the top of Home while there's no connection. */
@Composable
private fun OfflineCard(downloads: Int) {
    val c = AppTheme.colors
    androidx.compose.foundation.layout.Row(
        Modifier
            .padding(horizontal = com.enes.liquidtube.ui.components.Gutter, vertical = 8.dp)
            .fillMaxWidth()
            .glassChip(
                tint = if (c.isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f),
                corner = 20.dp,
                rim = if (c.isDark) 0.3f else 0.8f,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = c.accent, modifier = Modifier.size(28.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        androidx.compose.foundation.layout.Column {
            androidx.compose.material3.Text(tr("You’re offline", "Çevrimdışısınız"), style = AppTheme.type.headline, color = c.label)
            androidx.compose.material3.Text(
                if (downloads > 0) tr("Your $downloads downloaded songs are ready to play.", "$downloads indirilen şarkınız çalmaya hazır.") else tr("Download songs to listen without a connection.", "İnternet bağlantısı olmadan dinlemek için şarkı indirin."),
                style = AppTheme.type.subhead,
                color = c.secondaryLabel,
            )
        }
    }
}

/** Avatar in the large-title bar: opens the account page. */
@Composable
fun AccountButton() {
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val account by connection.auth.account.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    Box(
        Modifier
            .size(40.dp)
            .bounceClick(pressedScale = 0.85f, onClick = nav.openAccount)
            .glassChip(tint = c.fill, rim = if (c.isDark) 0.4f else 0.9f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Person, "Account", tint = c.secondaryLabel, modifier = Modifier.size(22.dp))
        account?.photoUrl?.let {
            AsyncImage(it, "Account", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    FeedScreen(tr("Home", "Ana Sayfa"), viewModel, showRecents = true, onChip = viewModel::selectChip)
}
