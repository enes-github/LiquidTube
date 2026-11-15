@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.enes.liquidtube.ui.navigation

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.enes.liquidtube.playback.LocalPlayerConnection
import com.enes.liquidtube.playback.PlayerConnection
import com.enes.liquidtube.ui.account.AccountScreen
import com.enes.liquidtube.ui.account.LoginScreen
import com.enes.liquidtube.ui.browse.BrowseScreen
import com.enes.liquidtube.ui.collection.CollectionScreen
import com.enes.liquidtube.ui.components.LocalBottomInset
import com.enes.liquidtube.ui.components.LocalMenus
import com.enes.liquidtube.ui.components.LocalNavActions
import com.enes.liquidtube.ui.components.LocalOverlay
import com.enes.liquidtube.ui.components.MenuFactory
import com.enes.liquidtube.ui.components.MiniPlayer
import com.enes.liquidtube.ui.components.NavActions
import com.enes.liquidtube.ui.components.OverlayController
import com.enes.liquidtube.ui.components.OverlayHost
import com.enes.liquidtube.ui.components.bounceClick
import com.enes.liquidtube.ui.components.isLandscape
import com.enes.liquidtube.ui.glass.GlassBackdrop
import com.enes.liquidtube.ui.glass.glassSource
import com.enes.liquidtube.ui.glass.liquidGlass
import com.enes.liquidtube.ui.glass.rememberGlassBackdrop
import com.enes.liquidtube.ui.home.HomeScreen
import com.enes.liquidtube.ui.library.LibraryGridScreen
import com.enes.liquidtube.ui.library.LibraryScreen
import com.enes.liquidtube.ui.library.SongListScreen
import com.enes.liquidtube.ui.player.NowPlayingScreen
import com.enes.liquidtube.ui.radio.RadioScreen
import com.enes.liquidtube.ui.search.SearchScreen
import com.enes.liquidtube.ui.theme.AppTheme
import com.enes.liquidtube.ui.theme.chromeGlass
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.enes.liquidtube.ui.theme.tr
import kotlin.math.roundToInt

private enum class Tab(val icon: ImageVector) {
    Home(Icons.Rounded.Home),
    New(Icons.Rounded.GridView),
    Radio(Icons.Rounded.Sensors),
    Library(Icons.Rounded.LibraryMusic),
    Search(Icons.Rounded.Search);

        fun label(): String = when (this) {
        Home -> tr("Home", "Ana Sayfa")
        New -> tr("New", "Yeni")
        Radio -> tr("Radio", "Radyo")
        Library -> tr("Library", "Kitaplık")
        Search -> tr("Search", "Ara")
    }
}

/** Tabs shown inside the capsule; Search has its own round button. */
private val BAR_TABS = Tab.entries.filter { it != Tab.Search }
private const val ROOT = "root"

private val BarHeight = 66.dp      // expanded tab bar / search button
private val CompactHeight = 56.dp  // minimized row: tab circle, mini-player, search
private val MiniHeight = 60.dp     // mini-player above the expanded bar
private val ChromeGap = 10.dp
private val ChromeMargin = 18.dp

private val RailWidth = 68.dp       // landscape: vertical tab rail / search button
private val RailItem = 58.dp        // one tab in the rail
private val RailMargin = 14.dp
private val LandMiniHeight = 58.dp

private val PushEasing = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)
private val TabSpring = spring<Float>(dampingRatio = 0.94f, stiffness = 340f)

