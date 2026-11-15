package com.enes.liquidtube.ui.player

import com.enes.liquidtube.ui.theme.tr
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.rounded.History
import android.app.Activity
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.playback.QueueEntry
import com.enes.liquidtube.ui.components.Artwork
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.formatMs
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.rounded.Airplay
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.pressMenu
import com.enes.liquidtube.ui.components.menuButton
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import com.enes.liquidtube.ui.glass.glassChip
import com.enes.liquidtube.ui.components.MenuAction
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.InterDisplay
import com.enes.innertube.models.LyricLine
import com.enes.innertube.models.Lyrics
import com.enes.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Light tint pulled from the artwork; artist name, sliders and icons use it (iOS 26). */
private val LocalTint = compositionLocalOf { Color.White }

private val Dim: Color
    @Composable get() = LocalTint.current.copy(alpha = 0.62f)

/** Where the landscape cover rests in one layout; remembers the last spot once it's gone. */
private class CoverSlot {
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
    private var lastSize: androidx.compose.ui.unit.IntSize? = null
    private var lastPosition: Offset? = null

    fun currentSize(): androidx.compose.ui.unit.IntSize? =
        coords?.takeIf { it.isAttached }?.size?.also { lastSize = it } ?: lastSize

    fun currentPosition(relativeTo: androidx.compose.ui.layout.LayoutCoordinates): Offset? =
        coords?.takeIf { it.isAttached && relativeTo.isAttached }
            ?.let { relativeTo.localPositionOf(it, Offset.Zero) }
            ?.also { lastPosition = it } ?: lastPosition

    fun lastKnown(): Offset? = lastPosition
}

/** Landscape Now Playing: a smaller, evenly sized set of controls that fits the short height. */
private val LocalCompactControls = compositionLocalOf { false }

private enum class PanelMode { Artwork, Lyrics, Queue }

