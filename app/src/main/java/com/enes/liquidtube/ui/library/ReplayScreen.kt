package com.enes.liquidtube.ui.library

import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.collection.PlayActionRow
import com.enes.liquidtube.ui.collection.SideAction
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.EmptyState
import com.enes.liquidtube.ui.components.FilterChips
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.SongRow
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.glassCardTint
import com.enes.liquidtube.ui.glass.glassChip
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.SongItem
import java.text.SimpleDateFormat
import java.util.Locale

/** One artist's share of a month. */
private data class ArtistStat(val name: String, val ms: Long, val artistId: String?, val art: String?)

/**
 * Replay: your month in music, Apple-style. Minutes listened, top songs (playable as a
 * mix) and top artists, from the listening time LiquidTube records as you play.
 */
@Composable
fun ReplayScreen(onBack: () -> Unit) {
    val connection = LocalPlayerConnection.current ?: return
    val nav = LocalNavActions.current
    val c = AppTheme.colors
    val listening by connection.library.listening.collectAsStateWithLifecycle()
    val months = remember(listening) { listening.keys.sortedDescending() }
    var month by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = month?.takeIf { it in months } ?: months.firstOrNull()

    val data = selected?.let { listening[it] }.orEmpty()
    val topSongs = remember(data) {
        data.entries.sortedByDescending { it.value }
            .mapNotNull { (id, ms) -> connection.library.songFor(id)?.let { it to ms } }
    }
    val artists = remember(topSongs) {
        topSongs.groupBy { (s, _) -> primaryArtist(s) }
            .map { (name, list) ->
                ArtistStat(name, list.sumOf { it.second }, list.firstNotNullOfOrNull { it.first.artistId }, list.first().first.thumbnailUrl)
            }
            .sortedByDescending { it.ms }
    }
    val totalMin = data.values.sum() / 60_000
    val songs = topSongs.map { it.first }
    var showStory by rememberSaveable { mutableStateOf(false) }
    val dayparts by connection.library.dayparts.collectAsStateWithLifecycle()
    if (showStory && selected != null && songs.isNotEmpty()) {
        ReplayStory(
            ReplayStats(
                monthTitle = monthLabel(selected),
                minutes = totalMin,
                songs = topSongs,
                artists = artists.map { it.name to it.ms },
                artistArt = artists.associate { it.name to it.art },
                daypart = dayparts.maxByOrNull { (_, m) -> m.values.sum() }?.key,
            ),
            onClose = { showStory = false },
        )
    }

    ScreenScaffold("Replay", onBack = onBack, backdropUrl = songs.firstOrNull()?.thumbnailUrl, backdropGlow = true) {
        if (months.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.Insights, "Your Replay starts now", "Listen for a while and your top songs and artists of the month show up here.")
            }
            return@ScreenScaffold
        }
        if (months.size > 1) {
            item(key = "months") {
                FilterChips(
                    options = months,
                    selected = selected ?: months.first(),
                    label = ::monthLabel,
                    onSelect = { month = it },
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                )
            }
        }
        item(key = "summary") {
            Column(
                Modifier
                    .padding(horizontal = Gutter, vertical = 8.dp)
                    .fillMaxWidth()
                    .glassChip(tint = glassCardTint(c.isDark), corner = 24.dp, rim = if (c.isDark) 0.3f else 0.8f)
                    .padding(20.dp)
                    .appear(0),
            ) {
                Text(monthLabel(selected ?: "").uppercase(), style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp), color = c.secondaryLabel)
                Spacer(Modifier.height(6.dp))
                Text("$totalMin", style = AppTheme.type.largeTitle.copy(fontSize = 48.sp, lineHeight = 52.sp), color = c.accent)
                Text(if (totalMin == 1L) "minute listened" else "minutes listened", style = AppTheme.type.headline, color = c.label)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Stat("${topSongs.size}", if (topSongs.size == 1) "song" else "songs", Modifier.width(64.dp))
                    Stat("${artists.size}", if (artists.size == 1) "artist" else "artists", Modifier.width(64.dp))
                    artists.firstOrNull()?.let { Stat(it.name, "top artist", Modifier.weight(1f)) }
                }
            }
        }
        if (songs.isNotEmpty()) {
            item(key = "story") {
                // The month as a story you tap through (and can share).
                Row(
                    Modifier
                        .padding(horizontal = Gutter, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFA2D48), androidx.compose.ui.graphics.Color(0xFF7B1FA2))))
                        .bounceClick { showStory = true }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Watch Your Replay", style = AppTheme.type.headline, color = androidx.compose.ui.graphics.Color.White)
                        Text("Your month as a story, ready to share", style = AppTheme.type.footnote, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
                    }
                    Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
                }
            }
            item(key = "play") {
                PlayActionRow(
                    onPlay = { connection.play(songs.take(100)) },
                    left = SideAction(Icons.Rounded.Shuffle, "Shuffle") { connection.shufflePlay(songs.take(100)) },
                    right = null,
                )
            }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists") {
                Column {
                    SectionHeader("Top Artists")
                    LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(artists.take(15)) { i, a ->
                            Column(
                                Modifier
                                    .width(104.dp)
                                    .then(if (a.artistId != null) Modifier.bounceClick { nav.openCollection(a.artistId) } else Modifier),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box {
                                    Artwork(a.art, Modifier.size(96.dp), corner = 48.dp, small = true)
                                    Box(
                                        Modifier.align(Alignment.TopStart).size(26.dp).clip(CircleShape).background(c.accent),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("${i + 1}", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = androidx.compose.ui.graphics.Color.White) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(a.name, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                                Text("${a.ms / 60_000} min", style = AppTheme.type.caption, color = c.secondaryLabel)
                            }
                        }
                    }
                }
            }
        }
        if (topSongs.isNotEmpty()) {
            item(key = "songs-header") { SectionHeader("Top Songs") }
            itemsIndexed(topSongs.take(50), key = { _, p -> "r-${p.first.id}" }) { i, (song, ms) ->
                SongRow(
                    song,
                    onClick = { connection.play(songs.take(100), i) },
                    showDivider = i != topSongs.take(50).lastIndex,
                    leading = {
                        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${i + 1}", style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                            Text("${ms / 60_000}m", style = AppTheme.type.caption2, color = c.secondaryLabel)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier) {
        Text(value, style = AppTheme.type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = AppTheme.type.caption, color = c.secondaryLabel)
    }
}

/** "2026-11" → "November 2026". */
private fun monthLabel(key: String): String = runCatching {
    val d = SimpleDateFormat("yyyy-MM", Locale.US).parse(key)!!
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(d)
}.getOrDefault(key)

/** "Ariana Grande & The Weeknd" → "Ariana Grande". */
private fun primaryArtist(song: SongItem): String =
    song.artists.split(" & ", ", ", " feat. ", " ft. ", " x ").first().trim().ifBlank { song.artists }
