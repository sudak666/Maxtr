package ua.rytm.app.ui.screens.finance

import androidx.compose.ui.graphics.Color
import ua.rytm.app.ui.theme.BlueDark
import ua.rytm.app.ui.theme.Cyan
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Pink
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.RedDark

// PWA has a real per-category color/icon map (js/state.js's categoryIcons +
// a color picker per category, see ANDROID_MIGRATION.md §5 icon-porting
// note) that isn't ported yet. Deterministic hash-to-palette is an honest
// placeholder — same category always gets the same color — not a claim of
// real per-category customization.
private val categoryPalette = listOf(PurpleDark, GreenDark, BlueDark, OrangeDark, RedDark, Pink, Cyan)

fun categoryColor(category: String): Color =
    categoryPalette[(category.hashCode().let { if (it < 0) -it else it }) % categoryPalette.size]