@Composable
fun NowPlayingScreen(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    containerModifier: Modifier = Modifier,
    artModifier: Modifier = Modifier,
    shown: Boolean = true,
) {
    val song by connection.current.collectAsStateWithLifecycle()
    val current = song ?: return
    val isPlaying by connection.isPlaying.collectAsStateWithLifecycle()
    val positionMs by connection.positionMs.collectAsStateWithLifecycle()
    val durationMs by connection.durationMs.collectAsStateWithLifecycle()
    val upNext by connection.upNext.collectAsStateWithLifecycle()
    val shuffle by connection.shuffle.collectAsStateWithLifecycle()
    val repeatMode by connection.repeatMode.collectAsStateWithLifecycle()
    val favorites by connection.library.favorites.collectAsStateWithLifecycle()
    val isFavorite = favorites.any { it.id == current.id }
    val nav = LocalNavActions.current

    var mode by rememberSaveable { mutableStateOf(PanelMode.Artwork) }
    var lyricsFull by rememberSaveable { mutableStateOf(false) }
    val shareScope = rememberCoroutineScope()
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    val shareLyrics: (List<String>, Long?) -> Unit = { lines, startMs ->
        shareScope.launch { runCatching { LyricsShare.share(shareContext, current, lines, startMs) } }
    }
    val palette = rememberArtworkPalette(current.thumbnailUrl)
    val topColor = palette.top

    var lyrics by remember(current.id) { mutableStateOf<Lyrics?>(null) }
    var lyricsLoading by remember(current.id) { mutableStateOf(true) }
    val online by connection.network.online.collectAsStateWithLifecycle()
    // Re-fetches when the connection comes back, if the first try found nothing.
    LaunchedEffect(current.id, online) {
        if (lyrics != null) return@LaunchedEffect
        lyricsLoading = true
        lyrics = connection.lyrics(current, durationMs)
        lyricsLoading = false
    }

    // Translation under each line (toggle in the lyrics header; language in Settings).
    val translateOn by connection.settings.translateLyrics.collectAsStateWithLifecycle()
    val translateTo by connection.settings.translateTo.collectAsStateWithLifecycle()
    val translations by androidx.compose.runtime.produceState<List<String>?>(null, current.id, lyrics, translateOn, translateTo) {
        value = null
        val l = lyrics ?: return@produceState
        if (!translateOn) return@produceState
        val lines = l.synced?.map { it.text } ?: l.plain.orEmpty().lines()
        value = com.enes.liquidtube.data.LyricsTranslator.translate(
            current.id, lines, com.enes.liquidtube.data.LyricsTranslator.target(translateTo),
        )
    }
    val translateButton: @Composable () -> Unit = {
        CircleButton(
            Icons.Rounded.Translate, if (translateOn) tr("Hide Translation", "Çeviriyi Gizle") else tr("Translate", "Çevir"),
            modifier = Modifier.graphicsLayer { alpha = if (translateOn) 1f else 0.55f },
            onClick = { connection.settings.setTranslateLyrics(!translateOn) },
        )
        Spacer(Modifier.width(10.dp))
    }
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val dismissPx = with(LocalDensity.current) { 150.dp.toPx() }
    // Reopened while it was still closing (after a swipe down): the same sheet comes back,
    // so it has to spring back up from wherever the swipe left it, not stay half way down.
    LaunchedEffect(shown) {
        if (shown && offsetY.value != 0f) offsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 380f))
    }

    ForceLightSystemBars()
    BackHandler { if (mode != PanelMode.Artwork) mode = PanelMode.Artwork else onCollapse() }

    val toggleFavorite = { connection.toggleFavorite(current) }
    val openArtist: (() -> Unit)? = current.artistId?.let { id ->
        {
            onCollapse()
            nav.openCollection(id)
        }
    }

    val context = LocalContext.current
    val landscape = com.enes.liquidtube.ui.components.isLandscape()
    CompositionLocalProvider(LocalTint provides palette.tint) {
        Box(
            containerModifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    shape = RoundedCornerShape(if (offsetY.value > 1f) 32.dp else 0.dp)
                    clip = true
                }
                .background(palette.bottom)
                .pointerInput(Unit) { detectTapGestures { } } // swallow taps so they don't hit the screen below
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                    },
                    onDragStopped = { velocity ->
                        if (offsetY.value > dismissPx || velocity > 1800f) onCollapse()
                        else offsetY.animateTo(0f, spring(dampingRatio = 0.85f))
                    },
                ),
        ) {
            if (landscape) {
                // Landscape: cover + title on the left, controls on the right. Opening lyrics or
                // the queue gives them the whole left side: the cover shrinks and flies up to a
                // small header above the controls (one moving image, so it reads as one object).
                LandscapeBackdrop(current.thumbnailUrl, palette)
                val premiumIds by connection.quality.premiumIds.collectAsStateWithLifecycle()
                val lyricsButtons: @Composable () -> Unit = {
                    if (lyrics != null) {
                        translateButton()
                        CircleButton(Icons.Rounded.Share, tr("Share Lyrics", "Sözleri Paylaş"), onClick = {
                            val l = lyrics
                            val synced = l?.synced
                            val i = synced?.indexOfLast { it.timeMs <= positionMs + 300 }?.coerceAtLeast(0) ?: 0
                            val text = synced?.drop(i)?.take(2)?.map { it.text }?.filter { it.isNotBlank() }
                                ?: l?.plain.orEmpty().lines().filter { it.isNotBlank() }.take(2)
                            if (text.isNotEmpty()) shareLyrics(text, synced?.getOrNull(i)?.timeMs ?: positionMs)
                        })
                        Spacer(Modifier.width(10.dp))
                    }
                }
                // No full-screen lyrics in landscape: the controls always stay beside them.
                val showControls = true

                // Where the cover sits in each layout (in this box's coordinates).
                val bigSlot = remember { CoverSlot() }
                val smallSlot = remember { CoverSlot() }
                // While things move, re-place the cover every frame (the header slides in with
                // its own animation, which the cover must follow to the last pixel).
                var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
                LaunchedEffect(mode) {
                    val end = System.nanoTime() + 1_400_000_000L
                    while (System.nanoTime() < end) androidx.compose.runtime.withFrameNanos { tick++ }
                }
                val collapse by animateFloatAsState(
                    if (mode == PanelMode.Artwork) 0f else 1f,
                    spring(dampingRatio = 0.82f, stiffness = 260f),
                    label = "coverFly",
                )
                val coverAlpha by animateFloatAsState(if (showControls) 1f else 0f, tween(220), label = "coverAlpha")
                val pausedScale by animateFloatAsState(
                    if (isPlaying) 1f else 0.86f,
                    spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
                    label = "landArtScale",
                )

                CompositionLocalProvider(LocalCompactControls provides true) {
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .displayCutoutPadding()
                        .padding(start = 32.dp, end = 32.dp, top = 6.dp, bottom = 10.dp),
                ) {
                    val gap = 48.dp
                    val controlsWidth = (maxWidth - gap) * 0.5f
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            label = "panelLand",
                        ) { panel ->
                            when (panel) {
                                PanelMode.Artwork -> LandscapeCover(
                                    current, isFavorite, openArtist, toggleFavorite,
                                    onSlot = { bigSlot.coords = it },
                                ) { SongMoreMenu(current, connection, onCollapse) }
                                PanelMode.Lyrics -> Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                                    LyricsPanel(
                                        lyrics, lyricsLoading, positionMs, connection::seekTo,
                                        full = false, onToggleFull = {}, onShare = shareLyrics,
                                        translations = translations,
                                    )
                                }
                                PanelMode.Queue -> Box(Modifier.fillMaxSize().padding(top = 14.dp)) {
                                    QueuePanel(
                                        upNext = upNext,
                                        connection = connection,
                                        shuffle = shuffle,
                                        repeatMode = repeatMode,
                                        accent = topColor,
                                    )
                                }
                            }
                        }
                        // Full-screen lyrics: the controls step aside and the lyrics get the width.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showControls,
                            enter = androidx.compose.animation.expandHorizontally() + fadeIn(),
                            exit = androidx.compose.animation.shrinkHorizontally() + fadeOut(),
                        ) {
                            Row {
                                Spacer(Modifier.width(gap))
                                Column(
                                    Modifier.width(controlsWidth).fillMaxHeight(),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    // The song, above the controls, while lyrics or the queue are open.
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = mode != PanelMode.Artwork,
                                        enter = androidx.compose.animation.expandVertically(spring(dampingRatio = 0.86f, stiffness = 300f)) + fadeIn(tween(260, delayMillis = 80)),
                                        exit = androidx.compose.animation.shrinkVertically(spring(dampingRatio = 0.86f, stiffness = 300f)) + fadeOut(tween(140)),
                                    ) {
                                        LandscapeHeader(
                                            current, isFavorite, toggleFavorite,
                                            onSlot = { smallSlot.coords = it },
                                            extra = if (mode == PanelMode.Lyrics) lyricsButtons else ({}),
                                        )
                                    }
                                    Scrubber(
                                        positionMs, durationMs, onSeek = connection::seekTo,
                                        badge = if (current.id in premiumIds) "256 kbps" else null,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Transport(
                                        isPlaying = isPlaying,
                                        onPrevious = connection::skipPrevious,
                                        onToggle = connection::togglePlayPause,
                                        onNext = connection::skipNext,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    VolumeRow()
                                    Spacer(Modifier.height(14.dp))
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ToggleIcon(
                                            Icons.Rounded.FormatQuote, tr("Lyrics", "Sözler"), mode == PanelMode.Lyrics, topColor,
                                            dimmed = !lyricsLoading && lyrics == null,
                                        ) { mode = if (mode == PanelMode.Lyrics) PanelMode.Artwork else PanelMode.Lyrics }
                                        ToggleIcon(Icons.Rounded.Airplay, tr("Output", "Çıkış"), false, topColor) { openOutputSwitcher(context) }
                                        ToggleIcon(
                                            Icons.AutoMirrored.Rounded.QueueMusic, tr("Playing Next", "Sıradaki"), mode == PanelMode.Queue, topColor,
                                        ) { mode = if (mode == PanelMode.Queue) PanelMode.Artwork else PanelMode.Queue }
                                    }
                                }
                            }
                        }
                    }
                }

                }
                // The one cover, flying between its big spot and the header above the controls.
                // Its size and position are worked out during layout, from where the two slots
                // are in this very frame, so it lands exactly in place with no jump at the end.
                val t = collapse
                LandscapeFlyingCover(
                    url = current.thumbnailUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .layout { measurable, constraints ->
                            @Suppress("UNUSED_EXPRESSION") tick
                            val k = collapse
                            // The cover must be measured and placed in every pass, including the
                            // preview ("lookahead") pass the open animation runs first; skipping
                            // a pass crashes. Slot positions are only read in the real pass.
                            // Either spot may not exist yet (e.g. rotated into landscape with lyrics
                            // open: the big spot was never laid out), so each falls back to the other.
                            val bigSize = bigSlot.currentSize()
                            val smallSize = smallSlot.currentSize()
                            val from = bigSize ?: smallSize
                            val to = smallSize ?: bigSize
                            val w = if (from == null || to == null) 1 else (from.width + (to.width - from.width) * k).roundToInt().coerceAtLeast(1)
                            val h = if (from == null || to == null) 1 else (from.height + (to.height - from.height) * k).roundToInt().coerceAtLeast(1)
                            val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, h))
                            val lookahead = isLookingAhead
                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val me = if (lookahead) null else coordinates
                                val big = me?.let { bigSlot.currentPosition(it) } ?: bigSlot.lastKnown()
                                val small = me?.let { smallSlot.currentPosition(it) } ?: smallSlot.lastKnown()
                                val p0 = big ?: small
                                val p1 = small ?: big
                                if (p0 != null && p1 != null) {
                                    placeable.place(
                                        (p0.x + (p1.x - p0.x) * k).roundToInt(),
                                        (p0.y + (p1.y - p0.y) * k).roundToInt(),
                                    )
                                } else {
                                    placeable.place(0, 0)
                                }
                            }
                        }
                        .graphicsLayer {
                            val scale = pausedScale + (1f - pausedScale) * collapse
                            scaleX = scale
                            scaleY = scale
                            alpha = if (bigSlot.lastKnown() == null && smallSlot.lastKnown() == null) 0f else coverAlpha
                        },
                    artModifier = Modifier,
                    corner = (14f - 6f * t).dp,
                    shadow = (28f * (1f - t) + 6f * t).dp,
                )
            } else {
            FullBleedArtwork(
                url = current.thumbnailUrl,
                palette = palette,
                sharp = mode == PanelMode.Artwork,
                isPlaying = isPlaying,
                artModifier = if (mode == PanelMode.Artwork) artModifier else Modifier,
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 30.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(palette.tint.copy(alpha = 0.55f)),
                )

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                    modifier = Modifier.weight(1f),
                    label = "panel",
                ) { panel ->
                    val headerArt = if (mode != PanelMode.Artwork) artModifier else Modifier
                    when (panel) {
                        PanelMode.Artwork -> Column(Modifier.fillMaxSize()) {
                            Spacer(Modifier.weight(1f))
                            TitleRow(current, isFavorite, openArtist, toggleFavorite) {
                                SongMoreMenu(current, connection, onCollapse)
                            }
                        }
                        PanelMode.Lyrics -> Column(Modifier.fillMaxSize()) {
                            CompactHeader(current, isFavorite, toggleFavorite, headerArt) {
                                if (lyrics != null) {
                                    translateButton()
                                    CircleButton(Icons.Rounded.Share, tr("Share Lyrics", "Sözleri Paylaş"), onClick = {
                                        val l = lyrics
                                        val synced = l?.synced
                                        val i = synced?.indexOfLast { it.timeMs <= positionMs + 300 }?.coerceAtLeast(0) ?: 0
                                        val text = synced?.drop(i)?.take(2)?.map { it.text }?.filter { it.isNotBlank() }
                                            ?: l?.plain.orEmpty().lines().filter { it.isNotBlank() }.take(2)
                                        if (text.isNotEmpty()) shareLyrics(text, synced?.getOrNull(i)?.timeMs ?: positionMs)
                                    })
                                    Spacer(Modifier.width(10.dp))
                                    CircleButton(
                                        if (lyricsFull) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                                        if (lyricsFull) tr("Exit Full Screen", "Tam Ekrandan Çık") else tr("Full Screen", "Tam Ekran"),
                                        onClick = { lyricsFull = !lyricsFull },
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                            }
                            LyricsPanel(
                                lyrics, lyricsLoading, positionMs, connection::seekTo,
                                full = lyricsFull, onToggleFull = { lyricsFull = !lyricsFull }, onShare = shareLyrics,
                                translations = translations,
                            )
                        }
                        PanelMode.Queue -> Column(Modifier.fillMaxSize()) {
                            CompactHeader(current, isFavorite, toggleFavorite, headerArt)
                            QueuePanel(
                                upNext = upNext,
                                connection = connection,
                                shuffle = shuffle,
                                repeatMode = repeatMode,
                                accent = topColor,
                            )
                        }
                    }
                }

                // Full-screen lyrics: the controls step aside, only the lyrics stay.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !(mode == PanelMode.Lyrics && lyricsFull),
                    enter = androidx.compose.animation.expandVertically() + fadeIn(),
                    exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
                ) {
                Column {
                Spacer(Modifier.height(22.dp))
                val premiumIds by connection.quality.premiumIds.collectAsStateWithLifecycle()
                Scrubber(
                    positionMs, durationMs, onSeek = connection::seekTo,
                    badge = if (current.id in premiumIds) "256 kbps" else null,
                )
                Spacer(Modifier.height(18.dp))
                Transport(
                    isPlaying = isPlaying,
                    onPrevious = connection::skipPrevious,
                    onToggle = connection::togglePlayPause,
                    onNext = connection::skipNext,
                )
                Spacer(Modifier.height(22.dp))
                VolumeRow()
                }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToggleIcon(
                        Icons.Rounded.FormatQuote, tr("Lyrics", "Sözler"), mode == PanelMode.Lyrics, topColor,
                        dimmed = !lyricsLoading && lyrics == null,
                    ) { mode = if (mode == PanelMode.Lyrics) PanelMode.Artwork else PanelMode.Lyrics }
                    ToggleIcon(Icons.Rounded.Airplay, tr("Output", "Çıkış"), false, topColor) { openOutputSwitcher(context) }
                    ToggleIcon(
                        Icons.AutoMirrored.Rounded.QueueMusic, tr("Playing Next", "Sıradaki"), mode == PanelMode.Queue, topColor,
                    ) { mode = if (mode == PanelMode.Queue) PanelMode.Artwork else PanelMode.Queue }
                }
                Spacer(Modifier.height(8.dp))
            }
            }
        }
    }
}

