package ua.rytm.app.ui.components

import ua.rytm.app.ui.theme.RytmDimens

val SwipeRevealWidth = RytmDimens.SwipeReveal
val SwipeOpenThreshold = RytmDimens.SwipeThreshold

enum class SwipeReleaseAction { Settle, Reveal, Delete }

fun swipeReleaseAction(
    offsetPx: Float,
    rowWidthPx: Float,
    openThresholdPx: Float,
    velocityPxPerSecond: Float,
): SwipeReleaseAction = when {
    rowWidthPx > 0f && offsetPx <= -rowWidthPx * 0.5f -> SwipeReleaseAction.Delete
    offsetPx <= -openThresholdPx || velocityPxPerSecond < -1_000f -> SwipeReleaseAction.Reveal
    else -> SwipeReleaseAction.Settle
}
