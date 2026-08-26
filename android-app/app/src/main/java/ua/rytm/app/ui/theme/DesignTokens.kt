package ua.rytm.app.ui.theme

import androidx.compose.ui.unit.dp

/** Exact shared geometry ported from index.html's CSS tokens and core component rules. */
object RytmDimens {
    val ContentHorizontal = 16.dp
    val CompactContentHorizontal = 10.dp
    // Shared scroll tail: clears the complete floating navigation and leaves
    // a visible 16dp breathing gap below the final card on every main screen.
    val BottomContentClearance = 112.dp
    // Extra clearance for a full-width element that would otherwise sit in
    // the same screen band as a floating pill FAB (icon+label Row, ~56dp
    // tall) positioned `BottomContentClearance` above the screen bottom.
    // BottomContentClearance alone only clears the bottom NAVIGATION bar —
    // it says nothing about a FAB floating above that bar, so a full-width
    // row landing right at that boundary visually collides with the FAB
    // instead of stacking above it (real incident: Debt tab's "Переглянути
    // всі" button, reported live via screenshot, hidden behind "+ Платіж").
    // Add this on top of BottomContentClearance for the last item preceding
    // such a FAB, rather than dodging horizontally (narrower button/eyeballed
    // end-padding) — narrowing was tried before here and already flagged as
    // fragile once translations/font scale change the FAB's own width.
    val FabRowClearance = 72.dp
    val BottomNavHorizontal = 14.dp
    val BottomNavBottom = 14.dp
    val SheetHorizontal = 22.dp
    val SheetVertical = 20.dp
    val HeroHorizontal = 24.dp
    val HeroVertical = 22.dp
    val QuickActionMinHeight = 76.dp
    val TouchTarget = 48.dp
    val IconBadge = 34.dp
    val IconBadgeIcon = 17.dp
    val QuickActionIcon = 26.dp
    val BottomNavRadius = 26.dp
    val TabIcon = 48.dp
    val TabGlyph = 23.dp
    val SwipeReveal = 60.dp
    val SwipeThreshold = 30.dp
}

/**
 * The spacing scale. `DesignTokens.kt` declared geometry but no spacing
 * ladder, so every `padding()` in the app was written as a raw number: 701
 * `.dp` literals against 41 token references. Use these for new code; the
 * pre-existing literals are being migrated opportunistically, not in one
 * risky sweep.
 */
object RytmSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
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

    // Pills were being written four different ways across the app
    // (RytmRadii.Pill, RoundedCornerShape(999.dp), (99.dp) and (50) —
    // percent!). Always use [Pill].
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
