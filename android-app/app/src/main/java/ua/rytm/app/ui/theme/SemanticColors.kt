package ua.rytm.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Theme-aware semantic colors (income / expense / positive / negative).
 *
 * MaterialTheme's own roles carry no income-vs-expense meaning — `tertiary`
 * is the brand purple — so money values used to reach straight into
 * [GreenDark2]/[RedDark2]. Those are the *dark-theme* tones: on a white
 * surface `#34D399` measures 1.92:1 and `#F87171` 2.77:1, i.e. a transaction
 * amount (the single most important number on the Finance screen) was
 * effectively unreadable in the light theme.
 *
 * Every accessor below branches on the real background luminance — the same
 * check TransactionFormSheet already used locally — and returns a tone that
 * clears WCAG AA in that theme. Use these instead of naming a raw green/red.
 */
object RytmSemantic {
    /** True while the active color scheme is a dark one. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** Income text on a plain surface. Dark 8.12:1, light 7.8:1. */
    val income: Color
        @Composable @ReadOnlyComposable get() = if (isDark) GreenDark2 else GreenText

    /** Expense text on a plain surface. Dark 5.65:1, light 8.3:1. */
    val expense: Color
        @Composable @ReadOnlyComposable get() = if (isDark) RedDark2 else RedText

    /** Tint used for the washed/gradient card backgrounds behind [income]. */
    val incomeWash: Color
        @Composable @ReadOnlyComposable get() = if (isDark) GreenDark2 else GreenDark

    /** Tint used for the washed/gradient card backgrounds behind [expense]. */
    val expenseWash: Color
        @Composable @ReadOnlyComposable get() = if (isDark) RedDark2 else RedDark

    /** Signed helper: positive → income, negative → expense, zero → muted. */
    @Composable
    @ReadOnlyComposable
    fun signed(value: Double): Color = when {
        value > 0 -> income
        value < 0 -> expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Picks black or white for text drawn on top of an arbitrary (data-driven)
 * background color — wallet colors, debt chips, tag badges. Fixed white text
 * on a mid-tone accent measured as low as 2.15:1 across the app.
 */
fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF1C1C1E) else Color.White
