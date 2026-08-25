package ua.rytm.app.ui.screens.debt
import androidx.compose.runtime.Immutable

// 1:1 with js/debt.js's Debt/DebtEntry typedefs.
@Immutable
data class DebtEntry(
    val id: Long,
    val amount: String,
    val balance: Double,
    val date: String,
)

data class Debt(
    val id: Long,
    val name: String,
    val note: String,
    val currency: String,
    val startAmount: Double,
    val dueDate: String,
    /** Chronological (push) order, oldest first — same invariant js/debt.js's `entries` array relies on. */
    val entries: List<DebtEntry>,
)

fun Debt.currentBalance(): Double = entries.lastOrNull()?.balance ?: startAmount

// Mirrors CLAUDE.md's DEBT_COLORS (js/core.js) chip-color rotation.
val DEBT_COLORS = listOf(0xFF8B5CF6, 0xFF10B981, 0xFF3B82F6, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4)
