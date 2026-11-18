package ua.rytm.app.navigation

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import ua.rytm.app.ui.theme.RytmRadii
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import ua.rytm.app.ui.components.pressScale
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Add
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlinx.coroutines.launch
import androidx.navigation.NavGraph.Companion.findStartDestination
import ua.rytm.app.ui.screens.profileSwipeToOpen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.rytm.app.ui.screens.DebtScreen
import ua.rytm.app.ui.screens.SettingsScreen
import ua.rytm.app.ui.screens.finance.FinanceScreen
import ua.rytm.app.ui.screens.shifts.ShiftsScreen
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
    val app = ua.rytm.app.ui.rytmApp()
    // Widget "+" → make sure the Finance tab is showing; FinanceScreen then
    // opens the new-transaction sheet and consumes the action.
    val launchAction by app.pendingLaunchAction.collectAsStateWithLifecycle()
    LaunchedEffect(launchAction) {
        if (launchAction in ua.rytm.app.MainActivity.FINANCE_ACTIONS &&
            navController.currentDestination?.route != RytmDestination.Finance.route
        ) {
            navController.navigate(RytmDestination.Finance.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    val accountUid = FirebaseAuth.getInstance().currentUser?.uid
    val profileId by (accountUid?.let(app.activeProfileStore::activeProfileId) ?: flowOf(DEFAULT_PROFILE_ID)).collectAsStateWithLifecycle(initialValue = DEFAULT_PROFILE_ID)
    val ownerUid by (accountUid?.let(app.activeProfileStore::activeProfileOwnerUid) ?: flowOf(null)).collectAsStateWithLifecycle(initialValue = null)
    // Starts read-only: a shared viewer briefly saw edit controls (FAB, swipe) before the role loaded.
    val canEdit by produceState(initialValue = false, accountUid, ownerUid, profileId) {
        value = accountUid?.let { app.profilesRepository.canEditProfile(it, ownerUid, profileId) } ?: false
    }
    val realtimeState by app.profileSyncCoordinator.realtimeState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val contentOffsetPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        val uid = accountUid ?: return
        if (refreshing) return
        scope.launch {
            refreshing = true
            try { app.profileSyncCoordinator.refresh(uid) } finally { refreshing = false }
        }
    }

    // One retry entry point for every screen's load-error state — the same
    // reload pull-to-refresh triggers, so a failed load stops being a dead end.
    // One snackbar host for the whole nav graph, so every screen reports
    // transient events the same way (see ui/SnackbarHost.kt).
    val snackbarHostState = remember { SnackbarHostState() }
    // Writes are queued locally and confirmed later (data/LocalFirstWrite.kt),
    // so a server rejection can only be reported here, after the fact — and the
    // rejected edit is replaced by the server's state on the reload below.
    val rejectedMessage = stringResource(R.string.sync_write_rejected)
    LaunchedEffect(Unit) {
        // No manual reload needed: Firestore reverts a rejected write in its
        // cache, the realtime listener sees that and re-syncs Room.
        ua.rytm.app.data.SyncWriteErrors.events.collect {
            snackbarHostState.showSnackbar(rejectedMessage)
        }
    }
    val primaryAction = remember { ua.rytm.app.ui.components.PrimaryActionHolder() }
    val profileOverlay = remember { ua.rytm.app.ui.screens.ProfileOverlayState() }
    CompositionLocalProvider(
        ua.rytm.app.ui.components.LocalPrimaryAction provides primaryAction,
        LocalCanEditProfile provides canEdit,
        ua.rytm.app.ui.LocalSession provides ua.rytm.app.ui.RytmSession(accountUid, profileId, ownerUid),
        LocalRealtimeState provides realtimeState,
        LocalRetryLoad provides ::refresh,
        LocalSnackbarHost provides snackbarHostState,
        ua.rytm.app.ui.screens.LocalProfileOverlay provides profileOverlay,
    ) {
    // Width adaptivity. The app previously reacted to screen HEIGHT
    // (`screenHeightDp < 480`) and font scale, but never to width: on a
    // tablet the hero card stretched to ~768dp with its number in the far
    // left corner, transaction rows put the icon and the amount 700dp apart,
    // and the floating nav capsule spread five tabs across the whole screen.
    // Two changes cover it without a per-screen rewrite: the content column
    // is capped and centred, and the bottom capsule becomes a NavigationRail
    // on the side once there is room for one.
    // The window, not the screen: in split screen or a freeform window the
    // app gets less than the display's width.
    val windowWidthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val widthDp = with(androidx.compose.ui.platform.LocalDensity.current) { windowWidthPx.toDp().value.toInt() }
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
            composable(RytmDestination.Finance.route) {
                Box(Modifier.fillMaxSize().profileSwipeToOpen()) { FinanceScreen() }
            }
            composable(RytmDestination.Shifts.route) { ShiftsScreen() }
            composable(RytmDestination.Debt.route) { DebtScreen() }
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
        // Scrim under the floating capsule: list rows were visible, sharp,
        // in the gap between the capsule and the gesture bar.
        val scrimColor = MaterialTheme.colorScheme.background
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars.add(WindowInsets(bottom = RytmDimens.BottomContentClearance / 2)))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(scrimColor.copy(alpha = 0f), scrimColor.copy(alpha = 0.92f), scrimColor))),
        )
        RytmBottomBar(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    ua.rytm.app.ui.screens.ProfileOverlay(
        onOpenSettings = {
            navController.navigate(RytmDestination.Settings.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )
    }
    }
    }
}

