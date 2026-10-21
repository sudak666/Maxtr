package ua.rytm.app.ui.screens.finance

import java.time.YearMonth

/** A month in money, compared with the month before (the "Wrapped"-style recap). */
data class MonthSummary(
    val month: YearMonth,
    val income: Double,
    val expense: Double,
    val previousExpense: Double,
    val topCategories: List<CategoryShare>,
    val biggestExpense: Transaction?,
    val transactionCount: Int,
) {
    val net: Double get() = income - expense

    /** Expense change vs the previous month in percent, null when there is nothing to compare with. */
    val expenseChangePercent: Int? get() = if (previousExpense <= 0) null else Math.round((expense - previousExpense) / previousExpense * 100).toInt()

    val isEmpty: Boolean get() = transactionCount == 0

    data class CategoryShare(val category: String, val amount: Double, val previousAmount: Double)

    companion object {
        /** All sums in UAH via [toUah]; transfers move money between wallets and are left out. */
        /** [upToDay]: for the month in progress, the previous month is cut to the
         * same days (1–21 Oct vs 1–21 Sep), or a fresh month always looks "cheaper". */
        fun compute(transactions: List<Transaction>, month: YearMonth, upToDay: Int? = null, toUah: (Transaction) -> Double): MonthSummary {
            val prefix = month.toString()
            val previousPrefix = month.minusMonths(1).toString()
            val current = transactions.filter { it.date.startsWith(prefix) && it.type != TxType.TRANSFER }
            val previousCutoff = upToDay?.let { "$previousPrefix-%02d".format(minOf(it, month.minusMonths(1).lengthOfMonth())) }
            val previousExpenses = transactions.filter {
                it.date.startsWith(previousPrefix) && it.type == TxType.EXPENSE && (previousCutoff == null || it.date.take(10) <= previousCutoff)
            }
            val expenses = current.filter { it.type == TxType.EXPENSE }
            val previousByCategory = previousExpenses.groupBy { it.category }.mapValues { (_, l) -> l.sumOf(toUah) }
            val top = expenses.groupBy { it.category }
                .map { (cat, l) -> CategoryShare(cat, l.sumOf(toUah), previousByCategory[cat] ?: 0.0) }
                .sortedByDescending { it.amount }
                .take(3)
            return MonthSummary(
                month = month,
                income = current.filter { it.type == TxType.INCOME }.sumOf(toUah),
                expense = expenses.sumOf(toUah),
                previousExpense = previousExpenses.sumOf(toUah),
                topCategories = top,
                biggestExpense = expenses.maxByOrNull(toUah),
                transactionCount = current.size,
            )
        }
    }
}
