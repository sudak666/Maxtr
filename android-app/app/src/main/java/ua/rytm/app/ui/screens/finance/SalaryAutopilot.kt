package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Shifts ↔ money on payday: recognise an income as last month's pay (by the
 * amount the shift calendar says was earned) and suggest how much of it to
 * move to each savings goal. Suggestions only; nothing moves without a tap.
 */
object SalaryAutopilot {
    /** An income within ±30% of what last month's shifts earned is treated as that pay. */
    fun matchesExpectedPay(amountUah: Double, expectedUah: Double): Boolean =
        expectedUah > 0 && amountUah > 0 && abs(amountUah - expectedUah) <= expectedUah * 0.30

    data class GoalShare(val goalId: String, val walletId: String, val amount: Double)

    /**
     * Each unfinished goal gets what it still needs divided by the months left
     * to its date (at least one month), capped at what it still needs; the total
     * never exceeds half of [income], so payday never empties the wallet.
     * Only goals whose wallet uses the income's currency and isn't the income's own wallet.
     */
    fun suggest(
        income: Double,
        incomeWalletId: String,
        incomeCurrency: String,
        goals: List<Goal>,
        walletCurrency: (String) -> String?,
        walletBalance: (String) -> Double,
        today: LocalDate,
    ): List<GoalShare> {
        val raw = goals.mapNotNull { g ->
            if (g.walletId == incomeWalletId || walletCurrency(g.walletId) != incomeCurrency) return@mapNotNull null
            val remaining = g.targetAmount - walletBalance(g.walletId)
            if (remaining < 1) return@mapNotNull null
            val months = runCatching { LocalDate.parse(g.targetDate) }.getOrNull()
                ?.let { ChronoUnit.MONTHS.between(today.withDayOfMonth(1), it.withDayOfMonth(1)) }
                ?.coerceAtLeast(1) ?: 12
            GoalShare(g.id, g.walletId, minOf(remaining, remaining / months))
        }
        val budget = income * 0.5
        val total = raw.sumOf { it.amount }
        val scale = if (total > budget && total > 0) budget / total else 1.0
        return raw.map { it.copy(amount = Math.floor(it.amount * scale)) }.filter { it.amount >= 1 }
    }
}
