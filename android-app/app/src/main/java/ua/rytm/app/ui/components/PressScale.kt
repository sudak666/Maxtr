package ua.rytm.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import ua.rytm.app.ui.LocalReducedMotion
import ua.rytm.app.ui.theme.RytmInteraction

/**
 * Springy press feedback for custom pill buttons (FABs) that are a plain
 * clickable Row: ripple alone read flat next to the quick actions, which
 * already scale. Pass the same [source] to `clickable(interactionSource=…)`.
 */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = RytmInteraction.ButtonPressedScale): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press-scale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}
