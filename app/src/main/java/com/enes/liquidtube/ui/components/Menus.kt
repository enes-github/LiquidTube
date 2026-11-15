package com.enes.liquidtube.ui.components

import com.enes.liquidtube.ui.theme.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowCircleDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.blurredBackdrop
import com.enes.liquidtube.ui.glass.liquidGlass
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.menuGlass
import com.enes.innertube.models.CollectionItem
import com.enes.innertube.models.CollectionType
import com.enes.innertube.models.PlaylistOption
import com.enes.innertube.models.SongItem

// ======================================================================= model

class MenuAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

data class MenuSpec(
    val title: String? = null,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val roundArtwork: Boolean = false,
    /** Compact icon buttons across the top of the menu (iOS "medium" menu elements). */
    val quick: List<MenuAction> = emptyList(),
    val groups: List<List<MenuAction>>,
)

/** App-wide overlay state: context menu, add-to-playlist sheet, text prompt. */
@Stable
class OverlayController {
    internal var menu by mutableStateOf<Pair<MenuSpec, Offset>?>(null)
    internal var playlistSheet by mutableStateOf<List<SongItem>?>(null)
    internal var prompt by mutableStateOf<PromptSpec?>(null)

    /**
     * Joystick selection while a held finger steers an open menu. It works in steps, not
     * as a free pointer: every [stepY] of finger travel moves one row, every [stepX]
     * sideways moves one button in a multi-button row. The finger can be anywhere on screen;
     * only its movement counts, the dominant direction wins, and the ends don't overshoot.
     */
    internal var selected by mutableStateOf<MenuAction?>(null)
    internal var steering by mutableStateOf(false)
    private val targets = HashMap<MenuAction, Rect>()
    internal var panelBounds: Rect = Rect.Zero
    private var lastFinger: Offset? = null
    private var accX = 0f
    private var accY = 0f
    private var rememberedCol = 0
    internal var stepX = 120f
    internal var stepY = 90f

    /** Any menu, sheet or dialog is showing (it owns the back gesture). */
    val isActive: Boolean get() = menu != null || playlistSheet != null || prompt != null

    fun showMenu(spec: MenuSpec, at: Offset) { menu = spec to at; resetSteer() }
    fun dismissMenu() { menu = null; resetSteer() }
    fun addToPlaylist(songs: List<SongItem>) { playlistSheet = songs }
    fun prompt(spec: PromptSpec) { prompt = spec }

    internal fun register(action: MenuAction, bounds: Rect) { targets[action] = bounds }
    internal fun unregister(action: MenuAction) { targets.remove(action) }
    internal fun boundsOf(action: MenuAction): Rect? = targets[action]

    /** Items grouped into visual rows (top to bottom), each row left to right. */
    private fun rows(): List<List<MenuAction>> {
        val sorted = targets.entries.sortedWith(compareBy({ it.value.center.y }, { it.value.left }))
        val rows = mutableListOf<MutableList<Map.Entry<MenuAction, Rect>>>()
        for (e in sorted) {
            val row = rows.lastOrNull()
            if (row != null && kotlin.math.abs(row.first().value.center.y - e.value.center.y) < 8f) row += e else rows += mutableListOf(e)
        }
        return rows.map { r -> r.sortedBy { it.value.left }.map { it.key } }
    }

