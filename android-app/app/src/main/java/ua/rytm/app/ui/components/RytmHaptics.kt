package ua.rytm.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

// One vocabulary for tactile feedback. confirm() stays LongPress: on the A51
// the lighter Confirm/VirtualKey constants were barely perceptible.
class RytmHaptics(private val feedback: HapticFeedback) {
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberRytmHaptics(): RytmHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { RytmHaptics(feedback) }
}
