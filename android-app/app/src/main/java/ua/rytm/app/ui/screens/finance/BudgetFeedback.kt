package ua.rytm.app.ui.screens.finance

data class BudgetExceededFeedback(val category: String, val spent: Double, val limit: Double)

fun budgetExceededFeedback(category: String, limit: Double?, existingMonthAmountsUah: List<Double>, savedAmountUah: Double): BudgetExceededFeedback? {
    if (limit == null || limit <= 0) return null
    val spent = existingMonthAmountsUah.sum() + savedAmountUah
    return if (isOverBudget(spent, limit)) BudgetExceededFeedback(category, spent, limit) else null
}

/** Compared in kopecks: 16.10 + 70.20 as Doubles is 86.30000000000001, not over an 86.30 limit. Same rule as the PWA and the push sweep. */
fun isOverBudget(spent: Double, limit: Double): Boolean = Math.round(spent * 100) > Math.round(limit * 100)