    /** Feed the finger's current position (root coordinates). */
    internal fun joystick(finger: Offset) {
        val last = lastFinger
        lastFinger = finger
        steering = true
        if (last == null) return
        val d = finger - last
        val rows = rows()
        if (rows.isEmpty()) return
        var r = selected?.let { s -> rows.indexOfFirst { s in it } } ?: -1
        var c = if (r >= 0) rows[r].indexOf(selected) else 0
        // In a row of buttons, sideways is what you want: short side steps, and leaving
        // the row vertically takes a more deliberate move.
        val inButtonRow = r >= 0 && rows[r].size > 1
        val sx = if (inButtonRow) stepX * 0.6f else stepX
        val sy = if (inButtonRow) stepY * 1.6f else stepY
        // The dominant direction wins; the other bleeds off gently, so diagonals don't jitter.
        if (kotlin.math.abs(d.y) >= kotlin.math.abs(d.x) * (if (inButtonRow) 1.4f else 1f)) {
            accY += d.y; accX *= 0.9f
        } else {
            accX += d.x; accY *= 0.75f
        }

        while (accY >= sy) {
            accY -= sy
            if (r < rows.lastIndex) r++ else accY = 0f
        }
        while (accY <= -sy) {
            accY += sy
            if (r > 0) r-- else if (r < 0) r = rows.lastIndex else accY = 0f
        }
        if (r < 0) return
        val row = rows[r]
        if (row.size > 1) {
            if (selected !in row) c = rememberedCol.coerceIn(0, row.lastIndex)
            while (accX >= sx) { accX -= sx; if (c < row.lastIndex) c++ else accX = 0f }
            while (accX <= -sx) { accX += sx; if (c > 0) c-- else accX = 0f }
            rememberedCol = c
        } else {
            c = 0
            accX = 0f
        }
        selected = row[c.coerceIn(0, row.lastIndex)]
    }

    /** Finger lifted after steering: run the selected item (if any). */
    internal fun releaseDrag() {
        val action = selected
        resetSteer()
        if (action != null) {
            dismissMenu()
            action.onClick()
        }
    }

    private fun resetSteer() {
        selected = null
        steering = false
        lastFinger = null
        accX = 0f
        accY = 0f
        rememberedCol = 0
    }

    internal fun cancelDrag() { resetSteer() }
}

data class PromptSpec(
    val title: String,
    val message: String? = null,
    val initial: String = "",
    val placeholder: String = "",
    val confirm: String,
    val destructive: Boolean = false,
    val onConfirm: (String) -> Unit,
)

val LocalOverlay = staticCompositionLocalOf { OverlayController() }

/** Cursor travel per finger travel: a little faster than 1:1 so small flicks reach far. */
private const val JOYSTICK_GAIN = 1.35f

// ================================================================ press modifier

private enum class PressOutcome { Tap, Cancel }

/** Waits for the pointer to lift (a tap) or to move / be stolen by a scroll (cancel). */
private suspend fun AwaitPointerEventScope.awaitTapOrCancel(down: PointerInputChange, slop: Float): PressOutcome {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return PressOutcome.Cancel
        if (change.changedToUpIgnoreConsumed()) return if (change.isConsumed) PressOutcome.Cancel else PressOutcome.Tap
        if (change.isConsumed || (change.position - down.position).getDistance() > slop) return PressOutcome.Cancel
    }
}

/**
 * After a long press has opened a menu, keep following the same finger: sliding over
 * the menu highlights rows (with a tick), lifting on one runs it. Lifting without having
 * moved leaves the menu open for normal taps, like iOS.
 */
private suspend fun AwaitPointerEventScope.followIntoMenu(
    down: PointerInputChange,
    overlay: OverlayController,
    slop: Float,
    toRoot: (Offset) -> Offset,
) {
    var moved = false
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!moved && (change.position - down.position).getDistance() > slop) moved = true
            if (moved) overlay.joystick(toRoot(change.position))
            change.consume()
            if (!change.pressed) {
                if (moved) overlay.releaseDrag()
                break
            }
        }
    } finally {
        overlay.cancelDrag()
    }
}

/**
 * Tap + long-press with iOS feel: a soft press highlight/scale, a haptic tick, and the
 * context menu popping from the finger. Keep holding and slide to pick an item.
 * [menu] is built lazily at long-press time.
 */