/**
 * iOS 26/27 full-bleed artwork with a truly progressive blur: one pre-rendered image
 * (see [ProgressiveArtTransformation]) whose blur grows row by row, with a sharp
 * high-resolution cover on top that melts into it where the blur is still faint.
 */
@Composable
private fun FullBleedArtwork(
    url: String?,
    palette: ArtworkPalette,
    sharp: Boolean,
    @Suppress("UNUSED_PARAMETER") isPlaying: Boolean,
    artModifier: Modifier,
) {
    val sharpAlpha by animateFloatAsState(if (sharp) 1f else 0f, tween(420), label = "sharp")
    val context = androidx.compose.ui.platform.LocalContext.current
    val backdrop = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .size(544)
            .transformations(ProgressiveArtTransformation(CoverAspect))
            .build()
    }

    Box(Modifier.fillMaxSize().background(palette.bottom)) {
        // 1. The progressive-blur backdrop, filling the sheet from the top.
        AsyncImage(
            model = backdrop,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. The sharp cover (full resolution) over the top part; it dissolves into the
        //    backdrop where the backdrop's blur is still barely there, so no edge shows.
        Box(
            artModifier
                .fillMaxWidth()
                .aspectRatio(CoverAspect)
                .graphicsLayer {
                    alpha = sharpAlpha
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            0.82f to Color.Black,
                            0.9f to Color.Black.copy(alpha = 0.65f),
                            0.96f to Color.Black.copy(alpha = 0.2f),
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 3. A shade toward the cover's colour low down, for the controls. Bright covers
        //    get darkened more, so white text always reads (Apple does the same).
        val bright = maxOf(palette.bottom.luminance(), palette.top.luminance()).coerceIn(0f, 1f)
        val dim = 0.18f + 0.5f * bright
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.56f to Color.Black.copy(alpha = dim * 0.7f),
                        1f to Color.Black.copy(alpha = dim),
                    )
                )
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        0.75f to palette.bottom.copy(alpha = 0.22f * (1f - bright)),
                        1f to palette.bottom.copy(alpha = 0.5f * (1f - bright)),
                    )
                ),
        )

        // 4. Lyrics / queue: a fully blurred, deeper backing so text reads crisply.
        if (sharpAlpha < 1f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - sharpAlpha }) {
                com.enes.liquidtube.ui.components.BlurredImage(url, Modifier.fillMaxSize()) {
                    scaleX = 1.3f
                    scaleY = 1.3f
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to palette.bottom.copy(alpha = 0.5f),
                                0.5f to palette.bottom.copy(alpha = 0.6f),
                                1f to palette.bottom.copy(alpha = 0.85f),
                            )
                        ),
                )
            }
        }
    }
}

