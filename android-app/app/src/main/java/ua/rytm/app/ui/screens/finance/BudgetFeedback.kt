package ua.rytm.app.ui.screens.finance

data class BudgetExceededFeedback(val category: String, val spent: Double, val limit: Double)

fun budgetExceededFeedback(category: String, limit: Double?, existingMonthAmountsUah: List<Double>, savedAmountUah: Double): BudgetExceededFeedback? {
    if (limit == null || limit <= 0) return null
    val spent = existingMonthAmountsUah.sum() + savedAmountUah
    return if (isOverBudget(spent, limit)) BudgetExceededFeedback(category, spent, limit) else null
}

/** Compared in kopecks: 16.10 + 70.20 as Doubles is 86.30000000000001, not over an 86.30 limit. Same rule as the PWA and the push sweep. */
fun isOverBudget(spent: Double, limit: Double): Boolean = Math.round(spent * 100) > Math.round(limit * 100)

enum class BudgetPace { ON_TRACK, AHEAD, OVER }

/**
 * Colour by pace, not by a fixed share: 78 % spent on day 26 of 30 is on
 * track, 60 % on day 5 is not. Amber when spending runs ten points ahead of
 * the month and is past half the limit, or from 90 % whatever the day; red
 * once over. [elapsed] is the share of the budget month gone, 0..1.
 */
fun budgetPace(spent: Double, limit: Double, elapsed: Double): BudgetPace {
    if (limit <= 0) return BudgetPace.ON_TRACK
    if (isOverBudget(spent, limit)) return BudgetPace.OVER
    val share = spent / limit
    return if (share >= 0.9 || (share >= 0.5 && share > elapsed + 0.1)) BudgetPace.AHEAD else BudgetPace.ON_TRACK
}

/** Share of the current budget month gone by the end of [today], 0..1. */
fun budgetMonthElapsed(today: java.time.LocalDate): Double {
    val month = ua.rytm.app.data.FinancialMonth.of(today)
    val total = java.time.temporal.ChronoUnit.DAYS.between(ua.rytm.app.data.FinancialMonth.firstDay(month), ua.rytm.app.data.FinancialMonth.lastDay(month)) + 1
    return (ua.rytm.app.data.FinancialMonth.dayNumber(today).toDouble() / total).coerceIn(0.0, 1.0)
}
