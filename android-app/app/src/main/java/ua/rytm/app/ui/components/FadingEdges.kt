package ua.rytm.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades a horizontal chip row out at whichever edge still has content to
 * scroll to. Without it the last chip was cut by a hard vertical edge
 * ("Картка ПУМБ 55 538,4|"), which reads as a layout bug, not "swipe for more".
 */
fun Modifier.fadingEdges(state: LazyListState, width: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 3)
        if (state.canScrollBackward) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w), size = size.copy(width = w), blendMode = BlendMode.DstIn)
        }
        if (state.canScrollForward) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f), size = size.copy(width = w), blendMode = BlendMode.DstIn,
            )
        }
    }