/**
 * Cover box aspect. Like Apple Music, the art is cropped taller than square (losing a
 * little at the sides) so it reaches down to the title before the blur takes over.
 */
private const val CoverAspect = 0.76f

@Composable
private fun SongMoreMenu(song: SongItem, connection: PlayerConnection, onCollapse: () -> Unit) {
    val menus = com.enes.liquidtube.ui.components.LocalMenus.current ?: return
    val overlay = com.enes.liquidtube.ui.components.LocalOverlay.current
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val sleepAt by connection.sleepAtMs.collectAsStateWithLifecycle()
    val speed by connection.speed.collectAsStateWithLifecycle()
    val eq by connection.settings.eqPreset.collectAsStateWithLifecycle()
    val soundCheck by connection.settings.soundCheck.collectAsStateWithLifecycle()
    // Tap opens the menu; hold opens it too and lets you steer it like a joystick.
    CircleButton(
        Icons.Rounded.MoreHoriz, tr("More", "Daha Fazla"),
        modifier = Modifier.onGloballyPositioned { coords = it }.menuButton(Offset(0f, 40f)) {
            val at = coords?.localToRoot(Offset(0f, 40f)) ?: Offset.Zero

            val sleepLabel = when {
                sleepAt == null -> tr("Sleep Timer", "Uyku Zamanlayıcısı")
                sleepAt == -1L -> tr("Sleep Timer: End of Song", "Uyku Zamanlayıcısı: Şarkı Sonu")
                else -> tr("Sleep Timer", "Uyku Zamanlayıcısı") + ": ${((sleepAt!! - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)} " + tr("min", "dk")
            }
            val extra = listOf(
                MenuAction(sleepLabel, Icons.Rounded.Bedtime) {
                    overlay.showMenu(
                        com.enes.liquidtube.ui.components.MenuSpec(
                            title = tr("Sleep Timer", "Uyku Zamanlayıcısı"),
                            subtitle = tr("Pause playback after", "Şu süre sonra durdur"),
                            groups = listOf(
                                listOf(15, 30, 45, 60).map { m -> MenuAction(tr("$m minutes", "$m dakika"), Icons.Rounded.Timer) { connection.setSleepTimer(m) } } +
                                    MenuAction(tr("End of This Song", "Bu Şarkının Sonu"), Icons.Rounded.MusicNote) { connection.setSleepTimer(0) },
                            ) + if (sleepAt != null) listOf(listOf(MenuAction(tr("Turn Off", "Kapat"), Icons.Rounded.TimerOff, destructive = true) { connection.setSleepTimer(null) })) else emptyList(),
                        ),
                        at,
                    )
                },
MenuAction(
                    "Equalizer: " + (com.enes.liquidtube.playback.AudioFx.PRESETS.firstOrNull { it.id == eq }?.label ?: tr("Off", "Kapalı")),
                    Icons.Rounded.GraphicEq,
                ) {
                    overlay.showMenu(
                        com.enes.liquidtube.ui.components.MenuSpec(
                            title = tr("Equalizer", "Ekolayzır"),
                            subtitle = if (soundCheck) tr("Sound Check is on", "Ses Dengeleme açık") else null,
                            groups = listOf(
                                com.enes.liquidtube.playback.AudioFx.PRESETS.map { p ->
                                    MenuAction(if (p.id == eq) "${p.label}  ✓" else p.label, Icons.Rounded.GraphicEq) {
                                        connection.settings.setEqPreset(p.id)
                                    }
                                },
                                listOf(
                                    MenuAction(if (soundCheck) tr("Sound Check  ✓", "Ses Dengeleme  ✓") else tr("Sound Check", "Ses Dengeleme"), Icons.Rounded.Equalizer) {
                                        connection.settings.setSoundCheck(!soundCheck)
                                    },
                                ),
                            ),
                        ),
                        at,
                    )
                },
                MenuAction(if (speed == 1f) tr("Playback Speed", "Çalma Hızı") else tr("Playback Speed: ${speed}×", "Çalma Hızı: ${speed}×"), Icons.Rounded.Speed) {
                    overlay.showMenu(
                        com.enes.liquidtube.ui.components.MenuSpec(
                            title = tr("Playback Speed", "Çalma Hızı"),
                            groups = listOf(
                                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).map { v ->
                                    MenuAction(if (v == speed) "${v}×  ✓" else "${v}×", Icons.Rounded.Speed) { connection.setSpeed(v) }
                                },
                            ),
                        ),
                        at,
                    )
                },
            )
            menus.song(song, extra = extra, onNavigate = onCollapse)
        },
        onClick = null,
    )
}

/** Android's system output picker (speaker / Bluetooth / cast), our AirPlay. */
private fun openOutputSwitcher(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        val shown = runCatching { MediaRouter2.getInstance(context).showSystemOutputSwitcher() }.getOrDefault(false)
        if (shown) return
    }
    runCatching {
        context.sendBroadcast(
            Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG")
                .setPackage("com.android.systemui")
                .putExtra("package_name", context.packageName)
        )
    }.onFailure {
        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ------------------------------------------------------------------ pieces

/** Landscape backdrop: the cover blurred across the whole screen, toned for white text. */
@Composable
private fun LandscapeBackdrop(url: String?, palette: ArtworkPalette) {
    val bright = maxOf(palette.bottom.luminance(), palette.top.luminance()).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize().background(palette.bottom)) {
        com.enes.liquidtube.ui.components.BlurredImage(url, Modifier.fillMaxSize()) {
            scaleX = 1.25f
            scaleY = 1.25f
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to palette.bottom.copy(alpha = 0.3f),
                        1f to palette.bottom.copy(alpha = 0.62f),
                    )
                )
                .background(Color.Black.copy(alpha = 0.12f + 0.4f * bright)),
        )
    }
}

/**
 * Landscape left side: room for the cover (drawn by [LandscapeFlyingCover], which reports
 * here through [onSlot]) as big as the height allows, with the title under it.
 */
@Composable
private fun LandscapeCover(
    song: SongItem,
    isFavorite: Boolean,
    onArtist: (() -> Unit)?,
    onFavorite: () -> Unit,
    onSlot: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    more: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight - 72.dp).coerceAtLeast(120.dp)
        Column(Modifier.width(side)) {
            Box(Modifier.size(side).onPlaced(onSlot))
            Spacer(Modifier.height(14.dp))
            TitleRow(song, isFavorite, onArtist, onFavorite, more)
        }
    }
}