@Composable
fun AppScaffold(connection: PlayerConnection) {
    val backdrop = rememberGlassBackdrop()
    val screenBackdrop = rememberGlassBackdrop() // everything, for menus and sheets
    val current by connection.current.collectAsStateWithLifecycle()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var minimized by remember { mutableStateOf(false) }
    val c = AppTheme.colors
    val scope = rememberCoroutineScope()

    // Each tab is a page with its own navigation stack, like iOS.
    val pagerState = rememberPagerState { Tab.entries.size }
    val controllers = Tab.entries.map { rememberNavController() }
    val currentController = controllers[pagerState.currentPage]
    val currentEntry by currentController.currentBackStackEntryAsState()
    val atRoot = currentEntry == null || currentEntry?.destination?.route == ROOT

    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { minimized = false } }
    LaunchedEffect(current) { if (current == null) minimized = false }

    fun selectTab(index: Int) {
        if (index == pagerState.currentPage) {
            controllers[index].popBackStack(ROOT, inclusive = false)
        } else {
            scope.launch {
                // A critically-damped spring sweeps through the tabs in between, so the
                // direction always reads right; long jumps travel faster, not longer.
                pagerState.animateScrollToPage(index, animationSpec = TabSpring)
            }
        }
    }

    val scrollConnection = remember(current) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (current == null) {
                    minimized = false
                    return Offset.Zero
                }
                if (available.y < -8f) minimized = true
                else if (available.y > 8f) minimized = false
                return Offset.Zero
            }
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val hasSong = current != null
    val landscape = isLandscape()
    // Landscape: the tabs move to a vertical rail on the left; the mini-player floats bottom-right.
    val sideInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).asPaddingValues()
    val startInset = sideInsets.calculateStartPadding(LayoutDirection.Ltr)
    val endInset = sideInsets.calculateEndPadding(LayoutDirection.Ltr)
    val railSpace = startInset + RailMargin + RailWidth + 4.dp
    val bottomInset = if (landscape) {
        navInset + (if (hasSong) LandMiniHeight + 10.dp else 0.dp) + 16.dp
    } else {
        navInset + 8.dp + BarHeight + (if (hasSong) MiniHeight + ChromeGap else 0.dp) + 20.dp
    }

    val navActions = remember(controllers) {
        NavActions(
            openCollection = { id -> controllers[pagerState.currentPage].navigate("collection/$id") },
            openAccount = { controllers[pagerState.currentPage].navigate("account") { launchSingleTop = true } },
            openSection = { ref, title ->
                val e = { v: String -> android.net.Uri.encode(v) }
                controllers[pagerState.currentPage].navigate("section/${e(ref.browseId)}?params=${e(ref.params.orEmpty())}&title=${e(title)}")
            },
            openAddSongs = { id, title ->
                val e = { v: String -> android.net.Uri.encode(v) }
                controllers[pagerState.currentPage].navigate("playlist-add/${e(id.removePrefix("VL"))}?title=${e(title)}")
            },
        )
    }
    // Opened from a notification: go to that album/single.
    LaunchedEffect(Unit) {
        PendingNav.collection.collect { id ->
            if (id != null) {
                nowPlayingOpen = false
                navActions.openCollection(id)
                PendingNav.collection.value = null
            }
        }
    }
    val overlay = remember { OverlayController() }
    val menus = remember(navActions, overlay) { MenuFactory(connection, navActions, overlay) }

    // Each tab's NavHost gets its own back dispatcher. The system back gesture is forwarded
    // (with its progress) to the visible tab only, so the page follows your finger.
    val lifecycleOwner = LocalLifecycleOwner.current
    val tabBack = remember(lifecycleOwner) {
        Tab.entries.map {
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher = OnBackPressedDispatcher()
                override val lifecycle: Lifecycle get() = lifecycleOwner.lifecycle
            }
        }
    }

    CompositionLocalProvider(
        LocalPlayerConnection provides connection,
        LocalBottomInset provides bottomInset,
        LocalNavActions provides navActions,
        LocalOverlay provides overlay,
        LocalMenus provides menus,
    ) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().background(c.background)) {
            // The whole screen is recorded for menus/sheets to blur only while one is open:
            // drawing through a recorded layer degrades Now Playing's progressive blur.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (overlay.isActive) Modifier.glassSource(screenBackdrop, c.background) else Modifier),
            ) {
                // Each tab page. Upright the pages slide sideways; in landscape they move up and
                // down, the same direction as the tab rail.
                val tabPage: @Composable androidx.compose.foundation.pager.PagerScope.(Int) -> Unit = { page ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Tab switch: pages travel side by side (stacked in landscape,
                                // following the rail) as rounded cards that shrink and dim
                                // slightly in flight, then settle full-bleed.
                                val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                                val d = abs(offset).coerceAtMost(1f)
                                val inFlight = (d * 2f).coerceAtMost(1f) * (1f - (d - 0.5f).coerceAtLeast(0f))
                                val s = 1f - 0.07f * (d * 2f).coerceAtMost(1f)
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.4f * d
                                if (d > 0.001f) {
                                    shape = RoundedCornerShape((34f * inFlight.coerceAtLeast(0.6f)).dp)
                                    clip = true
                                } else {
                                    clip = false
                                }
                            },
                    ) {
                        CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides tabBack[page]) {
                            TabNavHost(Tab.entries[page], controllers[page])
                        }
                    }
                }
                if (landscape) {
                    androidx.compose.foundation.pager.VerticalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    beyondViewportPageCount = Tab.entries.size,
                    key = { it },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (landscape) Modifier.padding(start = railSpace, end = endInset) else Modifier)
                        .nestedScroll(scrollConnection)
                        .glassSource(backdrop, c.background),
                        pageContent = tabPage,
                    )
                } else {
                    HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    beyondViewportPageCount = Tab.entries.size,
                    key = { it },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (landscape) Modifier.padding(start = railSpace, end = endInset) else Modifier)
                        .nestedScroll(scrollConnection)
                        .glassSource(backdrop, c.background),
                        pageContent = tabPage,
                    )
                }

                // Scroll-edge effects: content dissolves under the status bar and behind the chrome.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusInset + 22.dp)
                        .background(Brush.verticalGradient(listOf(c.background, c.background.copy(alpha = 0.85f), Color.Transparent))),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(navInset + if (landscape) 70.dp else 110.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, c.background.copy(alpha = 0.35f), c.background.copy(alpha = 0.6f)))),
                )

                AnimatedContent(
                    targetState = nowPlayingOpen && hasSong,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(260)) },
                    label = "nowPlaying",
                ) { open ->
                    val boundsSpec = spring<Rect>(dampingRatio = 0.86f, stiffness = 360f)
                    val container = Modifier.sharedBounds(
                        rememberSharedContentState("np-container"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> boundsSpec },
                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(30.dp)),
                    )
                    val art = Modifier.sharedElement(
                        rememberSharedContentState("np-art"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ -> boundsSpec },
                    )
                    if (open) {
                        NowPlayingScreen(
                            connection = connection,
                            onCollapse = { nowPlayingOpen = false },
                            containerModifier = container,
                            artModifier = art,
                            shown = this@AnimatedContent.transition.targetState == androidx.compose.animation.EnterExitState.Visible,
                        )
                    } else if (landscape) {
                        SideChrome(
                            pagerState = pagerState,
                            backdrop = backdrop,
                            connection = connection,
                            hasSong = hasSong,
                            minimized = minimized,
                            onSelect = ::selectTab,
                            onPreview = { index ->
                                scope.launch { pagerState.animateScrollToPage(index, animationSpec = TabSpring) }
                            },
                            onOpenPlayer = { nowPlayingOpen = true },
                            miniModifier = container,
                            miniArtModifier = art,
                        )
                    } else {
                        BottomChrome(
                            pagerState = pagerState,
                            backdrop = backdrop,
                            connection = connection,
                            hasSong = hasSong,
                            minimized = minimized,
                            onSelect = ::selectTab,
                            onPreview = { index ->
                                scope.launch { pagerState.animateScrollToPage(index, animationSpec = TabSpring) }
                            },
                            onExpandBar = { minimized = false },
                            onOpenPlayer = { nowPlayingOpen = true },
                            miniModifier = container,
                            miniArtModifier = art,
                        )
                    }
                }
            }

            OverlayHost(overlay, connection, screenBackdrop)
          }
        }
    }

    // Menus, sheets and dialogs take back first (their own handlers live in OverlayHost).
    val free = !nowPlayingOpen && !overlay.isActive
    BackHandler(enabled = nowPlayingOpen && !overlay.isActive) { nowPlayingOpen = false }
    BackHandler(enabled = free && atRoot && pagerState.currentPage != 0) { selectTab(0) }
    PredictiveBackHandler(enabled = free && !atRoot) { progress ->
        val dispatcher = tabBack[pagerState.currentPage].onBackPressedDispatcher
        var started = false
        try {
            progress.collect { event ->
                if (!started) {
                    dispatcher.dispatchOnBackStarted(event)
                    started = true
                } else {
                    dispatcher.dispatchOnBackProgressed(event)
                }
            }
            dispatcher.onBackPressed()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (started) dispatcher.dispatchOnBackCancelled()
            throw e
        }
    }
}

