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
    val direction: String = "owe",
    /** [dueDate] repeats every month on its day of month. */
    val dueMonthly: Boolean = false,
)

val Debt.owedToMe: Boolean get() = direction == "owed"

/** Open balances per currency, for one direction; settled debts drop out. */
fun List<Debt>.openTotals(owedToMe: Boolean): Map<String, Double> =
    filter { it.owedToMe == owedToMe && it.currentBalance() > 0 }
        .groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.currentBalance() } }

/**
 * The due date that is next, as yyyy-MM-dd ("" when none). A monthly one repeats
 * on [Debt.dueDate]'s day of month, clamped to the month's last day, from
 * [Debt.dueDate] on. Same rule as nextDebtDueDate() in functions/lib/sweep.js,
 * which sends the reminder a day before.
 */
fun Debt.nextDueDate(today: java.time.LocalDate = java.time.LocalDate.now()): String {
    if (!dueMonthly || dueDate.isBlank()) return dueDate
    val first = runCatching { java.time.LocalDate.parse(dueDate) }.getOrNull() ?: return dueDate
    if (!first.isBefore(today)) return dueDate
    val anchor = first.dayOfMonth
    var month = java.time.YearMonth.from(today)
    repeat(2) {
        val candidate = month.atDay(minOf(anchor, month.lengthOfMonth()))
        if (!candidate.isBefore(today)) return candidate.toString()
        month = month.plusMonths(1)
    }
    return dueDate
}

fun Debt.currentBalance(): Double = entries.lastOrNull()?.balance ?: startAmount

// Mirrors CLAUDE.md's DEBT_COLORS (js/core.js) chip-color rotation.
val DEBT_COLORS = listOf(0xFF8B5CF6, 0xFF10B981, 0xFF3B82F6, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4)

/**
 * When every payment's balance was auto-filled from [oldStart] (start minus
 * the running sum of plain amounts), returns the same payments recounted from
 * [newStart]; otherwise null, so balances the user typed by hand stay. This
 * repairs a loan first saved without its amount, whose balances went below
 * zero, as soon as the amount is entered.
 */
fun rechainedEntries(entries: List<DebtEntry>, oldStart: Double, newStart: Double): List<DebtEntry>? {
    if (entries.isEmpty() || kotlin.math.abs(oldStart - newStart) < 0.005) return null
    var old = oldStart
    var new = newStart
    return entries.map { e ->
        val paid = parsePlainDebtAmount(e.amount) ?: return null
        old -= paid
        if (kotlin.math.abs(old - e.balance) >= 0.005) return null
        new -= paid
        e.copy(balance = kotlin.math.round(new * 100) / 100)
    }
}