/** Landscape, lyrics/queue open: the song above the controls; the cover lands in the gap. */
@Composable
private fun LandscapeHeader(
    song: SongItem,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onSlot: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit,
    extra: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).onPlaced(onSlot))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = AppTheme.type.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, style = AppTheme.type.subhead, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        extra()
        CircleButton(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, tr("Favorite", "Favori"), onClick = onFavorite)
    }
}

/** The landscape cover image itself, wherever it currently is. */
@Composable
private fun LandscapeFlyingCover(url: String?, modifier: Modifier, artModifier: Modifier, corner: Dp, shadow: Dp) {
    val context = LocalContext.current
    // A fixed request size: during the open animation the cover has no settled size yet,
    // and a size-dependent request could stall on the placeholder.
    val request = remember(url) { ImageRequest.Builder(context).data(url).size(720).build() }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.shadow(shadow, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.6f)),
    ) {
        Box(artModifier.fillMaxSize().clip(shape).background(Color.White.copy(alpha = 0.08f))) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BigArtwork(url: String?, isPlaying: Boolean, modifier: Modifier, artModifier: Modifier) {
    // Apple's signature: artwork shrinks back when paused, springs forward on play.
    val scale by animateFloatAsState(
        if (isPlaying) 1f else 0.78f,
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "artScale",
    )
    Box(modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(1f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(12.dp),
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor = Color.Black.copy(alpha = 0.6f),
                ),
        ) {
            Artwork(url, artModifier.fillMaxSize(), corner = 12.dp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleRow(
    song: SongItem,
    isFavorite: Boolean,
    onArtist: (() -> Unit)?,
    onFavorite: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = AppTheme.type.title3.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier.basicMarquee(initialDelayMillis = 2500),
            )
            Text(
                song.artists,
                style = AppTheme.type.title3.copy(fontWeight = FontWeight.Normal, fontSize = 22.sp),
                color = LocalTint.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onArtist != null) Modifier.clickable(onClick = onArtist) else Modifier,
            )
        }
        Spacer(Modifier.width(12.dp))
        CircleButton(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, tr("Favorite", "Favori"), onClick = onFavorite)
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun CompactHeader(
    song: SongItem,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    artModifier: Modifier,
    extra: @Composable () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.thumbnailUrl, artModifier.size(64.dp), corner = 8.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = AppTheme.type.headline, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, style = AppTheme.type.subhead, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        extra()
        CircleButton(if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, tr("Favorite", "Favori"), onClick = onFavorite)
    }
}

@Composable
private fun CircleButton(icon: ImageVector, description: String, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier
            .size(40.dp)
            .then(if (onClick != null) Modifier.bounceClick(pressedScale = 0.85f, onClick = onClick) else Modifier)
            .glassChip(tint = Color.White.copy(alpha = 0.18f), rim = 0.6f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit, badge: String? = null) {
    var preview by remember { mutableStateOf<Long?>(null) }
    val duration = durationMs.coerceAtLeast(1L)
    val shown = (preview ?: positionMs).coerceIn(0L, duration)

    PillSlider(
        value = shown.toFloat() / duration,
        onValueChange = { preview = (it * duration).toLong() },
        onValueChangeFinished = {
            onSeek((it * duration).toLong())
            preview = null
        },
    )
    Spacer(Modifier.height(4.dp))
    val timeStyle = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    Box(Modifier.fillMaxWidth()) {
        Text(formatMs(shown), style = timeStyle, color = Dim, modifier = Modifier.align(Alignment.CenterStart))
        // Like Apple's "Lossless" badge: shows when the stream is Premium quality.
        androidx.compose.animation.AnimatedVisibility(
            visible = badge != null,
            modifier = Modifier.align(Alignment.Center),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Row(
                Modifier
                    .glassChip(tint = Color.White.copy(alpha = 0.14f), rim = 0.3f)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.GraphicEq, null, tint = Dim, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(badge.orEmpty(), style = AppTheme.type.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Dim)
            }
        }
        Text(
            if (durationMs > 0) "-" + formatMs(durationMs - shown) else "--:--",
            style = timeStyle, color = Dim, modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** Apple-style thin slider: a pill track that thickens while you drag it. */
@Composable
private fun PillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value) }
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val shown = if (dragging) dragValue else value
    val trackHeight by animateDpAsState(if (dragging) 12.dp else 7.dp, label = "track")
    val tint = LocalTint.current

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragValue = (o.x / size.width).coerceIn(0f, 1f)
                        change(dragValue)
                    },
                    onDragEnd = {
                        dragging = false
                        finish(dragValue)
                    },
                    onDragCancel = { dragging = false },
                ) { pointer, amount ->
                    pointer.consume()
                    dragValue = (dragValue + amount / size.width).coerceIn(0f, 1f)
                    change(dragValue)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val v = (o.x / size.width).coerceIn(0f, 1f)
                    change(v)
                    finish(v)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.24f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(shown.coerceIn(0f, 1f))
                    .background(if (dragging) Color.White else tint.copy(alpha = 0.92f)),
            )
        }
    }
}

@Composable
private fun Transport(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Landscape uses a smaller set, in proportion with the row of icons under it.
        val compact = LocalCompactControls.current
        val side = if (compact) 42.dp else 54.dp
        val main = if (compact) 56.dp else 74.dp
        TransportButton(Icons.Rounded.FastRewind, tr("Previous", "Önceki"), side, onPrevious)
        TransportButton(
            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (isPlaying) tr("Pause", "Duraklat") else tr("Play", "Çal"),
            main,
            onToggle,
        )
        TransportButton(Icons.Rounded.FastForward, tr("Next", "Sonraki"), side, onNext)
    }
}

@Composable
private fun TransportButton(icon: ImageVector, description: String, size: Dp, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, label = "tp")
    val bg by animateColorAsState(if (pressed) Color.White.copy(alpha = 0.12f) else Color.Transparent, label = "tpbg")
    Box(
        Modifier
            .size(size + if (LocalCompactControls.current) 18.dp else 26.dp)
            .clip(CircleShape)
            .background(bg)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, description, tint = Color.White,
            modifier = Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale },
        )
    }
}

@Composable
private fun VolumeRow() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()) }

    // Keep in sync with hardware volume keys.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = Dim, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        PillSlider(
            value = volume,
            onValueChange = {
                volume = it
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).roundToInt(), 0)
            },
            onValueChangeFinished = {},
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Dim, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ToggleIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    accent: Color,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.9f) else Color.Transparent, label = "tbg")
    val tint by animateColorAsState(
        when {
            active -> accent
            dimmed -> LocalTint.current.copy(alpha = 0.3f)
            else -> LocalTint.current.copy(alpha = 0.9f)
        },
        label = "ttint",
    )
    val compact = LocalCompactControls.current
    Box(
        Modifier
            .size(if (compact) 44.dp else 50.dp)
            .bounceClick(pressedScale = 0.85f, onClick = onClick)
            .then(if (active) Modifier.glassChip(tint = bg, rim = 0.5f) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(if (compact) 24.dp else 27.dp))
    }
}