// --------------------------------------------------------------- per-tab nav

@Composable
private fun TabNavHost(tab: Tab, controller: NavHostController) {
    NavHost(
        navController = controller,
        startDestination = ROOT,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(tween(400, easing = PushEasing)) { it } },
        exitTransition = { slideOutHorizontally(tween(400, easing = PushEasing)) { -it / 4 } + fadeOut(tween(400), targetAlpha = 0.5f) },
        popEnterTransition = { slideInHorizontally(tween(400, easing = PushEasing)) { -it / 4 } + fadeIn(tween(400), initialAlpha = 0.5f) },
        popExitTransition = { slideOutHorizontally(tween(400, easing = PushEasing)) { it } },
    ) {
        composable(ROOT) {
          Page {
            when (tab) {
                Tab.Home -> HomeScreen()
                Tab.New -> BrowseScreen()
                Tab.Radio -> RadioScreen()
                Tab.Library -> LibraryScreen(
                    onOpenList = { kind -> controller.navigate(if (kind == "replay") "replay" else "library/songs/$kind") },
                    onOpenLibrary = { kind -> controller.navigate("library/grid/$kind") },
                )
                Tab.Search -> SearchScreen()
            }
          }
        }
        composable("library/songs/{kind}") { entry ->
            Page { SongListScreen(kind = entry.arguments?.getString("kind") ?: "recents", onBack = { controller.popBackStack() }) }
        }
        composable("replay") {
            Page { com.enes.liquidtube.ui.library.ReplayScreen(onBack = { controller.popBackStack() }) }
        }
        composable("library/grid/{kind}") {
            Page { LibraryGridScreen(onBack = { controller.popBackStack() }) }
        }
        composable(
            "section/{browseId}?params={params}&title={title}",
            arguments = listOf(
                androidx.navigation.navArgument("params") { defaultValue = "" },
                androidx.navigation.navArgument("title") { defaultValue = "" },
            ),
        ) {
            Page { com.enes.liquidtube.ui.collection.SectionScreen(onBack = { controller.popBackStack() }) }
        }
        composable(
            "playlist-add/{playlistId}?title={title}",
            arguments = listOf(androidx.navigation.navArgument("title") { defaultValue = "" }),
        ) {
            Page { com.enes.liquidtube.ui.collection.AddSongsScreen(onBack = { controller.popBackStack() }) }
        }
        composable("collection/{browseId}") {
            Page { CollectionScreen(onBack = { controller.popBackStack() }) }
        }
        composable("account") {
            Page { AccountScreen(onBack = { controller.popBackStack() }, onSignIn = { controller.navigate("login") }) }
        }
        composable("login") {
            Page { LoginScreen(onDone = { controller.popBackStack() }) }
        }
    }
}

