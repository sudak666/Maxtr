package ua.rytm.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// One vocabulary for tactile feedback. Everything is LongPress: on the owner's
// Samsung A51 the lighter types (ToggleOn/Off, SegmentTick, Confirm,
// VirtualKey) were not felt at all (checked by hand 2026-10-14) — only
// LongPress registers. Kept as separate calls so the strength can differ
// per kind if a lighter type ever proves perceptible.
class RytmHaptics(private val feedback: HapticFeedback) {
    @Suppress("UNUSED_PARAMETER")
    fun toggle(on: Boolean) = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberRytmHaptics(): RytmHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { RytmHaptics(feedback) }
}