// ------------------------------------------------------------------- queue

@Composable
private fun QueuePanel(
    upNext: List<QueueEntry>,
    connection: PlayerConnection,
    shuffle: Boolean,
    repeatMode: Int,
    accent: Color,
) {
    val autoplay by connection.settings.autoplay.collectAsStateWithLifecycle()
    val history by connection.history.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    // The last song swiped away, offered back for a few seconds.
    var undo by remember { mutableStateOf<QueueEntry?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(5_000)
            undo = null
        }
    }
    val haptic = LocalHapticFeedback.current
    val rowPx = with(LocalDensity.current) { QueueRowHeight.toPx() }

    // Drag-to-reorder state (positions within the up-next list).
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestUpNext by rememberUpdatedState(upNext)
    val listState = rememberLazyListState()
    // Where the dragged row started (in the list's viewport) and how far the list has
    // scrolled under it since, so dragging near an edge can scroll the list.
    var dragStartTop by remember { mutableFloatStateOf(0f) }
    var scrolledSince by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(dragFrom >= 0) {
        if (dragFrom < 0) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val viewport = listState.layoutInfo.viewportSize.height.toFloat()
            if (viewport <= 0f) continue
            val top = dragStartTop - scrolledSince + dragOffset
            val edge = rowPx * 1.2f
            val maxStep = rowPx * 0.22f
            val step = when {
                top < edge -> -maxStep * ((edge - top) / edge).coerceIn(0f, 1f)
                top + rowPx > viewport - edge -> maxStep * ((top + rowPx - (viewport - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (step != 0f) {
                val moved = listState.scrollBy(step)
                // Keep the row under the finger while the list moves beneath it.
                scrolledSince += moved
                dragOffset += moved
            }
        }
    }
    val dragTarget = if (dragFrom < 0) -1
        else (dragFrom + (dragOffset / rowPx).roundToInt()).coerceIn(0, (upNext.size - 1).coerceAtLeast(0))

    Column(Modifier.fillMaxSize()) {
        // iOS 26: three glass capsules.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QueueToggle(Icons.Rounded.Shuffle, tr("Shuffle", "Karıştır"), shuffle, accent, Modifier.weight(1f)) { connection.toggleShuffle() }
            QueueToggle(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                tr("Repeat", "Tekrarla"), repeatMode != Player.REPEAT_MODE_OFF, accent, Modifier.weight(1f),
            ) { connection.cycleRepeat() }
            QueueToggle(Icons.Rounded.AllInclusive, tr("Autoplay", "Otomatik Oynat"), autoplay, accent, Modifier.weight(1f)) { connection.toggleAutoplay() }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(tr("Playing Next", "Sıradaki"), style = AppTheme.type.headline, color = Color.White)
                Text(
                    when {
                        upNext.isEmpty() && autoplay -> tr("Autoplay will keep the music going", "Otomatik Oynatma müziği sürdürecek")
                        upNext.isEmpty() -> tr("Nothing queued", "Sırada bir şey yok")
                        else -> tr("${upNext.size} songs", "${upNext.size} şarkı")
                    },
                    style = AppTheme.type.footnote, color = Dim,
                )
            }
            if (upNext.isNotEmpty()) {
                Text(
                    tr("Clear", "Temizle"),
                    style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = LocalTint.current,
                    modifier = Modifier.clip(CircleShape).clickable { connection.clearUpNext() }.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize().fadingEdges(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                // History: what already played in this queue, tap to go back to it.
                if (history.isNotEmpty()) {
                    item(key = "history-toggle") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showHistory = !showHistory }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, null, tint = Dim, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tr("History", "Geçmiş"), style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("${history.size}", style = AppTheme.type.subhead, color = Dim, modifier = Modifier.weight(1f))
                            Text(if (showHistory) tr("Hide", "Gizle") else tr("Show", "Göster"), style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = LocalTint.current)
                        }
                    }
                    if (showHistory) {
                        items(history, key = { "h-${it.index}-${it.song.id}" }) { entry ->
                            QueueRow(
                                song = entry.song,
                                modifier = Modifier.animateItem().graphicsLayer { alpha = 0.6f },
                                onClick = { connection.playAt(entry.index) },
                                handle = Modifier,
                                showHandle = false,
                            )
                        }
                        item(key = "history-end") {
                            Box(Modifier.animateItem().fillMaxWidth().padding(vertical = 6.dp).height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
                        }
                    }
                }
                itemsIndexed(upNext, key = { _, e -> "${e.index}-${e.song.id}" }) { pos, entry ->
                    val isDragged = pos == dragFrom
                    // The dragged row stays alive even when its original spot scrolls out of view.
                    val pinnable = androidx.compose.ui.layout.LocalPinnableContainer.current
                    DisposableEffect(isDragged) {
                        val handle = if (isDragged) pinnable?.pin() else null
                        onDispose { handle?.release() }
                    }
                    val shift = when {
                        dragFrom < 0 || isDragged -> 0f
                        pos in (dragFrom + 1)..dragTarget -> -rowPx
                        pos in dragTarget until dragFrom -> rowPx
                        else -> 0f
                    }
                    val animatedShift by animateFloatAsState(shift, spring(dampingRatio = 0.85f, stiffness = 600f), label = "shift")
                    SwipeToRemove(
                        enabled = dragFrom < 0,
                        onRemove = {
                            connection.removeQueueItem(entry.index)
                            undo = entry
                        },
                    ) {
                        QueueRow(
                            song = entry.song,
                            modifier = Modifier
                                .zIndex(if (isDragged) 1f else 0f)
                                .graphicsLayer {
                                    // While dragged, the floating copy above the list is what you see.
                                    translationY = if (isDragged) dragOffset else animatedShift
                                    alpha = if (isDragged) 0f else 1f
                                },
                            onClick = { connection.playAt(entry.index) },
                            handle = Modifier.pointerInput(entry.index, upNext.size) {
                                detectDragGestures(
                                    onDragStart = {
                                        val rowKey = "${entry.index}-${entry.song.id}"
                                        dragStartTop = listState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { it.key == rowKey }?.offset?.toFloat() ?: 0f
                                        scrolledSince = 0f
                                        dragFrom = pos
                                        dragOffset = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDragEnd = {
                                        // Worked out now, from the live drag state: values captured when
                                        // this gesture handler was created would be stale.
                                        val list = latestUpNext
                                        val to = if (dragFrom < 0) -1
                                            else (dragFrom + (dragOffset / rowPx).roundToInt()).coerceIn(0, (list.size - 1).coerceAtLeast(0))
                                        if (to >= 0 && to != dragFrom && to < list.size) connection.moveQueueItem(entry.index, list[to].index)
                                        dragFrom = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragFrom = -1; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                }
                            },
                        )
                    }
                }
            }
            // The dragged row, floating under your finger above the list (the list may scroll
            // its original spot out of view while you drag toward an edge).
            upNext.getOrNull(dragFrom)?.let { dragged ->
                QueueRow(
                    song = dragged.song,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = dragStartTop - scrolledSince + dragOffset
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = 18f
                            shape = RoundedCornerShape(14.dp)
                            clip = true
                        }
                        // A frosted backing so it reads as lifted above the rows it passes over.
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp),
                    onClick = {},
                    handle = Modifier,
                )
            }
            // "Removed · Undo", floating at the bottom for a few seconds.
            androidx.compose.animation.AnimatedVisibility(
                visible = undo != null,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                exit = fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                var last by remember { mutableStateOf<QueueEntry?>(null) }
                undo?.let { last = it }
                val gone = last ?: return@AnimatedVisibility
                // The whole pill is the Undo button (and it never lets a tap through to the
                // song row underneath).
                Row(
                    Modifier
                        .clip(CircleShape)
                        .clickable {
                            connection.insertQueueItem(gone.index, gone.song)
                            undo = null
                        }
                        .glassChip(tint = Color.Black.copy(alpha = 0.45f), rim = 0.4f)
                        .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr("Removed \u201C${gone.song.title}\u201D", "“${gone.song.title}” kaldırıldı"),
                        style = AppTheme.type.subhead, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        tr("Undo", "Geri Al"),
                        style = AppTheme.type.subhead.copy(fontWeight = FontWeight.Bold),
                        color = LocalTint.current,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private val QueueRowHeight = 62.dp

@Composable
private fun QueueToggle(icon: ImageVector, label: String, active: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f), label = "qbg")
    val tint by animateColorAsState(if (active) accent.darkenTo(0.2f) else Color.White, label = "qtint")
    Box(
        modifier
            .height(44.dp)
            .bounceClick(pressedScale = 0.92f, onClick = onClick)
            .glassChip(tint = bg, rim = if (active) 0.3f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * iOS-style swipe to remove: the row slides left and a red Remove button grows in
 * the space it leaves. A partial swipe parks the button (tap it); a long swipe removes.
 */
@Composable
private fun SwipeToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val revealPx = with(density) { 92.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val remove by rememberUpdatedState(onRemove)

    fun commitRemove() {
        scope.launch {
            offset.animateTo(-widthPx, tween(200))
            remove()
            offset.snapTo(0f)
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val dx = -offset.value
                        when {
                            dx > widthPx * 0.5f -> commitRemove()
                            dx > revealPx * 0.5f -> scope.launch { offset.animateTo(-revealPx, spring(dampingRatio = 0.8f)) }
                            else -> scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.8f)) }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offset.snapTo((offset.value + dragAmount).coerceIn(-widthPx, 0f)) }
                }
            },
    ) {
        val revealed = -offset.value
        if (revealed > 0.5f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(with(density) { revealed.toDp() })
                    .height(QueueRowHeight - 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFFF453A))
                    .clickable { commitRemove() },
                contentAlignment = Alignment.Center,
            ) {
                if (revealed > revealPx * 0.6f) {
                    Text(tr("Remove", "Kaldır"), style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                }
            }
        }
        Box(Modifier.offset { IntOffset(offset.value.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun QueueRow(song: SongItem, modifier: Modifier, onClick: () -> Unit, handle: Modifier, showHandle: Boolean = true) {
    val menus = com.enes.liquidtube.ui.components.LocalMenus.current
    // The song (tap to play, hold for its menu) and the grabber (drag to reorder) are
    // separate touch areas, so reordering never opens the menu.
    Row(
        modifier
            .fillMaxWidth()
            .height(QueueRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .pressMenu(onClick = onClick, menu = { menus?.song(song) }, highlight = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumbnailUrl, Modifier.size(46.dp), corner = 7.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.Medium), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists, style = AppTheme.type.footnote, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showHandle) Box(handle.size(width = 52.dp, height = QueueRowHeight), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DragHandle, tr("Reorder", "Sırala"), tint = Dim, modifier = Modifier.size(24.dp))
        }
    }
}

// ------------------------------------------------------------------ lyrics

private val TranslationStyle = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 22.sp,
    letterSpacing = (-0.2).sp,
)

private val LyricStyle = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = 30.sp,
    lineHeight = 37.sp,
    letterSpacing = (-0.6).sp,
)

@Composable
private fun LyricsPanel(
    lyrics: Lyrics?,
    loading: Boolean,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    full: Boolean,
    onToggleFull: () -> Unit,
    onShare: (List<String>, Long?) -> Unit,
    translations: List<String>? = null,
) {
    val synced = lyrics?.synced
    Box(Modifier.fillMaxSize()) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Dim, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
        lyrics == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("Lyrics aren’t available for this song.", "Bu şarkının sözleri yok."), style = AppTheme.type.body, color = Dim)
        }
        synced != null -> SyncedLyrics(synced, positionMs, onSeek, lyrics.source, onShare, translations)
        else -> LazyColumn(Modifier.fillMaxSize().fadingEdges()) {
            item {
                val plainLines = lyrics.plain.orEmpty().lines()
                if (translations != null && translations.size == plainLines.size) {
                    // Each line with its translation under it.
                    Column(Modifier.padding(vertical = 16.dp)) {
                        plainLines.forEachIndexed { i, l ->
                            Text(l, style = LyricStyle.copy(fontSize = 24.sp, lineHeight = 32.sp), color = Color.White.copy(alpha = 0.9f))
                            if (translations[i].isNotBlank()) {
                                Text(translations[i], style = TranslationStyle, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(bottom = 10.dp))
                            }
                        }
                    }
                } else {
                    Text(
                        lyrics.plain.orEmpty(),
                        style = LyricStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            item { LyricsSource(lyrics.source) }
        }
    }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, positionMs: Long, onSeek: (Long) -> Unit, source: String, onShare: (List<String>, Long?) -> Unit, translations: List<String>? = null) {
    val active = lines.indexOfLast { it.timeMs <= positionMs + 300 }
    // Positions arrive every 250ms; advance smoothly between them for word-by-word fill.
    var smoothPos by remember { mutableLongStateOf(positionMs) }
    val anchor = remember(positionMs) { positionMs to android.os.SystemClock.uptimeMillis() }
    LaunchedEffect(anchor) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            val elapsed = (android.os.SystemClock.uptimeMillis() - anchor.second).coerceAtMost(400)
            smoothPos = anchor.first + elapsed
        }
    }
    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScroll by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastUserScroll = System.currentTimeMillis() }

    LaunchedEffect(active) {
        if (active >= 0 && System.currentTimeMillis() - lastUserScroll > 3000) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(active, scrollOffset = -(viewport * 0.22f).toInt())
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().fadingEdges(),
        contentPadding = PaddingValues(top = 24.dp, bottom = 280.dp),
    ) {
        itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, line ->
            val isActive = i == active
            val alpha by animateFloatAsState(
                when {
                    isActive -> 1f
                    i < active -> 0.3f
                    else -> 0.42f
                },
                tween(350), label = "la",
            )
            val scale by animateFloatAsState(
                if (isActive) 1f else 0.95f,
                spring(stiffness = Spring.StiffnessLow), label = "ls",
            )
            val end = lines.getOrNull(i + 1)?.timeMs ?: (line.timeMs + 5000)
            val translated = translations?.getOrNull(i)?.takeIf { it.isNotBlank() && it != line.text.trim() }
            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .pointerInput(i) {
                        detectTapGestures(
                            onTap = { onSeek(line.timeMs) },
                            // Hold a line to share it (with the next one) as a lyrics card.
                            onLongPress = {
                                onShare(lines.drop(i).take(2).map { it.text }.filter { it.isNotBlank() }, line.timeMs)
                            },
                        )
                    }
                    .padding(vertical = 10.dp),
            ) {
            Text(
                if (isActive && line.text.isNotBlank()) karaoke(line.text, line.timeMs, end, smoothPos)
                else androidx.compose.ui.text.AnnotatedString(line.text.ifBlank { "• • •" }),
                style = LyricStyle,
                color = Color.White.copy(alpha = alpha),
                modifier = Modifier.fillMaxWidth(),
            )
            // The translation, smaller and softer, right under the line.
            if (translated != null) {
                Text(
                    translated,
                    style = TranslationStyle,
                    color = Color.White.copy(alpha = alpha * 0.62f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
            }
        }
        item { LyricsSource(source) }
    }
}

/**
 * Word-by-word fill for the current line (Apple's "karaoke" lyrics). Word timings aren't
 * in the lyrics data, so each word gets a share of the line's time by its length; sung
 * words light up fully, the current word brightens as it's sung, the rest stay dim.
 */
private fun karaoke(text: String, start: Long, end: Long, pos: Long): androidx.compose.ui.text.AnnotatedString {
    val words = Regex("""\S+\s*""").findAll(text).map { it.value }.toList()
    val total = words.sumOf { it.trim().length.coerceAtLeast(1) }.coerceAtLeast(1)
    // Lines often end with a pause; sing the words over ~85% of the gap.
    val singMs = ((end - start) * 0.85f).coerceIn(600f, 9000f)
    val t = ((pos - start) / singMs).coerceIn(0f, 1f) * total
    return androidx.compose.ui.text.buildAnnotatedString {
        var acc = 0f
        for (w in words) {
            val len = w.trim().length.coerceAtLeast(1)
            val f = ((t - acc) / len).coerceIn(0f, 1f)
            acc += len
            val alpha = 0.42f + 0.58f * f
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White.copy(alpha = alpha)))
            append(w)
            pop()
        }
    }
}