/**
 * Side navigation for medium/expanded widths (tablets, unfolded foldables,
 * landscape phones over 600dp). Same accent-glyph selection as the floating capsule.
 */
@Composable
private fun RytmNavigationRail(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val plus = ua.rytm.app.ui.components.LocalPrimaryAction.current?.action
    // A phone in landscape is ~410dp tall: "+" plus four icon+label items
    // overflowed and clipped "Налаштування" (P5 #3). There only the selected
    // item keeps its label and the badges shrink, as in the bottom capsule.
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.safeDrawingPadding(),
        header = { plus?.let { PrimaryPlusButton(it, size = if (compactHeight) 48.dp else 56.dp) } },
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
                    val railLabel = stringResource(destination.labelRes)
                    AnimatedTabIcon(
                        destination, selected,
                        if (selected) RytmSemantic.link else MaterialTheme.colorScheme.onSurfaceVariant,
                        RytmDimens.TabGlyph,
                        // Named only while the label is hidden, so TalkBack never reads it twice.
                        modifier = if (compactHeight && !selected) Modifier.semantics { contentDescription = railLabel } else Modifier,
                    )
                },
                label = { Text(stringResource(destination.labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = !compactHeight,
                // Same as the bottom capsule: accent glyph and label, no pill.
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedTextColor = RytmSemantic.link,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

// The floating capsule (matches the PWA's .tabs: inset 14dp from the edges,
// not an edge-to-edge NavigationBar) with the screen's "+" beside it.
//
// Audit 2026-11-18: the selected tab used to expand into icon+label, which
// pushed every other icon sideways on each switch (taps landed on the wrong
// tab), and each tab had its own color (green/purple/orange/slate). Now
// every tab owns an equal, fixed slot with its label always shown; the
// selected one turns accent-colored and its glyph plays a short
// part-animation (AnimatedTabIcons.kt), monobank-style, with no pill. Every tab has a "+", so the
// capsule keeps one width and nothing ever shifts.
@Composable
private fun RytmBottomBar(navController: androidx.navigation.NavHostController, modifier: Modifier = Modifier) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val shape = RoundedCornerShape(RytmDimens.BottomNavRadius)
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    // At ~2x font a label no longer fits a quarter of the capsule; there every
    // tab is icon-only and TalkBack still gets the name.
    val hugeFont = LocalDensity.current.fontScale > 1.5f
    val showLabels = !compactHeight && !hugeFont

    Box(
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            // P5: content scrolling under the floating pill showed through the
            // gap beside the "+" (half-cut amounts). Fade the page into the
            // background behind the bar instead of letting it peek through.
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.18f to MaterialTheme.colorScheme.background))
            .navigationBarsPadding()
            .padding(
                horizontal = RytmDimens.BottomNavHorizontal,
                vertical = if (compactHeight) 6.dp else RytmDimens.BottomNavBottom,
            ),
    ) {
        // Dark theme raises the capsule tonally (a black shadow is invisible
        // on a near-black page); light theme keeps a white capsule + shadow.
        val barColor = if (RytmSemantic.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
        val plus = ua.rytm.app.ui.components.LocalPrimaryAction.current?.action
        val selectedIndex = RytmDestination.entries.indexOfFirst { d -> currentDestination?.hierarchy?.any { it.route == d.route } == true }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .shadow(elevation = 14.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                    .clip(shape)
                    .background(barColor)
                    .padding(horizontal = 8.dp, vertical = if (compactHeight) 4.dp else 7.dp),
            ) {
                val pillHeight = if (compactHeight) 36.dp else TabPillHeight
                Row(Modifier.fillMaxWidth()) {
                    RytmDestination.entries.forEachIndexed { index, destination ->
                        RytmTabSlot(
                            destination = destination,
                            selected = index == selectedIndex,
                            pillHeight = pillHeight,
                            showLabel = showLabels,
                            // «Налаштування» is the one long label (~89dp bold at
                            // 12sp); its slot gets 1.3 shares so it fits without
                            // touching «Позики». Weights never change, so no
                            // icon moves between tabs.
                            modifier = Modifier.weight(if (destination == RytmDestination.Settings) 1.3f else 1f),
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
            // The screen's "+" sits next to the capsule instead of floating over
            // the content (it covered the history amounts) — design pass 2.
            // Every tab provides one now, so the capsule never changes width
            // and no icon ever shifts; a read-only profile gets a disabled
            // "+" in the same place (2026-11-18).
            Spacer(Modifier.width(10.dp))
            val plusSize = if (compactHeight) 52.dp else 60.dp
            if (plus != null) PrimaryPlusButton(plus, size = plusSize) else DisabledPlusButton(size = plusSize)
        }
    }
}

private val TabPillHeight = 32.dp

@Composable
private fun DisabledPlusButton(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .semantics { disabled() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(RytmIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f), modifier = Modifier.size(28.dp))
    }
}

// One flat accent circle: the gradient + accent-colored glow it had was the
// last "generated UI" tell on the main screens (audit 2026-11-18).
@Composable
private fun PrimaryPlusButton(action: ua.rytm.app.ui.components.PrimaryAction, size: androidx.compose.ui.unit.Dp) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val press = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(size)
            .pressScale(press)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(interactionSource = press, indication = androidx.compose.material3.ripple(), role = Role.Button, onClickLabel = action.label) {
                // LongPress: the lighter constants are barely felt on the A51 (see FinanceScreen).
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                action.onClick()
            }
            .semantics { contentDescription = action.label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(RytmIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
    }
}

// One fixed slot of the capsule. The sliding pill behind it is drawn by
// RytmBottomBar; the slot only tints its icon and fades its label in, so
// nothing here changes size when the selection moves.
@Composable
private fun RytmTabSlot(destination: RytmDestination, selected: Boolean, pillHeight: androidx.compose.ui.unit.Dp, showLabel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val label = stringResource(destination.labelRes)
    val iconColor by animateColorAsState(
        // No indicator pill (owner, 2026-11-18, "як у моно"): the selected tab
        // is its accent-colored glyph and label, plus the glyph's animation.
        targetValue = if (selected) RytmSemantic.link else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionAwareSpec(tween(durationMillis = 220)),
        label = "tabIcon",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) RytmSemantic.link else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motionAwareSpec(tween(durationMillis = 220)),
        label = "tabLabel",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) RytmInteraction.TabPressedScale else 1f,
        animationSpec = motionAwareSpec(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)),
        label = "tabPressedScale",
    )
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, role = Role.Tab, interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(pillHeight).graphicsLayer { scaleX = pressedScale; scaleY = pressedScale }, contentAlignment = Alignment.Center) {
            AnimatedTabIcon(destination, selected, iconColor, RytmDimens.TabGlyph)
        }
        if (showLabel) {
            // Every tab keeps its label (monobank-style, owner 2026-11-18):
            // nothing appears or moves on a switch, only color changes. Shrinks
            // rather than clips when a quarter is narrow («Налаштування»).
            // 12sp (Material's nav-label size). At a large system font a label
            // shrinks inside its own slot rather than run into a neighbour.
            ua.rytm.app.ui.components.FitText(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = labelColor,
                minSize = 9.sp,
                modifier = Modifier
                    .padding(top = 2.dp, start = 2.dp, end = 2.dp)
                    .clearAndSetSemantics { },
            )
        }
    }
}
