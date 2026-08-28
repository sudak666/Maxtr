package ua.rytm.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.produceState
import ua.rytm.app.RytmApplication
import ua.rytm.app.R
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.LocalRealtimeState
import ua.rytm.app.ui.LocalRetryLoad
import ua.rytm.app.ui.motionAwareSpec
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.rytm.app.ui.screens.DebtScreen
import ua.rytm.app.ui.screens.SettingsScreen
import ua.rytm.app.ui.screens.finance.FinanceScreen
import ua.rytm.app.ui.screens.shifts.ShiftsScreen
import ua.rytm.app.ui.screens.shopping.ShoppingScreen
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmInteraction
import ua.rytm.app.ui.theme.RytmSemantic
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RytmNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as RytmApplication
    val accountUid = FirebaseAuth.getInstance().currentUser?.uid
    val profileId by (accountUid?.let(app.activeProfileStore::activeProfileId) ?: flowOf(DEFAULT_PROFILE_ID)).collectAsState(initial = DEFAULT_PROFILE_ID)
    val ownerUid by (accountUid?.let(app.activeProfileStore::activeProfileOwnerUid) ?: flowOf(null)).collectAsState(initial = null)
    val canEdit by produceState(initialValue = ownerUid == null, accountUid, ownerUid, profileId) {
        value = accountUid?.let { app.profilesRepository.canEditProfile(it, ownerUid, profileId) } ?: false
    }
    val realtimeState by app.profileSyncCoordinator.realtimeState.collectAsState()
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val contentOffsetPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        val uid = accountUid ?: return
        if (refreshing) return
        scope.launch {
            refreshing = true
            try { app.profileSyncCoordinator.loadOnSignIn(uid) } finally { refreshing = false }
        }
    }

    // One retry entry point for every screen's load-error state — the same
    // reload pull-to-refresh triggers, so a failed load stops being a dead end.
    // One snackbar host for the whole nav graph, so every screen reports
    // transient events the same way (see ui/SnackbarHost.kt).
    val snackbarHostState = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalCanEditProfile provides canEdit,
        LocalRealtimeState provides realtimeState,
        LocalRetryLoad provides ::refresh,
        LocalSnackbarHost provides snackbarHostState,
    ) {
    // Width adaptivity. The app previously reacted to screen HEIGHT
    // (`screenHeightDp < 480`) and font scale, but never to width: on a
    // tablet the hero card stretched to ~768dp with its number in the far
    // left corner, transaction rows put the icon and the amount 700dp apart,
    // and the floating nav capsule spread five tabs across the whole screen.
    // Two changes cover it without a per-screen rewrite: the content column
    // is capped and centred, and the bottom capsule becomes a NavigationRail
    // on the side once there is room for one.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val expandedWidth = widthDp >= 840
    val mediumWidth = widthDp >= 600

    Row(Modifier.fillMaxSize()) {
    if (mediumWidth) {
        RytmNavigationRail(navController)
    }
    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .clipToBounds(),
        ) {
        NavHost(
            navController = navController,
            startDestination = RytmDestination.Finance.route,
            // Long measures of text are unreadable; cap and centre.
            modifier = if (mediumWidth) {
                Modifier.widthIn(max = if (expandedWidth) 720.dp else 600.dp).align(Alignment.TopCenter)
            } else {
                Modifier
            },
            enterTransition = {
                if (reducedMotion) EnterTransition.None
                else fadeIn(tween(180)) + slideInVertically(tween(180)) { contentOffsetPx }
            },
            exitTransition = { ExitTransition.None },
            popEnterTransition = {
                if (reducedMotion) EnterTransition.None
                else fadeIn(tween(180)) + slideInVertically(tween(180)) { contentOffsetPx }
            },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(RytmDestination.Finance.route) { FinanceScreen() }
            composable(RytmDestination.Shifts.route) { ShiftsScreen() }
            composable(RytmDestination.Debt.route) { DebtScreen() }
            composable(RytmDestination.Shopping.route) { ShoppingScreen() }
            composable(RytmDestination.Settings.route) { SettingsScreen() }
        }
        }
    SnackbarHost(
        snackbarHostState,
        // BottomContentClearance (112dp) is calibrated to exactly match the
        // floating nav's own rendered height — right for scrollable content,
        // where the content's padding IS the visible gap (see that token's
        // own doc comment), but a Snackbar has no such padding of its own,
        // so the same value left it sitting flush against the nav with zero
        // visible breathing room (reported live, screenshot: looked like the
        // toast was half swallowed by the nav bar). +16dp measured as no
        // real change on the reporting device (confirmed by a pixel-level
        // scan of a live screenshot, not eyeballing) — likely lost to
        // Snackbar's own internal shape/shadow padding — so this was
        // re-verified empirically: +100dp produced an unmistakable ~70dp
        // gap (proving the modifier chain itself works), then dialed back
        // to +32dp and re-confirmed via the same pixel scan and a live
        // screenshot before landing on this value.
        Modifier.align(Alignment.BottomCenter).padding(bottom = RytmDimens.BottomContentClearance + 32.dp),
    )
    if (!mediumWidth) {
        RytmBottomBar(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
    }
    }
}