fun Modifier.pressMenu(
    onClick: (() -> Unit)?,
    menu: (() -> MenuSpec?)?,
    pressedScale: Float = 0.97f,
    highlight: Boolean = false,
): Modifier = composed {
    val overlay = LocalOverlay.current
    val haptic = LocalHapticFeedback.current
    val currentClick by rememberUpdatedState(onClick)
    val currentMenu by rememberUpdatedState(menu)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed && !highlight) pressedScale else 1f, spring(stiffness = 900f), label = "press")
    val highlightAlpha by animateFloatAsState(if (pressed && highlight) 1f else 0f, tween(if (pressed) 60 else 220), label = "hl")
    val fill = AppTheme.colors.fill

    this
        .onGloballyPositioned { coords = it }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (highlight) Modifier.background(fill.copy(alpha = fill.alpha * highlightAlpha)) else Modifier)
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapOrCancel(down, slop) }
                    when (early) {
                        PressOutcome.Tap -> currentClick?.invoke()
                        PressOutcome.Cancel -> Unit
                        null -> {
                            val spec = currentMenu?.invoke()
                            if (spec == null) {
                                // No menu here: a slow tap is still a tap.
                                if (awaitTapOrCancel(down, slop) == PressOutcome.Tap) currentClick?.invoke()
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pressed = false
                                val c = coords
                                val toRoot: (Offset) -> Offset = { p -> if (c != null && c.isAttached) c.localToRoot(p) else p }
                                overlay.showMenu(spec, toRoot(down.position))
                                followIntoMenu(down, overlay, slop, toRoot)
                            }
                        }
                    }
                } finally {
                    pressed = false
                }
            }
        }
}

/**
 * For ••• buttons: tap opens the menu below the button; holding opens it too and lets
 * the finger slide straight onto an item.
 */
fun Modifier.menuButton(anchor: Offset = Offset(0f, 30f), spec: () -> MenuSpec?): Modifier = composed {
    val overlay = LocalOverlay.current
    val haptic = LocalHapticFeedback.current
    val currentSpec by rememberUpdatedState(spec)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(stiffness = 900f), label = "press")
    this
        .onGloballyPositioned { coords = it }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                pressed = true
                try {
                    val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitTapOrCancel(down, slop) }
                    if (early == PressOutcome.Cancel) return@awaitEachGesture
                    val s = currentSpec() ?: return@awaitEachGesture
                    val c = coords
                    val toRoot: (Offset) -> Offset = { p -> if (c != null && c.isAttached) c.localToRoot(p) else p }
                    if (early == null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressed = false
                    overlay.showMenu(s, toRoot(anchor))
                    if (early == null) followIntoMenu(down, overlay, slop, toRoot)
                } finally {
                    pressed = false
                }
            }
        }
}

// ================================================================ menu factory

