package ua.rytm.app.ui.screens.finance

data class BudgetExceededFeedback(val category: String, val spent: Double, val limit: Double)

fun budgetExceededFeedback(category: String, limit: Double?, existingMonthAmountsUah: List<Double>, savedAmountUah: Double): BudgetExceededFeedback? {
    if (limit == null || limit <= 0) return null
    val spent = existingMonthAmountsUah.sum() + savedAmountUah
    return if (spent > limit) BudgetExceededFeedback(category, spent, limit) else null
}
