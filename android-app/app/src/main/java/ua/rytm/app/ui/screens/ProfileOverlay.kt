package ua.rytm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.data.ProfileAppearance
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.LocalSession
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.CloudDone
import ua.rytm.app.ui.icons.Groups
import ua.rytm.app.ui.icons.Lock
import ua.rytm.app.ui.icons.Settings
import ua.rytm.app.ui.icons.Star
import ua.rytm.app.ui.icons.VisibilityOff
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.rytmApp
import ua.rytm.app.ui.theme.RytmSpacing

private val EntryAvatarSize = 36.dp
private val PanelAvatarSize = 112.dp
private const val DismissBelowProgress = 0.7f
private const val OpenAboveProgress = 0.3f

/** The profile panel's state; lives in RytmNavHost so the Finance entry avatar and the panel share it. */
class ProfileOverlayState {
    var appearance by mutableStateOf(ProfileAppearance())
    var visible by mutableStateOf(false)
        internal set
    internal var anchor by mutableStateOf<Rect?>(null)
    internal var dragging by mutableStateOf(false)
    internal val progress = Animatable(0f)
}

val LocalProfileOverlay = compositionLocalOf { ProfileOverlayState() }

/**
 * Left swipe anywhere on the host pulls the panel in under the finger. Runs
 * after its children, so swipe-to-delete rows and chip rows keep their gestures.
 */