/** Builds the menus for songs and collections, wired to playback, library and playlists. */
class MenuFactory(
    private val connection: PlayerConnection,
    private val nav: NavActions,
    private val overlay: OverlayController,
) {
    fun song(song: SongItem, extra: List<MenuAction> = emptyList(), onNavigate: () -> Unit = {}): MenuSpec {
        val isFavorite = connection.library.favorites.value.any { it.id == song.id }
        val download = connection.downloads.downloads.value[song.id]
        val loggedIn = connection.auth.isLoggedIn.value
        val quick = listOf(
            MenuAction(tr("Play Next", "Sıradakine Ekle"), Icons.AutoMirrored.Rounded.QueueMusic) { connection.playNext(song) },
            MenuAction(tr("Play Last", "Sona Ekle"), Icons.AutoMirrored.Rounded.PlaylistAdd) { connection.addToQueue(song) },
            MenuAction(if (isFavorite) tr("Unfavorite", "Favorilerden Çıkar") else tr("Favorite", "Favori"), if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarOutline) {
                connection.toggleFavorite(song)
            },
            MenuAction(tr("Share", "Paylaş"), Icons.Rounded.Share) {
                connection.share(song.title, "https://music.youtube.com/watch?v=${song.id}")
            },
        )
        val library = buildList {
            add(MenuAction(tr("Start Station", "İstasyon Başlat"), Icons.Rounded.Sensors) { connection.playSong(song) })
            if (loggedIn) add(MenuAction(tr("Add to a Playlist…", "Çalma Listesine Ekle…"), Icons.AutoMirrored.Rounded.PlaylistAdd) { overlay.addToPlaylist(listOf(song)) })
            if (download == null) {
                add(MenuAction(tr("Download", "İndir"), Icons.Rounded.ArrowCircleDown) { connection.downloads.download(listOf(song)) })
            } else {
                add(MenuAction(if (download.isDone) tr("Remove Download", "İndirmeyi Kaldır") else tr("Cancel Download", "İndirmeyi İptal Et"), Icons.Rounded.RemoveCircleOutline, destructive = download.isDone) {
                    connection.downloads.remove(song.id)
                })
            }
        }
        val go = buildList {
            song.albumId?.let { id -> add(MenuAction(tr("Go to Album", "Albüme Git"), Icons.Rounded.Album) { onNavigate(); nav.openCollection(id) }) }
            song.artistId?.let { id -> add(MenuAction(tr("Go to Artist", "Sanatçıya Git"), Icons.Rounded.Person) { onNavigate(); nav.openCollection(id) }) }
        }
        return MenuSpec(
            title = song.title,
            subtitle = song.artists,
            artworkUrl = song.thumbnailUrl,
            quick = quick,
            groups = listOf(library, go, extra).filter { it.isNotEmpty() },
        )
    }

    fun collection(item: CollectionItem, extra: List<MenuAction> = emptyList()): MenuSpec {
        val loggedIn = connection.auth.isLoggedIn.value
        val playable = item.type != CollectionType.ARTIST
        val share = MenuAction(tr("Share", "Paylaş"), Icons.Rounded.Share) {
            val url = when (item.type) {
                CollectionType.PLAYLIST -> "https://music.youtube.com/playlist?list=${item.browseId.removePrefix("VL")}"
                CollectionType.ARTIST -> "https://music.youtube.com/channel/${item.browseId}"
                CollectionType.ALBUM -> "https://music.youtube.com/browse/${item.browseId}"
            }
            connection.share(item.title, url)
        }
        val quick = buildList {
            add(MenuAction(tr("Play", "Çal"), Icons.Rounded.PlayArrow) { connection.playCollection(item.browseId) })
            add(MenuAction(tr("Shuffle", "Karıştır"), Icons.Rounded.Shuffle) { connection.playCollection(item.browseId, shuffle = true) })
            if (playable) add(MenuAction(tr("Download", "İndir"), Icons.Rounded.ArrowCircleDown) { connection.downloadCollection(item.browseId) })
            add(share)
        }
        val library = buildList {
            if (loggedIn && playable) add(MenuAction(tr("Add to Library", "Kitaplığa Ekle"), Icons.Rounded.LibraryAdd) {
                connection.savePlaylist(item.browseId, save = true)
            })
        }
        return MenuSpec(
            title = item.title,
            subtitle = item.subtitle,
            artworkUrl = item.thumbnailUrl,
            roundArtwork = item.type == CollectionType.ARTIST,
            quick = quick,
            groups = listOf(library, extra).filter { it.isNotEmpty() },
        )
    }
}

val LocalMenus = staticCompositionLocalOf<MenuFactory?> { null }

// ================================================================== overlay host

/** Draws whichever overlay is active. Place once, above everything; [backdrop] is the whole screen. */
@Composable
fun OverlayHost(controller: OverlayController, connection: PlayerConnection, backdrop: GlassBackdrop) {
    ContextMenuLayer(controller, backdrop)
    PlaylistSheetLayer(controller, connection, backdrop)
    PromptLayer(controller, backdrop)
}

/** Blurred, dimmed copy of the screen behind an overlay; [progress] 0..1 fades it. */
@Composable
private fun OverlayScrim(backdrop: GlassBackdrop, progress: () -> Float, onTap: () -> Unit) {
    val dark = AppTheme.colors.isDark
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
            .blurredBackdrop(backdrop, 22.dp)
            .background(Color.Black.copy(alpha = if (dark) 0.2f else 0.1f))
            .pointerInput(Unit) { detectTapGestures { onTap() } },
    )
}

