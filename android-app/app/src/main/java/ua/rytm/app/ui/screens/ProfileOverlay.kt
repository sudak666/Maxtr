package ua.rytm.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.data.ProfileAppearance
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.LocalSession
import ua.rytm.app.ui.icons.Close
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

    BoxWithConstraints(
        Modifier.fillMaxSize().onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val progress = state.progress.value
        // No panel surface of its own: the cards float over the dimmed screen.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f * progress)))
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
            Text(
                stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpenSettings(); close() }
                    .padding(RytmSpacing.lg),
            )
        }

        val anchor = state.anchor?.translate(-overlayOrigin)
        val sizePx = with(density) { lerp(EntryAvatarSize.toPx(), PanelAvatarSize.toPx(), progress) }
        val targetCenter = Offset(widthPx / 2f, topInset + with(density) { (48.dp + 16.dp + PanelAvatarSize / 2).toPx() })
        val startCenter = anchor?.center ?: Offset(widthPx, targetCenter.y)
        val centerX = lerp(startCenter.x, targetCenter.x, progress)
        val centerY = lerp(startCenter.y, targetCenter.y, progress)
        ProfileAvatar(
            state.appearance,
            state.appearance.nickname.ifBlank { email },
            stringResource(R.string.profile_avatar),
            Modifier
                .offset { IntOffset((centerX - sizePx / 2).toInt(), (centerY - sizePx / 2).toInt()) }
                .clip(CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button) { avatarRequest++ },
            size = with(density) { sizePx.toDp() },
        )
    }
}
