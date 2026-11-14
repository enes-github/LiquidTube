package com.enes.liquidtube.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.glassSource
import com.enes.liquidtube.ui.glass.liquidGlass
import com.enes.liquidtube.ui.glass.rememberGlassBackdrop
import com.enes.liquidtube.ui.glass.scrollEdge
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.buttonGlass

/** Height of a compact navigation bar below the status bar. */
val NavBarHeight = 52.dp

/**
 * A tab/page with an iOS large title that collapses into a compact title over a
 * frosted scroll edge once you scroll past it. With [onBack] it becomes a pushed
 * page: a glass back button sits top-left above the large title.
 */
@Composable
fun ScreenScaffold(
    title: String,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    /** Artwork to blur into the page background (the artist-page look). */
    backdropUrl: String? = null,
    /** Backdrop as a top glow fading into the background (lists) instead of a full wash. */
    backdropGlow: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val threshold = with(LocalDensity.current) { 50.dp.toPx() }
    val titleIndex = if (onBack != null) 1 else 0
    val collapsed by remember {
        derivedStateOf {
            state.firstVisibleItemIndex > titleIndex ||
                (state.firstVisibleItemIndex == titleIndex && state.firstVisibleItemScrollOffset > threshold)
        }
    }
    val scrolled by remember { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 4 } }
    val bg = AppTheme.colors.background
    val tinted = backdropUrl != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().glassSource(backdrop, bg)) {
            if (tinted) {
                TintedBackdrop(backdropUrl, glow = backdropGlow) {
                    if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else 2000f
                }
            }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomInset.current),
            ) {
                if (onBack != null) item(key = "nav-space") { Spacer(Modifier.statusBarsPadding().height(NavBarHeight - 8.dp)) }
                item(key = "large-title") { LargeTitle(title, withStatusBar = onBack == null, trailing = trailing) }
                content()
            }
        }
        InlineTitleBar(
            title, visible = collapsed, backdrop = backdrop, scrolled = scrolled,
            tint = if (tinted) AppTheme.colors.background.copy(alpha = 0.3f) else null,
        )
        if (onBack != null) {
            GlassIconButton(
                backdrop, Icons.Rounded.ChevronLeft, "Back", onBack,
                Modifier.statusBarsPadding().padding(start = 14.dp, top = 5.dp),
            )
        }
    }
}

/**
 * The compact centred title over an iOS 26 scroll edge: the content behind is blurred
 * and tinted, melting away below the bar instead of ending in a hard line.
 */
@Composable
fun InlineTitleBar(title: String, visible: Boolean, backdrop: GlassBackdrop, scrolled: Boolean = visible, tint: Color? = null) {
    val c = AppTheme.colors
    // The frosted edge appears as soon as anything scrolls under it; the title only
    // once the large title has gone.
    val edge by animateFloatAsState(if (scrolled || visible) 1f else 0f, tween(220), label = "edge")
    val text by animateFloatAsState(if (visible) 1f else 0f, tween(200), label = "title")
    if (edge == 0f && text == 0f) return
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = edge }
            .scrollEdge(backdrop, tint ?: c.background.copy(alpha = if (c.isDark) 0.62f else 0.72f), blur = 24.dp, fade = 26.dp)
            .statusBarsPadding()
            .padding(bottom = 26.dp)
            .height(NavBarHeight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = AppTheme.type.headline.copy(fontWeight = FontWeight.SemiBold),
            color = c.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(0.6f).graphicsLayer {
                alpha = text
                translationY = (1f - text) * 6.dp.toPx()
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** A round Liquid Glass button floating over page content (back, •••, download). */
@Composable
fun GlassIconButton(
    backdrop: GlassBackdrop,
    icon: ImageVector,
    description: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.label,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
) {
    val c = AppTheme.colors
    Box(
        modifier
            .size(size)
            .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.86f, onClick = onClick) else Modifier)
            .shadow(10.dp, androidx.compose.foundation.shape.CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
            .liquidGlass(backdrop, buttonGlass(c.isDark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * First-appearance animation: content fades in and rises a few dp, staggered by
 * [index]. Runs once per item (state survives scrolling away and back).
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    var shown by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (shown) 1f else 0f) }
    val rise = with(LocalDensity.current) { 18.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!shown) {
            kotlinx.coroutines.delay((index.coerceAtMost(6) * 45).toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 260f))
            shown = true
        }
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}