@Composable
private fun ContextMenuLayer(controller: OverlayController, backdrop: GlassBackdrop) {
    val state = controller.menu
    var shown by remember { mutableStateOf<Pair<MenuSpec, Offset>?>(null) }
    val progress = remember { Animatable(0f) }
    if (state != null) shown = state

    LaunchedEffect(state) {
        if (state != null) {
            progress.snapTo(progress.value.coerceAtMost(0.3f))
            progress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 520f))
        } else if (shown != null) {
            progress.animateTo(0f, tween(170))
            shown = null
        }
    }
    val current = shown ?: return
    BackHandler(enabled = state != null) { controller.dismissMenu() }

    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        OverlayScrim(backdrop, { progress.value }) { controller.dismissMenu() }

        val (spec, anchor) = current
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                MenuPanel(spec, controller, backdrop) { action ->
                    controller.dismissMenu()
                    action.onClick()
                }
            },
        ) { measurables, constraints ->
            val margin = with(density) { 14.dp.roundToPx() }
            val topSafe = with(density) { 56.dp.roundToPx() }
            val maxH = constraints.maxHeight - topSafe - margin * 4
            val panel = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = maxH))
            val x = (anchor.x.toInt() - with(density) { 36.dp.roundToPx() })
                .coerceIn(margin, (constraints.maxWidth - panel.width - margin).coerceAtLeast(margin))
            val gap = with(density) { 12.dp.roundToPx() }
            val below = anchor.y.toInt() + gap
            val fitsBelow = below + panel.height < constraints.maxHeight - margin * 4
            val y = if (fitsBelow) below else (anchor.y.toInt() - panel.height - gap).coerceAtLeast(topSafe)
            val pivotX = ((anchor.x - x) / panel.width).coerceIn(0f, 1f)
            val pivotY = if (fitsBelow) 0f else 1f
            layout(constraints.maxWidth, constraints.maxHeight) {
                panel.placeWithLayer(x, y) {
                    val p = progress.value
                    val s = 0.35f + 0.65f * p
                    scaleX = s
                    scaleY = s
                    alpha = (p * 1.8f).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(pivotX, pivotY)
                }
            }
        }
    }
}

@Composable
private fun MenuPanel(
    spec: MenuSpec,
    controller: OverlayController,
    backdrop: GlassBackdrop,
    onAction: (MenuAction) -> Unit,
) {
    val c = AppTheme.colors
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    val scroll = rememberScrollState()
    var panelBounds by remember { mutableStateOf(Rect.Zero) }
    val hovered = controller.selected
    val density = LocalDensity.current
    LaunchedEffect(density) {
        controller.stepY = with(density) { 30.dp.toPx() }
        controller.stepX = with(density) { 44.dp.toPx() }
    }

    LaunchedEffect(hovered) {
        val action = hovered ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        // Keep the selected item visible in tall (scrolling) menus.
        val r = controller.boundsOf(action) ?: return@LaunchedEffect
        val b = panelBounds
        val margin = with(density) { 12.dp.toPx() }
        when {
            r.bottom > b.bottom - margin -> scroll.animateScrollBy(r.bottom - b.bottom + margin * 2)
            r.top < b.top + margin -> scroll.animateScrollBy(r.top - b.top - margin * 2)
        }
    }

    Column(
        Modifier
            .width(268.dp)
            .shadow(36.dp, shape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.45f))
            .liquidGlass(backdrop, menuGlass(c.isDark), corner = 26.dp)
            .clip(shape)
            .onGloballyPositioned { panelBounds = it.boundsInRoot(); controller.panelBounds = panelBounds }
            .verticalScroll(scroll),
    ) {
        if (spec.title != null) {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (spec.artworkUrl != null) {
                    Artwork(spec.artworkUrl, Modifier.size(46.dp), corner = if (spec.roundArtwork) 23.dp else 8.dp, small = true)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(spec.title, style = AppTheme.type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    spec.subtitle?.let {
                        Text(it, style = AppTheme.type.footnote, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MenuHairline()
        }
        if (spec.quick.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(74.dp)) {
                spec.quick.forEachIndexed { i, action ->
                    QuickButton(action, hovered === action, controller, Modifier.weight(1f)) { onAction(action) }
                    if (i != spec.quick.lastIndex) {
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(c.separator.copy(alpha = 0.45f)))
                    }
                }
            }
            if (spec.groups.isNotEmpty()) GroupGap()
        }
        spec.groups.forEachIndexed { gi, group ->
            group.forEachIndexed { i, action ->
                MenuRow(action, hovered === action, controller) { onAction(action) }
                if (i != group.lastIndex) MenuHairline(inset = 18.dp)
            }
            if (gi != spec.groups.lastIndex) GroupGap()
        }
    }
}

/** Registers a menu item's on-screen bounds so a sliding finger can find it. */
private fun Modifier.menuTarget(controller: OverlayController, action: MenuAction): Modifier = composed {
    DisposableEffect(action) { onDispose { controller.unregister(action) } }
    onGloballyPositioned { controller.register(action, it.boundsInRoot()) }
}

@Composable
private fun MenuHairline(inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(AppTheme.colors.separator.copy(alpha = 0.5f)),
    )
}

