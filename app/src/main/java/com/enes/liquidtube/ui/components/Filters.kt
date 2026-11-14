package com.enes.liquidtube.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enes.liquidtube.ui.glass.glassChip
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType

/**
 * iOS 26/27 filter capsules: a scrolling row of glass chips; the selected one fills
 * with the label colour. Scrolls the selected chip into view when it changes.
 */
@Composable
fun <T> FilterChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    edge: androidx.compose.ui.unit.Dp = Gutter,
) {
    val haptic = LocalHapticFeedback.current
    val state = rememberLazyListState()
    val index = options.indexOf(selected).coerceAtLeast(0)
    LaunchedEffect(index) { state.animateScrollToItem((index - 1).coerceAtLeast(0)) }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = edge),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { _, option ->
            FilterChip(label(option), option == selected) {
                if (option != selected) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(option)
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    val bg by animateColorAsState(
        if (selected) c.label else if (c.isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f),
        tween(180), label = "chipBg",
    )
    val fg by animateColorAsState(if (selected) c.background else c.label, tween(180), label = "chipFg")
    Box(
        Modifier
            .height(34.dp)
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(17.dp))
            .background(bg)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

/** Translucent card fill that reads on tinted artwork backdrops. */
fun glassCardTint(dark: Boolean) = if (dark) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.55f)

/** iOS inset-grouped glass card holding a few rows. */
@Composable
fun GlassGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = AppTheme.colors
    Column(
        modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .glassChip(tint = glassCardTint(c.isDark), corner = 22.dp, rim = if (c.isDark) 0.3f else 0.8f),
    ) { content() }
}

/** A row inside a [GlassGroup]: accent icon, label, optional count, chevron. */
@Composable
fun GlassGroupRow(
    icon: ImageVector,
    label: String,
    count: Int = 0,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressMenu(onClick = onClick, menu = null, highlight = true)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(52.dp)) {
            Row(Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppTheme.type.body.copy(fontSize = 18.sp), color = c.label, modifier = Modifier.weight(1f))
                if (count > 0) Text("$count", style = AppTheme.type.body, color = c.secondaryLabel)
                Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(22.dp))
            }
            if (showDivider) {
                HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator.copy(alpha = 0.5f))
            }
        }
    }
}

/** List row for an album/playlist/artist: artwork (round for artists), title, subtitle, chevron. */
@Composable
fun CollectionRow(
    item: CollectionItem,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    menuExtra: List<MenuAction> = emptyList(),
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    val menus = LocalMenus.current
    val isArtist = item.type == CollectionType.ARTIST
    Row(
        modifier
            .fillMaxWidth()
            .pressMenu(onClick = onClick, menu = { menus?.collection(item, menuExtra) }, highlight = true)
            .padding(start = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(item.thumbnailUrl, Modifier.size(if (isArtist) 50.dp else 58.dp), corner = if (isArtist) 25.dp else 7.dp, small = true)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(if (isArtist) 64.dp else 74.dp)) {
            Row(Modifier.fillMaxWidth().align(Alignment.CenterStart).padding(end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = AppTheme.type.callout.copy(fontSize = 17.sp), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.subtitle?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(it, style = AppTheme.type.subhead.copy(fontSize = 14.sp), color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiaryLabel, modifier = Modifier.size(22.dp))
            }
            if (showDivider) {
                HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 0.5.dp, color = c.separator.copy(alpha = if (c.isDark) 0.55f else 0.7f))
            }
        }
    }
}
