package com.enes.liquidtube.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.ui.components.Gutter
import com.enes.liquidtube.ui.components.phoneWidth
import com.enes.liquidtube.ui.components.LocalMenus
import com.enes.liquidtube.ui.components.ScreenScaffold
import com.enes.liquidtube.ui.components.SectionHeader
import com.enes.liquidtube.ui.components.appear
import com.enes.liquidtube.ui.components.pressMenu
import com.enes.liquidtube.ui.home.AccountButton
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.SongItem

private data class Station(
    val name: String,
    val tagline: String,
    val query: String,
    val colors: List<Color>,
    val icon: ImageVector = Icons.Rounded.Sensors,
)

private val featured = listOf(
    Station("Hits Radio", "The biggest songs right now", "top hits this week", listOf(Color(0xFFFA2D48), Color(0xFFFF8A3D)), Icons.Rounded.LocalFireDepartment),
    Station("Chill Radio", "Relax and unwind", "chill lofi", listOf(Color(0xFF3B5BDB), Color(0xFF5FD4F4)), Icons.Rounded.Headphones),
    Station("Throwback Radio", "Hits from the 2000s & 2010s", "2010s throwback hits", listOf(Color(0xFFFF9500), Color(0xFFFFCC00)), Icons.Rounded.Album),
)

private val genres = listOf(
    Station("Pop", "", "pop hits", listOf(Color(0xFFFF2D92), Color(0xFFFF6477)), Icons.Rounded.MusicNote),
    Station("Hip-Hop", "", "hip hop hits", listOf(Color(0xFF8E2DE2), Color(0xFFFA2D48)), Icons.Rounded.Bolt),
    Station("Rock", "", "rock classics", listOf(Color(0xFF3A3A3C), Color(0xFF8E8E93)), Icons.Rounded.Album),
    Station("Electronic", "", "electronic dance hits", listOf(Color(0xFF00B4DB), Color(0xFF0083B0)), Icons.Rounded.Sensors),
    Station("R&B", "", "rnb hits", listOf(Color(0xFF7F00FF), Color(0xFFE100FF)), Icons.Rounded.Headphones),
    Station("Jazz", "", "jazz classics", listOf(Color(0xFF1D976C), Color(0xFF93F9B9)), Icons.Rounded.Piano),
    Station("Party", "", "party hits", listOf(Color(0xFFF7971E), Color(0xFFFFD200)), Icons.Rounded.Celebration),
    Station("Workout", "", "workout motivation", listOf(Color(0xFFED213A), Color(0xFF93291E)), Icons.Rounded.FitnessCenter),
)

@Composable
fun RadioScreen() {
    val gridAcross = if (com.enes.liquidtube.ui.components.isLandscape()) 4 else 2
    val connection = LocalPlayerConnection.current ?: return
    val recents by connection.library.recents.collectAsStateWithLifecycle()
    // One personal station per recently played artist.
    val personal = recents.distinctBy { it.artists.substringBefore(",").substringBefore(" & ").trim() }.take(10)

    ScreenScaffold("Radio", trailing = { AccountButton() }) {
        item(key = "featured") {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp).appear(0)) {
                val cardWidth = phoneWidth(maxWidth) - Gutter * 2 - 20.dp
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(featured, key = { it.name }) { station ->
                        FeaturedStationCard(station, Modifier.width(cardWidth)) {
                            connection.playFromQuery(station.query)
                        }
                    }
                }
            }
        }

        if (personal.isNotEmpty()) {
            item(key = "personal") {
                Column(Modifier.appear(1)) {
                    SectionHeader("Stations for You")
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cardWidth = (phoneWidth(maxWidth) - Gutter - 12.dp * 2) / 2.2f
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Gutter),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(personal, key = { it.id }) { song ->
                                PersonalStationCard(song, Modifier.width(cardWidth)) { connection.playSong(song) }
                            }
                        }
                    }
                }
            }
        }

        item(key = "genres-header") { Box(Modifier.appear(2)) { SectionHeader("Genre Stations") } }
        genres.chunked(gridAcross).forEachIndexed { row, pair ->
            item(key = "genres-$row") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Gutter, vertical = 6.dp)
                        .appear(3 + row),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { station ->
                        GenreTile(station, Modifier.weight(1f)) { connection.playFromQuery(station.query) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedStationCard(station: Station, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(modifier.pressMenu(onClick = onClick, menu = null)) {
        Text(
            "RADIO STATION",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
            color = c.secondaryLabel,
        )
        Text(station.name, style = AppTheme.type.title3, color = c.label)
        Text(station.tagline, style = AppTheme.type.subhead, color = c.secondaryLabel)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.55f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(station.colors)),
        ) {
            Icon(
                station.icon, null,
                tint = Color.White.copy(alpha = 0.18f),
                modifier = Modifier.size(170.dp).align(Alignment.CenterEnd).offset(x = 30.dp),
            )
            Text(
                station.name.removeSuffix(" Radio"),
                style = AppTheme.type.title1.copy(fontSize = 34.sp),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** A station seeded by a song you played: glowing artwork ring on its own blurred art. */
@Composable
private fun PersonalStationCard(song: SongItem, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val menus = LocalMenus.current
    val artist = song.artists.substringBefore(",").substringBefore(" & ").trim()
    Column(modifier.pressMenu(onClick = onClick, menu = { menus?.song(song) })) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(c.elevated),
            contentAlignment = Alignment.Center,
        ) {
            com.enes.liquidtube.ui.components.BlurredImage(song.thumbnailUrl, Modifier.fillMaxSize()) {
                scaleX = 1.4f
                scaleY = 1.4f
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
            Box(
                Modifier
                    .fillMaxSize(0.62f)
                    .shadow(18.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.3f))
                    .padding(3.dp)
                    .clip(CircleShape),
            ) {
                AsyncImage(song.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Icon(
                Icons.Rounded.Sensors, null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(18.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            "$artist Station",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Medium),
            color = c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("Based on “${song.title}”", style = AppTheme.type.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GenreTile(station: Station, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1.5f)
            .pressMenu(onClick = onClick, menu = null)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(station.colors)),
    ) {
        Icon(
            station.icon, null,
            tint = Color.White.copy(alpha = 0.22f),
            modifier = Modifier.size(84.dp).align(Alignment.TopEnd).offset(x = 14.dp, y = (-10).dp),
        )
        Text(
            station.name,
            style = AppTheme.type.headline,
            color = Color.White,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}