@Composable
private fun LyricsSource(source: String) {
    Text(
        tr("Lyrics from $source", "Sözler: $source"),
        style = AppTheme.type.footnote,
        color = Dim,
        modifier = Modifier.padding(top = 24.dp),
    )
}

/** Fades list content out at the top and bottom edges, like Apple's lyrics view. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                0.06f to Color.Black,
                0.82f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

// ----------------------------------------------------------------- helpers

@Immutable
private data class ArtworkPalette(
    val top: Color,
    val bottom: Color,
    val blobA: Color,
    val blobB: Color,
    val blobC: Color,
    val tint: Color = Color.White,
)

private val DefaultPalette = ArtworkPalette(
    Color(0xFF3A3A3C), Color(0xFF111113), Color(0xFF4A4A4E), Color(0xFF2A2A2E), Color(0xFF38383C),
)

/** Colors pulled from the artwork and darkened so white text stays legible. */
@Composable
private fun rememberArtworkPalette(url: String?): ArtworkPalette {
    val context = LocalContext.current
    var target by remember { mutableStateOf(DefaultPalette) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(96)
                    .allowHardware(false)
                    .build()
                (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
            }.getOrNull()
        } ?: return@LaunchedEffect
        val p = withContext(Dispatchers.Default) { Palette.from(bitmap).maximumColorCount(16).generate() }
        val dominant = Color(p.getDominantColor(0xFF3A3A3C.toInt()))
        val vibrant = Color(p.getVibrantColor(p.getLightVibrantColor(dominant.toArgb())))
        val muted = Color(p.getMutedColor(dominant.toArgb()))
        val deep = Color(p.getDarkMutedColor(p.getDarkVibrantColor(0xFF111113.toInt())))
        target = ArtworkPalette(
            top = dominant.darkenTo(0.36f),
            bottom = deep.darkenTo(0.10f),
            blobA = vibrant.darkenTo(0.42f),
            blobB = muted.darkenTo(0.30f),
            blobC = dominant.darkenTo(0.26f),
            tint = lerp(vibrant, Color.White, 0.6f),
        )
    }

    val spec = tween<Color>(900)
    val top by animateColorAsState(target.top, spec, label = "top")
    val bottom by animateColorAsState(target.bottom, spec, label = "bottom")
    val a by animateColorAsState(target.blobA, spec, label = "a")
    val b by animateColorAsState(target.blobB, spec, label = "b")
    val cc by animateColorAsState(target.blobC, spec, label = "c")
    val tint by animateColorAsState(target.tint, spec, label = "tint")
    return ArtworkPalette(top, bottom, a, b, cc, tint)
}

