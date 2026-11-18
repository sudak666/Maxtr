package ua.rytm.app.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import ua.rytm.app.ui.motionAwareSpec
import kotlin.math.PI
import kotlin.math.sin

/**
 * Bottom-nav glyphs that move a part of themselves when their tab is
 * selected (owner's request after a monobank screen recording, 2026-11-18):
 * the wallet lets a coin peek out, the calendar's rings hop and a check is
 * drawn in, the receipt's lines print one by one, the gear turns a sixth.
 *
 * Same Lucide geometry as the static RytmIcons (24-unit space, 2-unit
 * round stroke), drawn on a Canvas so each part can move on its own; no
 * animation library. One ~0.7 s pass per selection; with system animations
 * off, motionAwareSpec snaps straight to the end state.
 */
@Composable
internal fun AnimatedTabIcon(destination: RytmDestination, selected: Boolean, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(if (selected) 1f else 0f) }
    val spec = motionAwareSpec(tween<Float>(durationMillis = 720, easing = LinearEasing))
    LaunchedEffect(selected) {
        if (selected) {
            progress.snapTo(0f)
            progress.animateTo(1f, spec)
        } else {
            progress.snapTo(0f)
        }
    }
    val glyph = remember(destination) { TabGlyph.of(destination) }
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            glyph.draw(this, progress.value, selected, tint)
        }
    }
}

private val GlyphStroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

/** 0..1 position of [p] inside the [from]..[to] window. */
private fun window(p: Float, from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
/** 0 → 1 → 0 over the window. */
private fun bump(t: Float) = sin(PI * t).toFloat()

private fun DrawScope.drawPartial(p: Path, fraction: Float, color: Color) {
    if (fraction <= 0f) return
    if (fraction >= 1f) { drawPath(p, color, style = GlyphStroke); return }
    val measure = PathMeasure().apply { setPath(p, false) }
    val out = Path()
    measure.getSegment(0f, measure.length * fraction, out, true)
    drawPath(out, color, style = GlyphStroke)
}

private sealed interface TabGlyph {
    fun draw(scope: DrawScope, p: Float, selected: Boolean, tint: Color)

    companion object {
        fun of(destination: RytmDestination): TabGlyph = when (destination) {
            RytmDestination.Finance -> Wallet
            RytmDestination.Shifts -> Calendar
            RytmDestination.Debt -> Receipt
            RytmDestination.Settings -> Gear
        }
    }

    /** Lucide `wallet`: the wallet dips, a coin rises out of its top and drops back. */
    object Wallet : TabGlyph {
        private val flap = path("M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1")
        private val side = path("M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4")
        override fun draw(scope: DrawScope, p: Float, selected: Boolean, tint: Color) = with(scope) {
            val rise = bump(window(p, 0.1f, 0.85f))
            if (rise > 0.01f) {
                // Only the part above the wallet's top edge shows, so the coin
                // reads as coming out of it.
                clipRect(left = -4f, top = -8f, right = 28f, bottom = 4.2f) {
                    drawCircle(tint, radius = 2.6f, center = Offset(12f, 6.5f - 6f * rise))
                }
            }
            val dip = 1.3f * bump(window(p, 0f, 0.3f))
            translate(top = dip) {
                drawPath(flap, tint, style = GlyphStroke)
                drawPath(side, tint, style = GlyphStroke)
            }
        }
    }

    /** Lucide `calendar`: the rings hop like a page turning, then a check is drawn in. */
    object Calendar : TabGlyph {
        private val ringLeft = path("M8 2v4")
        private val ringRight = path("M16 2v4")
        private val body = path("M5,4 H19 A2,2 0 0 1 21,6 V20 A2,2 0 0 1 19,22 H5 A2,2 0 0 1 3,20 V6 A2,2 0 0 1 5,4 Z")
        private val divider = path("M3 10h18")
        private val check = path("M8.5 16l2.5 2.5l5-5")
        override fun draw(scope: DrawScope, p: Float, selected: Boolean, tint: Color) = with(scope) {
            translate(top = -2f * bump(window(p, 0f, 0.35f))) { drawPath(ringLeft, tint, style = GlyphStroke) }
            translate(top = -2f * bump(window(p, 0.08f, 0.43f))) { drawPath(ringRight, tint, style = GlyphStroke) }
            drawPath(body, tint, style = GlyphStroke)
            drawPath(divider, tint, style = GlyphStroke)
            // The check stays while the tab is selected: the "active" state.
            if (selected) drawPartial(check, easeOut(window(p, 0.3f, 0.9f)), tint)
        }
    }

    /** Lucide `receipt-text`: the slip drops slightly and its lines print top to bottom. */
    object Receipt : TabGlyph {
        private val body = path("M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z")
        // Drawn left to right, so each line "prints" from the margin.
        private val lines = listOf(path("M8 8h6"), path("M8 12h8"), path("M8 16h5"))
        override fun draw(scope: DrawScope, p: Float, selected: Boolean, tint: Color) = with(scope) {
            translate(top = 1.4f * bump(window(p, 0f, 0.3f))) {
                drawPath(body, tint, style = GlyphStroke)
                lines.forEachIndexed { i, line ->
                    val fraction = if (selected) easeOut(window(p, 0.2f + i * 0.17f, 0.5f + i * 0.17f)) else 1f
                    drawPartial(line, fraction, tint)
                }
            }
        }
    }

    /** Lucide `settings`: the gear turns one tooth (60°) with a small overshoot. */
    object Gear : TabGlyph {
        private val teeth = path("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915")
        private val hub = path("M9,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0")
        override fun draw(scope: DrawScope, p: Float, selected: Boolean, tint: Color) = with(scope) {
            rotate(degrees = 60f * easeOutBack(window(p, 0f, 0.8f)), pivot = Offset(12f, 12f)) {
                drawPath(teeth, tint, style = GlyphStroke)
            }
            drawPath(hub, tint, style = GlyphStroke)
        }
    }
}
