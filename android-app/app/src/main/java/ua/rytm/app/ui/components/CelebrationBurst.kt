package ua.rytm.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val ConfettiColors = listOf(
    Color(0xFF8B5CF6), Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF3B82F6),
)

private class Particle(val angleDeg: Float, val speed: Float, val color: Color, val radius: Float)

/**
 * A one-shot confetti burst from the center of its bounds, for a genuine
 * milestone moment (a debt fully paid off, a savings goal reached) -- the
 * "delight" layer competitor fintech apps (Monobank's goal celebration)
 * have and this app didn't.
 *
 * Pure Canvas + Animatable, no Lottie/new dependency: this app's icon-based
 * design language already avoids external asset libraries (see RytmIcons),
 * and a milestone this rare doesn't justify pulling in a general animation
 * library for it.
 *
 * Fires whenever [trigger] changes to a new non-null value -- pass a
 * counter (e.g. `(previous ?: 0) + 1`), not a plain boolean, since a
 * boolean can only toggle twice before it stops producing new values for
 * `LaunchedEffect` to key off. Callers are responsible for only advancing
 * the counter on a genuine state transition (see DebtScreen's
 * `HeroBalance` / GoalsManagerSheet's `GoalRow` for the pattern:
 * remember the previous "done" state seeded from the CURRENT value, so an
 * already-completed goal/debt doesn't replay the celebration on every
 * later visit).
 */
@Composable
fun CelebrationBurst(trigger: Any?, modifier: Modifier = Modifier) {
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        particles = List(24) {
            Particle(
                angleDeg = Random.nextFloat() * 360f,
                speed = 0.6f + Random.nextFloat() * 0.4f,
                color = ConfettiColors[it % ConfettiColors.size],
                radius = 4f + Random.nextFloat() * 5f,
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(900))
    }
    if (particles.isNotEmpty() && progress.value < 1f) {
        Canvas(modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * 0.7f
            val alpha = (1f - progress.value).coerceIn(0f, 1f)
            particles.forEach { p ->
                val distance = maxRadius * progress.value * p.speed
                val x = center.x + cos(Math.toRadians(p.angleDeg.toDouble())).toFloat() * distance
                val y = center.y + sin(Math.toRadians(p.angleDeg.toDouble())).toFloat() * distance
                drawCircle(color = p.color.copy(alpha = alpha), radius = p.radius, center = Offset(x, y))
            }
        }
    }
}