/** iOS-style living background: soft color blobs drifting slowly over a gradient. */
@Composable
private fun MeshBackground(palette: ArtworkPalette) {
    val transition = rememberInfiniteTransition(label = "mesh")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "t",
    )
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
        val w = size.width
        val h = size.height
        fun blob(color: Color, cx: Float, cy: Float, r: Float) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                radius = r,
                center = Offset(cx, cy),
            )
        }
        blob(palette.blobA, w * (0.25f + 0.18f * cos(t)), h * (0.22f + 0.08f * sin(t * 2)), w * 0.95f)
        blob(palette.blobB, w * (0.8f + 0.15f * sin(t)), h * (0.45f + 0.10f * cos(t)), w * 0.85f)
        blob(palette.blobC, w * (0.4f + 0.2f * sin(t + 1.3f)), h * (0.78f + 0.06f * cos(t * 1.5f)), w * 1.05f)
        // Gentle vignette keeps controls readable.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f))))
    }
}

private fun Color.darkenTo(maxLuminance: Float): Color {
    var c = this
    var i = 0
    while (c.luminance() > maxLuminance && i < 16) {
        c = lerp(c, Color.Black, 0.12f)
        i++
    }
    return c
}

/** Now Playing is always dark, so force light status/nav bar icons while it's open. */
@Composable
private fun ForceLightSystemBars() {
    val view = LocalView.current
    val isDarkTheme = AppTheme.colors.isDark
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = !isDarkTheme
            controller?.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
