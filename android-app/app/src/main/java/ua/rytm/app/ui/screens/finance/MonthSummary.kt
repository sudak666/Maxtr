package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.FinancialMonth
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.ChronoUnit

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
    /** Days of the month counted so far (all of them for a finished month). */
    val days: Int = 0,
    /** Of [days], those with no expense at all, and the longest run of them. */
    val noSpendDays: Int = 0,
    val longestNoSpendRun: Int = 0,
    /** The weekday with the highest average spending per such day, and that average. */
    val priciestWeekday: DayOfWeek? = null,
    val priciestWeekdayAverage: Double = 0.0,
    val expenseCount: Int = 0,
) {
    /** The average expense, «середній чек». */
    val averageExpense: Double get() = if (expenseCount > 0) expense / expenseCount else 0.0

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
            // Day by day over the days counted so far: the story cards' facts
            // (days without spending, the priciest weekday) that the tables
            // below the cards do not already show.
            val first = FinancialMonth.firstDay(month)
            val last = upToDay?.let { FinancialMonth.cutoff(month, it) } ?: FinancialMonth.lastDay(month)
            val dayCount = (ChronoUnit.DAYS.between(first, last) + 1).toInt().coerceAtLeast(0)
            val byDay = expenses.groupBy { it.date.take(10) }.mapValues { (_, l) -> l.sumOf(toUah) }
            val dates = (0 until dayCount).map { first.plusDays(it.toLong()) }
            val spent = dates.map { byDay[it.toString()] ?: 0.0 }
            var run = 0
            var longest = 0
            spent.forEach { v -> if (v <= 0.0) { run++; longest = maxOf(longest, run) } else run = 0 }
            val weekday = dates.indices.groupBy({ dates[it].dayOfWeek }, { spent[it] })
                .mapValues { (_, l) -> l.average() }
                .filterValues { it > 0 }
                .maxByOrNull { it.value }
            return MonthSummary(
                month = month,
                income = current.filter { it.type == TxType.INCOME }.sumOf(toUah),
                expense = expenses.sumOf(toUah),
                previousExpense = previousExpenses.sumOf(toUah),
                allCategories = all,
                biggestExpense = expenses.maxByOrNull(toUah),
                transactionCount = current.size,
                partial = upToDay != null,
                days = dayCount,
                noSpendDays = spent.count { it <= 0.0 },
                longestNoSpendRun = longest,
                priciestWeekday = weekday?.key,
                priciestWeekdayAverage = weekday?.value ?: 0.0,
                expenseCount = expenses.size,
            )
        }
    }
}
