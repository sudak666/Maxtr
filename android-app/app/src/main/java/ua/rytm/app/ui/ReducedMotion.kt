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
import androidx.compose.animation.core.FiniteAnimationSpec

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

@Composable
fun ReducedMotionVisibility(visible: Boolean, content: @Composable () -> Unit) {
    if (LocalReducedMotion.current) {
        if (visible) content()
    } else {
        AnimatedVisibility(visible = visible) { content() }
    }
}

@Composable
fun <T> motionAwareSpec(default: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else default
