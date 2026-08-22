package ua.rytm.app.ui.theme

import androidx.compose.ui.unit.dp

/** Exact shared geometry ported from index.html's CSS tokens and core component rules. */
object RytmDimens {
    val ContentHorizontal = 16.dp
    val CompactContentHorizontal = 10.dp
    val BottomContentClearance = 112.dp
    val BottomNavHorizontal = 14.dp
    val BottomNavBottom = 14.dp
    val SheetHorizontal = 22.dp
    val SheetVertical = 20.dp
    val HeroHorizontal = 24.dp
    val HeroVertical = 22.dp
    val QuickActionMinHeight = 84.dp
    val TouchTarget = 48.dp
    val IconBadge = 34.dp
    val IconBadgeIcon = 17.dp
    val QuickActionIcon = 44.dp
    val BottomNavRadius = 26.dp
    val TabIcon = 48.dp
    val TabGlyph = 23.dp
    val SwipeReveal = 60.dp
    val SwipeThreshold = 30.dp
}

object RytmRadii {
    val Compact = 8.dp
    val Control = 14.dp
    val Row = 16.dp
    val Input = 18.dp
    val Chart = 20.dp
    val Card = 22.dp
    val AuthCard = 24.dp
    val Sheet = 32.dp
    val Pill = 999.dp
}

object RytmElevation {
    val AuthCard = 24.dp
    val Fab = 14.dp
    val LockLogo = 10.dp
    val EmptyIcon = 8.dp
}

/** Exact CSS interaction-state values used by custom Compose controls. */
object RytmInteraction {
    const val TabPressedScale = 0.88f
    const val ButtonPressedScale = 0.97f
    const val CardPressedScale = 0.98f
    const val IconPressedScale = 0.92f
    const val DisabledAlpha = 0.4f
    const val ReorderDisabledAlpha = 0.3f
    const val FocusGlowAlpha = 0.22f
    val FocusOutline = 2.dp
    val FocusOffset = 3.dp
    val FocusGlow = 4.dp
}