/**
 * Every navigation destination is opaque: while a page slides away (back swipe), the
 * page underneath must not show through it.
 */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AppTheme.colors.background)) { content() }
}

// ----------------------------------------------------------------- chrome

@Composable
private fun BottomChrome(
    pagerState: PagerState,
    backdrop: GlassBackdrop,
    connection: PlayerConnection,
    hasSong: Boolean,
    minimized: Boolean,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onExpandBar: () -> Unit,
    onOpenPlayer: () -> Unit,
    miniModifier: Modifier,
    miniArtModifier: Modifier,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = ChromeMargin, end = ChromeMargin, bottom = 6.dp),
        ) {
            val width = maxWidth
            val spring = spring<Dp>(dampingRatio = 0.8f, stiffness = 420f)
            val rowHeight by animateDpAsState(if (minimized) CompactHeight else BarHeight, spring, label = "rowH")
            val tabWidth by animateDpAsState(if (minimized) CompactHeight else width - BarHeight - ChromeGap, spring, label = "tabW")
            val miniX by animateDpAsState(if (minimized) CompactHeight + ChromeGap else 0.dp, spring, label = "miniX")
            val miniW by animateDpAsState(if (minimized) width - (CompactHeight + ChromeGap) * 2 else width, spring, label = "miniW")
            val miniH by animateDpAsState(if (minimized) CompactHeight else MiniHeight, spring, label = "miniH")
            val totalHeight = BarHeight + if (hasSong) MiniHeight + ChromeGap else 0.dp
            val miniY by animateDpAsState(if (minimized) totalHeight - CompactHeight else 0.dp, spring, label = "miniY")

            Box(Modifier.fillMaxWidth().height(totalHeight)) {
                AnimatedVisibility(
                    visible = hasSong,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.offset(x = miniX, y = miniY),
                ) {
                    MiniPlayer(
                        connection = connection,
                        backdrop = backdrop,
                        onExpand = onOpenPlayer,
                        modifier = miniModifier.width(miniW).height(miniH),
                        artModifier = miniArtModifier,
                        compact = minimized,
                    )
                }

                GlassTabBar(
                    backdrop = backdrop,
                    pagerState = pagerState,
                    compact = minimized,
                    onSelect = onSelect,
                    onPreview = onPreview,
                    onExpand = onExpandBar,
                    modifier = Modifier.align(Alignment.BottomStart).width(tabWidth).height(rowHeight),
                )

                GlassCircleButton(
                    backdrop = backdrop,
                    icon = Icons.Rounded.Search,
                    description = "Search",
                    selected = pagerState.targetPage == Tab.Search.ordinal,
                    onClick = { onSelect(Tab.Search.ordinal) },
                    modifier = Modifier.align(Alignment.BottomEnd).size(rowHeight),
                )
            }
        }
    }
}