/**
 * Side navigation for medium/expanded widths (tablets, unfolded foldables,
 * landscape phones over 600dp). Keeps the per-tab gradient badge treatment of
 * the floating capsule so the two read as the same product.
 */
@Composable
private fun RytmNavigationRail(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.safeDrawingPadding(),
    ) {
        RytmDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Box(
                        Modifier
                            .size(RytmDimens.TabIcon)
                            .clip(CircleShape)
                            .background(
                                if (selected) Brush.linearGradient(destination.activeGradient)
                                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(RytmDimens.TabGlyph),
                        )
                    }
                },
                label = { Text(stringResource(destination.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

// Matches the PWA's .tabs: a detached rounded pill inset from the screen
// edges (14dp on all sides, matching index.html's `left/right/bottom:14px`)
// with a shadow, not an edge-to-edge Material3 NavigationBar. Each tab's
// icon sits in its own circular badge that only picks up a per-tab color
// gradient (RytmDestination.activeGradient) while selected — mirrors the
// PWA's .tab-btn.tab-c-*.active .tab-icon-wrap overrides.
@Composable
private fun RytmBottomBar(navController: androidx.navigation.NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val shape = RoundedCornerShape(RytmDimens.BottomNavRadius)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480

    Box(
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(
                horizontal = RytmDimens.BottomNavHorizontal,
                vertical = if (compactHeight) 6.dp else RytmDimens.BottomNavBottom,
            ),
    ) {
        // In light theme, elevation reads via the drop shadow below (a white
        // pill on a light lavender page). In dark theme a black shadow on an
        // already near-black background is invisible — Material3's own
        // answer to that is tonal elevation instead of shadow (a lighter
        // surface, not a darker shadow), which this bar wasn't using: plain
        // colorScheme.surface (#242327) barely separates from
        // colorScheme.background (#1C1C1F) behind it, so the "floating pill"
        // read as flush with the page (reported live). surfaceContainerHigh
        // (#38373D) is enough lighter to actually look raised.
        val barColor = if (RytmSemantic.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(barColor)
                .padding(horizontal = 10.dp, vertical = if (compactHeight) 4.dp else 8.dp),
            // Deliberately NOT `weight(1f)` on each child: an equal 1/5 share
            // still clips the selected tab's label even though it's now the
            // only one rendering text (reported live — "Налаштув" clipped
            // with no ellipsis, worse than before). SpaceEvenly sizes every
            // tab to its own content (icon-only tabs stay compact, the one
            // tab currently showing icon+label gets exactly the room its
            // text needs) and spreads the leftover space as gaps — the
            // actual fix, not another magic-number width tweak.
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RytmDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                RytmTabButton(
                    destination = destination,
                    selected = selected,
                    compact = compactHeight,
                    modifier = Modifier,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

// Matches js/app-init.js's switchTab(): the icon's "pop" (tabIconPop, a
// scale/translateY overshoot) plus a glow ripple (tabRipple, an expanding
// disc in the tab's own glow color fading to transparent) replay on EVERY
// tap of a nav icon — even re-tapping the already-active tab — not just on
// selection change. Keyframe timings/values are 1:1 with index.html's
// @keyframes tabIconPop/tabRipple (both 500ms). The label fade-in
// (tabLabelIn, 300ms) instead only plays when a tab actually becomes
// selected, mirroring `.tab-btn.active span:last-child`'s animation trigger.
@Composable
private fun RytmTabButton(destination: RytmDestination, selected: Boolean, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val popScale = remember { Animatable(1f) }
    val popOffsetDp = remember { Animatable(0f) }
    val rippleProgress = remember { Animatable(0f) }
    var popTrigger by remember { mutableIntStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) RytmInteraction.TabPressedScale else 1f,
        animationSpec = motionAwareSpec(tween(durationMillis = 180)),
        label = "tabPressedScale",
    )

    LaunchedEffect(popTrigger) {
        if (popTrigger == 0) return@LaunchedEffect
        popScale.snapTo(1f)
        popOffsetDp.snapTo(0f)
        rippleProgress.snapTo(0f)
        if (reducedMotion) return@LaunchedEffect
        scope.launch {
            popScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 500
                    1f at 0
                    1.2f at 175 using CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
                    0.94f at 300
                    1f at 500
                },
            )
        }
        scope.launch {
            popOffsetDp.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 500
                    0f at 0
                    -6f at 175 using CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
                    1f at 300
                    0f at 500
                },
            )
        }
        rippleProgress.animateTo(1f, animationSpec = tween(durationMillis = 500, easing = LinearEasing))
    }

    val labelAlpha = remember { Animatable(1f) }
    val labelOffsetDp = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (!selected) return@LaunchedEffect
        if (reducedMotion) {
            labelAlpha.snapTo(1f)
            labelOffsetDp.snapTo(0f)
            return@LaunchedEffect
        }
        labelAlpha.snapTo(0.35f)
        labelOffsetDp.snapTo(4f)
        launch { labelAlpha.animateTo(1f, animationSpec = tween(300, easing = LinearEasing)) }
        labelOffsetDp.animateTo(0f, animationSpec = tween(300, easing = LinearEasing))
    }

    val glowColor = destination.activeGradient.first()

    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, interactionSource = interactionSource, indication = null, onClick = {
                popTrigger++
                onClick()
            })
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (compact) 40.dp else RytmDimens.TabIcon)
                .drawBehind {
                    if (rippleProgress.value in 0f..1f && rippleProgress.value > 0f) {
                        val extraRadiusPx = rippleProgress.value * 15.dp.toPx()
                        val alpha = (1f - rippleProgress.value) * 0.34f
                        drawCircle(color = glowColor.copy(alpha = alpha), radius = size.minDimension / 2f + extraRadiusPx)
                    }
                }
                .graphicsLayer {
                    scaleX = popScale.value * pressedScale
                    scaleY = popScale.value * pressedScale
                    translationY = popOffsetDp.value * density
                }
                .clip(CircleShape)
                .background(if (selected) Brush.linearGradient(destination.activeGradient) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                destination.icon,
                // The label Text sits right next to it inside a Role.Tab
                // selectable — a description here made TalkBack read the tab
                // name twice.
                contentDescription = null,
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (compact) 20.dp else RytmDimens.TabGlyph),
            )
        }
        // Label only renders for the selected tab (Instagram/X/TikTok
        // pattern) — at most one label competes for width at any time,
        // so a long Ukrainian word like "Налаштування" always gets the
        // full row width instead of a fixed per-item share and can never
        // truncate, no matter how translations or font scale change later.
        if (selected) {
            Text(
                stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = labelAlpha.value
                    translationY = labelOffsetDp.value * density
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
