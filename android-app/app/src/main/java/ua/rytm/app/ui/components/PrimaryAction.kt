package ua.rytm.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The current screen's "+" action, shown as a round button next to the bottom
 * navigation capsule (design pass 2, 2026-10-20). It replaced the per-screen
 * floating action buttons, which sat over the history rows' amounts.
 */
class PrimaryAction(val label: String, val onClick: () -> Unit)

class PrimaryActionHolder {
    var action by mutableStateOf<PrimaryAction?>(null)
        internal set
}

val LocalPrimaryAction = staticCompositionLocalOf<PrimaryActionHolder?> { null }

/** Registers [onClick] as the bar's "+" while this composable is on screen and [enabled]. */
@Composable
fun ProvidePrimaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val holder = LocalPrimaryAction.current ?: return
    val latest by rememberUpdatedState(onClick)
    val action = remember(label) { PrimaryAction(label) { latest() } }
    SideEffect { holder.action = if (enabled) action else holder.action.takeUnless { it === action } }
    DisposableEffect(holder, action) {
        onDispose { if (holder.action === action) holder.action = null }
    }
}