@Composable
fun Modifier.profileSwipeToOpen(): Modifier {
    val state = LocalProfileOverlay.current
    val scope = rememberCoroutineScope()
    val duration = if (LocalReducedMotion.current) 0 else 320
    return pointerInput(state) {
        fun release(open: Boolean) {
            state.dragging = false
            scope.launch {
                state.progress.animateTo(if (open) 1f else 0f, tween(duration, easing = FastOutSlowInEasing))
                if (!open) state.visible = false
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { state.dragging = true; state.visible = true },
            onDragEnd = { release(state.progress.value >= OpenAboveProgress) },
            onDragCancel = { release(false) },
        ) { change, dragAmount ->
            change.consume()
            scope.launch { state.progress.snapTo((state.progress.value - dragAmount / size.width).coerceIn(0f, 1f)) }
        }
    }
}

/** Top-right of the Finance hero; flies into the panel on open and back on dismiss. */
@Composable
fun ProfileEntryAvatar(modifier: Modifier = Modifier) {
    val state = LocalProfileOverlay.current
    val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
    ProfileAvatar(
        state.appearance,
        state.appearance.nickname.ifBlank { email },
        stringResource(R.string.profile_avatar),
        modifier
            .onGloballyPositioned { state.anchor = it.boundsInRoot() }
            // The flying copy replaces it while the panel is up.
            .alpha(if (state.visible) 0f else 1f)
            .clip(CircleShape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { state.visible = true },
        size = EntryAvatarSize,
    )
}

/**
 * Full-screen profile panel. One `progress` value drives the panel slide, the
 * scrim and the avatar flight, so a horizontal drag anywhere scrubs the whole
 * transition under the finger, also on 3-button navigation.
 */
@Composable
fun ProfileOverlay(onOpenSettings: () -> Unit, onMessage: (String) -> Unit) {
    val state = LocalProfileOverlay.current
    val app = rytmApp()
    val session = LocalSession.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val dataOwnerUid = session.ownerUid ?: uid
    val reducedMotion = LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val duration = if (reducedMotion) 0 else 320

    LaunchedEffect(dataOwnerUid, session.profileId) {
        if (dataOwnerUid != null) {
            runCatching { app.profileAppearanceRepository.load(dataOwnerUid, session.profileId) }
                .onSuccess { state.appearance = it }
        }
    }
    LaunchedEffect(state.visible) {
        if (state.visible && !state.dragging) state.progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    if (!state.visible || uid == null || dataOwnerUid == null) return

    fun close() {
        scope.launch {
            state.progress.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
            state.visible = false
        }
    }
    BackHandler(onBack = ::close)

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var avatarRequest by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
    val settingsViewModel: SettingsViewModel = viewModel()
    val hideAmounts by app.settingsStore.hideAmounts.collectAsStateWithLifecycle(initialValue = false)
    val backupsUnavailable = stringResource(R.string.backups_shared_unavailable)
    val profileSwitched = stringResource(R.string.settings_profile_switched)
    var openSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    var premiumOpen by rememberSaveable { mutableStateOf(false) }

    SettingsManagerSheets(
        open = openSheet,
        onClose = { openSheet = null },
        uid = uid,
        dataUid = dataOwnerUid,
        profileId = session.profileId,
        isSharedProfile = session.ownerUid != null,
        settingsViewModel = settingsViewModel,
        onProfileSwitched = { onMessage(profileSwitched) },
    )
    if (premiumOpen) PremiumDialog(onDismiss = { premiumOpen = false })

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        // No panel surface of its own: the cards float over the dimmed screen.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = state.progress.value }
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = (1f - state.progress.value) * widthPx }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (state.progress.value < DismissBelowProgress) close()
                            else scope.launch { state.progress.animateTo(1f, tween(duration)) }
                        },
                        onDragCancel = { scope.launch { state.progress.animateTo(1f, tween(duration)) } },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch { state.progress.snapTo((state.progress.value - dragAmount / widthPx).coerceIn(0f, 1f)) }
                    }
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = RytmSpacing.lg),
        ) {
            IconButton(onClick = ::close) { Icon(RytmIcons.Close, stringResource(R.string.action_close)) }
            Spacer(Modifier.height(PanelAvatarSize + 32.dp))
            ProfileAppearanceCard(
                uid = uid,
                dataOwnerUid = dataOwnerUid,
                profileId = session.profileId,
                email = email,
                repository = app.profileAppearanceRepository,
                onMessage = onMessage,
                showAvatar = false,
                avatarRequest = avatarRequest,
            )
            Spacer(Modifier.height(RytmSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
                ProfileTile(RytmIcons.Groups, R.string.profiles_title, R.string.settings_profiles_subtitle, Modifier.weight(1f)) { openSheet = SettingsSheet.Profiles }
                ProfileTile(RytmIcons.Lock, R.string.pin_settings_title, R.string.settings_pin_subtitle, Modifier.weight(1f)) { openSheet = SettingsSheet.Pin }
            }
            Spacer(Modifier.height(RytmSpacing.md))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
                ProfileTile(RytmIcons.CloudDone, R.string.settings_backups_title, R.string.settings_backups_subtitle, Modifier.weight(1f)) {
                    if (session.ownerUid != null) onMessage(backupsUnavailable) else openSheet = SettingsSheet.Backups
                }
                ProfileTile(RytmIcons.Star, R.string.settings_premium, R.string.settings_free_plan, Modifier.weight(1f)) { premiumOpen = true }
            }
            Spacer(Modifier.height(RytmSpacing.md))
            SettingsGroupCard {
                SettingsToggleRow(
                    icon = RytmIcons.VisibilityOff,
                    badgeColor = SettingsGroupColors.Appearance,
                    title = stringResource(R.string.settings_hide_amounts),
                    subtitle = stringResource(R.string.settings_hide_amounts_subtitle),
                    checked = hideAmounts,
                    enabled = true,
                    onCheckedChange = settingsViewModel::setHideAmounts,
                )
                SettingsRow(
                    icon = RytmIcons.Settings,
                    badgeColor = SettingsGroupColors.Appearance,
                    title = stringResource(R.string.nav_settings),
                    subtitle = stringResource(R.string.profile_panel_settings_subtitle),
                    onClick = { onOpenSettings(); close() },
                )
            }
            Spacer(Modifier.height(RytmSpacing.lg))
        }

        // Everything moving is read in graphicsLayer, not in composition: a
        // per-frame recomposition of this panel missed twice the frames of a scroll.
        val panelSizePx = with(density) { PanelAvatarSize.toPx() }
        val entryScale = EntryAvatarSize / PanelAvatarSize
        val targetCenter = Offset(widthPx / 2f, topInset + with(density) { (48.dp + 16.dp + PanelAvatarSize / 2).toPx() })
        val anchor = state.anchor?.translate(-overlayOrigin)
        val startCenter = anchor?.center ?: Offset(widthPx, targetCenter.y)
        ProfileAvatar(
            state.appearance,
            state.appearance.nickname.ifBlank { email },
            stringResource(R.string.profile_avatar),
            Modifier
                .graphicsLayer {
                    val p = state.progress.value
                    translationX = lerp(startCenter.x, targetCenter.x, p) - panelSizePx / 2
                    translationY = lerp(startCenter.y, targetCenter.y, p) - panelSizePx / 2
                    scaleX = lerp(entryScale, 1f, p)
                    scaleY = scaleX
                }
                .clip(CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { avatarRequest++ },
            size = PanelAvatarSize,
        )
    }
}

@Composable
private fun ProfileTile(icon: ImageVector, title: Int, subtitle: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(RytmSpacing.lg),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(RytmSpacing.md))
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