@Composable
private fun GroupGap() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(if (AppTheme.colors.isDark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.045f)),
    )
}

@Composable
private fun hoverFill(): Color = if (AppTheme.colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)

@Composable
private fun MenuRow(action: MenuAction, hovered: Boolean, controller: OverlayController, onClick: () -> Unit) {
    val c = AppTheme.colors
    val tint = if (action.destructive) Color(0xFFFF453A) else c.label
    val hover by animateFloatAsState(if (hovered) 1f else 0f, tween(if (hovered) 50 else 160), label = "hover")
    val fill = hoverFill()
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .menuTarget(controller, action)
            .background(fill.copy(alpha = fill.alpha * hover))
            .pressMenu(onClick = onClick, menu = null, highlight = true)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(action.label, style = AppTheme.type.body, color = tint, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Icon(action.icon, null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun QuickButton(action: MenuAction, hovered: Boolean, controller: OverlayController, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val tint = if (action.destructive) Color(0xFFFF453A) else c.label
    val hover by animateFloatAsState(if (hovered) 1f else 0f, tween(if (hovered) 50 else 160), label = "hover")
    val fill = hoverFill()
    Column(
        modifier
            .fillMaxHeight()
            .menuTarget(controller, action)
            .background(fill.copy(alpha = fill.alpha * hover))
            .pressMenu(onClick = onClick, menu = null, highlight = true)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, null, tint = tint, modifier = Modifier.size(23.dp).graphicsLayer {
            val s = 1f + 0.1f * hover
            scaleX = s; scaleY = s
        })
        Spacer(Modifier.height(6.dp))
        Text(
            action.label,
            style = AppTheme.type.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================================================ add-to-playlist sheet

@Composable
private fun PlaylistSheetLayer(controller: OverlayController, connection: PlayerConnection, backdrop: GlassBackdrop) {
    val songs = controller.playlistSheet
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = songs != null
    var shownSongs by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    if (songs != null) shownSongs = songs
    if (!visible.currentState && !visible.targetState && visible.isIdle) return

    BackHandler(enabled = songs != null) { controller.playlistSheet = null }
    val c = AppTheme.colors
    var options by remember(shownSongs) { mutableStateOf<List<PlaylistOption>?>(null) }
    LaunchedEffect(shownSongs) {
        options = null
        options = connection.playlistOptions(shownSongs)
    }
    val close = { controller.playlistSheet = null }
    val fade by animateFloatAsState(if (songs != null) 1f else 0f, tween(260), label = "sheetScrim")

    Box(Modifier.fillMaxSize()) {
        OverlayScrim(backdrop, { fade }) { close() }
        AnimatedVisibility(
            visibleState = visible,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it },
            exit = slideOutVertically(tween(240)) { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .clip(shape)
                    .liquidGlass(backdrop, menuGlass(c.isDark).copy(refraction = 0.dp, rim = 0f, edge = Color.Transparent), corner = 0.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                        .size(width = 38.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(c.tertiaryLabel),
                )
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        tr("Cancel", "İptal"),
                        style = AppTheme.type.body,
                        color = c.accent,
                        modifier = Modifier.align(Alignment.CenterStart).clickable { close() },
                    )
                    Text(tr("Add to Playlist", "Çalma Listesine Ekle"), style = AppTheme.type.headline, color = c.label, modifier = Modifier.align(Alignment.Center))
                }
                SheetRow(icon = Icons.Rounded.Add, label = tr("New Playlist…", "Yeni Çalma Listesi…"), accent = true) {
                    close()
                    controller.prompt(
                        PromptSpec(
                            title = tr("New Playlist", "Yeni Çalma Listesi"),
                            placeholder = tr("Playlist Name", "Çalma Listesi Adı"),
                            confirm = tr("Create", "Oluştur"),
                            onConfirm = { name -> connection.createPlaylist(name, shownSongs) },
                        )
                    )
                }
                when (val list = options) {
                    null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = c.secondaryLabel, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    else -> LazyColumn {
                        items(list, key = { it.playlistId }) { option ->
                            SheetRow(
                                artwork = option.thumbnailUrl,
                                label = option.title,
                                trailing = if (option.containsSongs) Icons.Rounded.Check else null,
                            ) {
                                connection.addToPlaylist(option.playlistId, option.title, shownSongs)
                                close()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    label: String,
    icon: ImageVector? = null,
    artwork: String? = null,
    accent: Boolean = false,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val c = AppTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressMenu(onClick = onClick, menu = null, highlight = true)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(9.dp)).background(c.fill), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = c.accent, modifier = Modifier.size(28.dp))
            }
        } else {
            Artwork(artwork, Modifier.size(54.dp), corner = 9.dp, small = true)
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = AppTheme.type.body, color = if (accent) c.accent else c.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { Icon(it, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
    }
}

// ===================================================================== prompt

@Composable
private fun PromptLayer(controller: OverlayController, backdrop: GlassBackdrop) {
    val spec = controller.prompt
    val visible = remember { MutableTransitionState(false) }
    visible.targetState = spec != null
    var shown by remember { mutableStateOf<PromptSpec?>(null) }
    if (spec != null) shown = spec
    val current = shown ?: return
    if (!visible.currentState && !visible.targetState && visible.isIdle) return

    var field by remember(current) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(current.initial, androidx.compose.ui.text.TextRange(current.initial.length)))
    }
    val text = field.text
    val focus = remember { FocusRequester() }
    val close = { controller.prompt = null }
    val c = AppTheme.colors
    BackHandler(enabled = spec != null) { close() }
    LaunchedEffect(current) { runCatching { focus.requestFocus() } }
    val fade by animateFloatAsState(if (spec != null) 1f else 0f, tween(220), label = "promptScrim")

    Box(Modifier.fillMaxSize()) {
        OverlayScrim(backdrop, { fade }) { }
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 600f), initialScale = 1.15f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.95f),
            ) {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .width(296.dp)
                        .liquidGlass(backdrop, menuGlass(c.isDark), corner = 28.dp)
                        .clip(shape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(22.dp))
                    Text(current.title, style = AppTheme.type.headline, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp))
                    current.message?.let {
                        Text(it, style = AppTheme.type.footnote, color = c.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    Box(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (c.isDark) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                    ) {
                        if (text.isEmpty()) Text(current.placeholder, style = AppTheme.type.body, color = c.tertiaryLabel)
                        BasicTextField(
                            value = field,
                            onValueChange = { field = it },
                            singleLine = true,
                            textStyle = AppTheme.type.body.copy(color = c.label),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (text.isNotBlank()) { close(); current.onConfirm(text.trim()) }
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PromptButton(tr("Cancel", "İptal"), primary = false, enabled = true, destructive = false, modifier = Modifier.weight(1f)) { close() }
                        PromptButton(current.confirm, primary = true, enabled = text.isNotBlank(), destructive = current.destructive, modifier = Modifier.weight(1f)) {
                            close(); current.onConfirm(text.trim())
                        }
                    }
                }
            }
        }
    }
}

/** iOS 26 alert buttons: capsules side by side. */
@Composable
private fun PromptButton(label: String, primary: Boolean, enabled: Boolean, destructive: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = AppTheme.colors
    val bg = when {
        !primary -> c.fill
        !enabled -> c.fill
        destructive -> Color(0xFFFF453A)
        else -> c.accent
    }
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .then(if (enabled) Modifier.bounceClick(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppTheme.type.headline,
            color = when {
                !enabled -> c.tertiaryLabel
                primary -> Color.White
                else -> c.label
            },
        )
    }
}