@Composable
private fun GlassTabBar(
    backdrop: GlassBackdrop,
    pagerState: PagerState,
    compact: Boolean,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier,
) {
    val c = AppTheme.colors
    var lift by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .graphicsLayer {
                val s = 1f + 0.035f * lift
                scaleX = s
                scaleY = s
            }
            .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.35f), clip = false)
            .liquidGlass(backdrop, chromeGlass(c.isDark)),
    ) {
        Crossfade(compact, animationSpec = tween(180), label = "tabCompact") { isCompact ->
            if (isCompact) {
                val tab = BAR_TABS.getOrNull(pagerState.currentPage) ?: Tab.Home
                Box(
                    Modifier.fillMaxSize().bounceClick(pressedScale = 0.88f, onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.icon, tab.label(),
                        tint = if (pagerState.currentPage < BAR_TABS.size) c.accent else c.label,
                        modifier = Modifier.size(27.dp),
                    )
                }
            } else {
                TabRow(pagerState, onSelect, onPreview) { lift = it }
            }
        }
    }
}

/**
 * Tab row with the iOS 26 lens: tap to switch; press-and-hold (or swipe) lifts a
 * magnifying glass bubble you can slide across the tabs, and the pages follow live.
 */
@Composable
private fun TabRow(
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onLift: (Float) -> Unit,
) {
    val c = AppTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val preview by rememberUpdatedState(onPreview)

    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        val count = BAR_TABS.size
        val itemWidth = maxWidth / count
        val itemPx = with(density) { itemWidth.toPx() }
        val widthPx = itemPx * count

        var dragging by remember { mutableStateOf(false) }
        var dragX by remember { mutableFloatStateOf(0f) }
        var hovered by remember { mutableIntStateOf(-1) }

        val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "lift")
        LaunchedEffect(lift) { onLift(lift) }

        // The indicator rides the pager itself, so tabs, bar and pages move as one.
        val pagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val restCenter = (pagePos.coerceIn(0f, count - 1f) + 0.5f) * itemPx
        val center = if (dragging) dragX else restCenter
        val indicatorAlpha = if (dragging) 1f else (1f - (pagePos - (count - 1)).coerceIn(0f, 1f))

        fun indexAt(x: Float) = (x / itemPx).toInt().coerceIn(0, count - 1)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(count, itemPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tapped = indexAt(down.position.x)
                        val outcome = withTimeoutOrNull(170L) {
                            awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: TapUp
                        }
                        if (outcome === TapUp) {
                            select(tapped)
                            return@awaitEachGesture
                        }
                        // Lens mode: long press or swipe.
                        dragging = true
                        dragX = down.position.x.coerceIn(itemPx / 2, widthPx - itemPx / 2)
                        hovered = tapped
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        drag(down.id) { change ->
                            change.consume()
                            dragX = change.position.x.coerceIn(itemPx / 2, widthPx - itemPx / 2)
                            val idx = indexAt(change.position.x)
                            if (idx != hovered) {
                                hovered = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                preview(idx)
                            }
                        }
                        val target = hovered
                        dragging = false
                        hovered = -1
                        select(target)
                    }
                },
        ) {
            // Glass lens / selection pill.
            Box(
                Modifier
                    .offset { IntOffset((center - itemPx / 2).roundToInt(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = indicatorAlpha
                        scaleX = 1f + 0.22f * lift
                        scaleY = 1f + 0.32f * lift
                    }
                    .shadow((10 * lift).dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(
                        if (c.isDark) Color.White.copy(alpha = 0.16f + 0.08f * lift)
                        else Color.Black.copy(alpha = 0.08f - 0.04f * lift)
                    )
                    .border((0.8f * lift).dp, Color.White.copy(alpha = 0.6f * lift), CircleShape),
            )

            Row(Modifier.fillMaxSize()) {
                BAR_TABS.forEachIndexed { i, tab ->
                    val itemCenter = (i + 0.5f) * itemPx
                    val closeness = (1f - abs(center - itemCenter) / itemPx).coerceIn(0f, 1f)
                    val selected = if (dragging) i == hovered else abs(pagePos - i) < 0.5f
                    val tint = if (selected) c.accent else c.label
                    val magnify = 1f + 0.28f * lift * closeness
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .graphicsLayer { scaleX = magnify; scaleY = magnify },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(tab.icon, tab.label(), tint = tint, modifier = Modifier.size(27.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            tab.label(),
                            style = AppTheme.type.caption2.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}

private val TapUp = Any()

@Composable
private fun GlassCircleButton(
    backdrop: GlassBackdrop,
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = AppTheme.colors
    Box(
        modifier
            .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.35f))
            .liquidGlass(backdrop, chromeGlass(c.isDark))
            .bounceClick(pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (selected) c.accent else c.label, modifier = Modifier.size(28.dp))
    }
}

// ------------------------------------------------------------ landscape chrome

/**
 * Landscape chrome: the tab bar stands up as a glass rail on the left with Search under it,
 * and the mini-player floats in the bottom-right corner, so the content gets the full height.
 */
@Composable
private fun SideChrome(
    pagerState: PagerState,
    backdrop: GlassBackdrop,
    connection: PlayerConnection,
    hasSong: Boolean,
    minimized: Boolean,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    onOpenPlayer: () -> Unit,
    miniModifier: Modifier,
    miniArtModifier: Modifier,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                .padding(start = RailMargin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ChromeGap),
        ) {
            GlassTabRail(
                backdrop = backdrop,
                pagerState = pagerState,
                onSelect = onSelect,
                onPreview = onPreview,
                modifier = Modifier.width(RailWidth).height(RailItem * BAR_TABS.size + 8.dp),
            )
            GlassCircleButton(
                backdrop = backdrop,
                icon = Icons.Rounded.Search,
                description = "Search",
                selected = pagerState.targetPage == Tab.Search.ordinal,
                onClick = { onSelect(Tab.Search.ordinal) },
                modifier = Modifier.size(RailWidth),
            )
        }

        AnimatedVisibility(
            visible = hasSong,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .padding(end = 18.dp, bottom = 8.dp),
        ) {
            val spring = spring<Dp>(dampingRatio = 0.8f, stiffness = 420f)
            val width by animateDpAsState(if (minimized) 250.dp else 380.dp, spring, label = "landMiniW")
            MiniPlayer(
                connection = connection,
                backdrop = backdrop,
                onExpand = onOpenPlayer,
                modifier = miniModifier.width(width).height(LandMiniHeight),
                artModifier = miniArtModifier,
                compact = minimized,
            )
        }
    }
}

/** The tab bar stood on its side: same lens (press-and-hold, then slide), moving vertically. */
@Composable
private fun GlassTabRail(
    backdrop: GlassBackdrop,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier,
) {
    val c = AppTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val select by rememberUpdatedState(onSelect)
    val preview by rememberUpdatedState(onPreview)

    var dragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "railLift")

    Box(
        modifier
            .graphicsLayer {
                val s = 1f + 0.035f * lift
                scaleX = s
                scaleY = s
            }
            .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.35f), clip = false)
            .liquidGlass(backdrop, chromeGlass(c.isDark)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
            val count = BAR_TABS.size
            val itemHeight = maxHeight / count
            val itemPx = with(density) { itemHeight.toPx() }
            val heightPx = itemPx * count

            var dragY by remember { mutableFloatStateOf(0f) }
            var hovered by remember { mutableIntStateOf(-1) }

            val pagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val restCenter = (pagePos.coerceIn(0f, count - 1f) + 0.5f) * itemPx
            val center = if (dragging) dragY else restCenter
            val indicatorAlpha = if (dragging) 1f else (1f - (pagePos - (count - 1)).coerceIn(0f, 1f))

            fun indexAt(y: Float) = (y / itemPx).toInt().coerceIn(0, count - 1)

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(count, itemPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tapped = indexAt(down.position.y)
                            val outcome = withTimeoutOrNull(170L) {
                                awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() } ?: TapUp
                            }
                            if (outcome === TapUp) {
                                select(tapped)
                                return@awaitEachGesture
                            }
                            dragging = true
                            dragY = down.position.y.coerceIn(itemPx / 2, heightPx - itemPx / 2)
                            hovered = tapped
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            drag(down.id) { change ->
                                change.consume()
                                dragY = change.position.y.coerceIn(itemPx / 2, heightPx - itemPx / 2)
                                val idx = indexAt(change.position.y)
                                if (idx != hovered) {
                                    hovered = idx
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    preview(idx)
                                }
                            }
                            val target = hovered
                            dragging = false
                            hovered = -1
                            select(target)
                        }
                    },
            ) {
                // Glass lens / selection pill.
                Box(
                    Modifier
                        .offset { IntOffset(0, (center - itemPx / 2).roundToInt()) }
                        .fillMaxWidth()
                        .height(itemHeight)
                        .graphicsLayer {
                            alpha = indicatorAlpha
                            scaleX = 1f + 0.32f * lift
                            scaleY = 1f + 0.22f * lift
                        }
                        .shadow((10 * lift).dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                        .clip(CircleShape)
                        .background(
                            if (c.isDark) Color.White.copy(alpha = 0.16f + 0.08f * lift)
                            else Color.Black.copy(alpha = 0.08f - 0.04f * lift)
                        )
                        .border((0.8f * lift).dp, Color.White.copy(alpha = 0.6f * lift), CircleShape),
                )

                Column(Modifier.fillMaxSize()) {
                    BAR_TABS.forEachIndexed { i, tab ->
                        val itemCenter = (i + 0.5f) * itemPx
                        val closeness = (1f - abs(center - itemCenter) / itemPx).coerceIn(0f, 1f)
                        val selected = if (dragging) i == hovered else abs(pagePos - i) < 0.5f
                        val tint = if (selected) c.accent else c.label
                        val magnify = 1f + 0.28f * lift * closeness
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = magnify; scaleY = magnify },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tab.icon, tab.label(), tint = tint, modifier = Modifier.size(25.dp))
                            Spacer(Modifier.height(1.dp))
                            Text(
                                tab.label(),
                                style = AppTheme.type.caption2.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = tint,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Navigation requested from outside the UI (a notification tap). */
object PendingNav {
    val collection = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}
