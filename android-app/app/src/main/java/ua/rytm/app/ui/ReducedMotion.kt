package ua.rytm.app.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect

val LocalReducedMotion = compositionLocalOf { false }

/** Android equivalent of CSS prefers-reduced-motion, updated while the app is alive. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    var reduced by remember { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = !ValueAnimator.areAnimatorsEnabled()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}

// Compose's own AnimatedVisibility default (expandIn()/shrinkOut(), which
// grow/shrink from the CENTER in both axes) reads as the panel "popping"
// oddly out of its own middle instead of unfolding — reported live, and a
// real UX regression: every accordion-style reveal in the app (Debt's edit
// panel, Shifts' quick-fill panel, Shifts' autofill-schedule fields) shares
// this one composable, so all three had the same jarring reveal. Real
// accordion motion (Material's own guidance, and what system Settings
// panels use) expands vertically FROM THE TOP down, with a plain cross-fade
// — never resizes horizontally and never appears to grow from the middle.
@Composable
fun ReducedMotionVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (LocalReducedMotion.current) {
        if (visible) content()
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top),
            exit = fadeOut(tween(180)) + shrinkVertically(animationSpec = tween(180), shrinkTowards = Alignment.Top),
        ) { content() }
    }
}

@Composable
fun <T> motionAwareSpec(default: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else default

/** Replays data-driven chart entrance while respecting the system animator scale. */
@Composable
fun motionProgress(key: Any?, durationMillis: Int): Float {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(key, reduced) {
        if (reduced) progress.snapTo(1f)
        else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis))
        }
    }
    return progress.value
}
