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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.produceState
import ua.rytm.app.RytmApplication
import ua.rytm.app.R
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.ProfileSyncCoordinator
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.LocalRealtimeState
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
    val hideAmounts by app.settingsStore.hideAmounts.collectAsState(initial = false)
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

    CompositionLocalProvider(LocalCanEditProfile provides canEdit, LocalRealtimeState provides realtimeState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rytm", fontWeight = FontWeight.Black)
                        val status = when (realtimeState) {
                            ProfileSyncCoordinator.RealtimeState.Syncing -> R.string.sync_status_syncing
                            ProfileSyncCoordinator.RealtimeState.Offline -> R.string.sync_status_offline
                            is ProfileSyncCoordinator.RealtimeState.Error -> R.string.sync_status_error
                            else -> null
                        }
                        status?.let { Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { app.settingsStore.setHideAmounts(!hideAmounts) } }) {
                        Icon(if (hideAmounts) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (hideAmounts) R.string.action_show_amounts else R.string.action_hide_amounts))
                    }
                    IconButton(onClick = ::refresh, enabled = !refreshing) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh)) }
                },
            )
        },
        bottomBar = { RytmBottomBar(navController) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding()),
        ) {
        NavHost(
            navController = navController,
            startDestination = RytmDestination.Finance.route,
            modifier = androidx.compose.ui.Modifier,
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
private fun RytmBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val shape = RoundedCornerShape(RytmDimens.BottomNavRadius)

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = RytmDimens.BottomNavHorizontal, vertical = RytmDimens.BottomNavBottom),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 14.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            RytmDestination.entries.forEach { destination ->
                val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                RytmTabButton(
                    destination = destination,
                    selected = selected,
                    modifier = Modifier.weight(1f),
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
private fun RytmTabButton(destination: RytmDestination, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
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
                .size(RytmDimens.TabIcon)
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
                contentDescription = stringResource(destination.labelRes),
                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(RytmDimens.TabGlyph),
            )
        }
        Text(
            stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                if (selected) {
                    alpha = labelAlpha.value
                    translationY = labelOffsetDp.value * density
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
