package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.FinancialMonth
import java.time.YearMonth

/** A month in money, compared with the month before (the "Wrapped"-style recap). */
data class MonthSummary(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    val previousExpense: Double,
    /** Every expense category of the month, biggest first (the AI summary sends these). */
    val allCategories: List<CategoryShare>,
    val biggestExpense: Transaction?,
    val transactionCount: Int,
    /** The month is still running (its previous month was cut to the same days). */
    val partial: Boolean = false,
) {
    val net: Double get() = income - expense

    val topCategories: List<CategoryShare> get() = allCategories.take(3)

    /** Expense change vs the previous month in percent, null when there is nothing to compare with. */
    val expenseChangePercent: Int? get() = if (previousExpense <= 0) null else Math.round((expense - previousExpense) / previousExpense * 100).toInt()

    val isEmpty: Boolean get() = transactionCount == 0

    data class CategoryShare(val category: String, val amount: Double, val previousAmount: Double)

    companion object {
        /** All sums in UAH via [toUah]; transfers move money between wallets and are left out. */
        /** [upToDay]: for the month in progress (its day number), the previous month is cut to the
         * same days (1–21 Oct vs 1–21 Sep), or a fresh month always looks "cheaper". */
        fun compute(transactions: List<Transaction>, month: YearMonth, upToDay: Int? = null, toUah: (Transaction) -> Double): MonthSummary {
            val previous = month.minusMonths(1)
            val current = transactions.filter { FinancialMonth.contains(month, it.date) && it.type != TxType.TRANSFER }
            val previousCutoff = upToDay?.let { FinancialMonth.cutoff(previous, it).toString() }
            val previousExpenses = transactions.filter {
                FinancialMonth.contains(previous, it.date) && it.type == TxType.EXPENSE && (previousCutoff == null || it.date.take(10) <= previousCutoff)
            }
            val expenses = current.filter { it.type == TxType.EXPENSE }
            val previousByCategory = previousExpenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf(toUah) }
            val all = expenses.groupBy { it.category }
                .map { (cat, l) -> CategoryShare(cat, l.sumOf(toUah), previousByCategory[cat] ?: 0.0) }
                .sortedByDescending { it.amount }
            return MonthSummary(
                month = month,
                income = current.filter { it.type == TxType.INCOME }.sumOf(toUah),
                expense = expenses.sumOf(toUah),
                previousExpense = previousExpenses.sumOf(toUah),
                allCategories = all,
                biggestExpense = expenses.maxByOrNull(toUah),
                transactionCount = current.size,
                partial = upToDay != null,
            )
        }
    }
}
