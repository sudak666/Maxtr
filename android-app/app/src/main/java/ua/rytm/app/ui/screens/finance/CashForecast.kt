package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.nextRecurringDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A planned recurring payment or income. [amountUah] is negative for an expense. */
data class CashEvent(val date: LocalDate, val label: String, val amountUah: Double, val balanceAfter: Double)

/**
 * The balance day by day until the month's last day [end]. [dailySpend] is null
 * with too little history; then only planned events count.
 * [shortfallDate] is the first day below zero, [shortfall] the deepest gap.
 * Shift pay stays out on purpose, as in safeToSpendPerDay: it often lands next month.
 */
data class CashForecast(
    val end: LocalDate,
    val dailySpend: Double?,
    val events: List<CashEvent>,
    val endBalance: Double,
    val shortfallDate: LocalDate?,
    val shortfall: Double,
) {
    companion object {
        private const val WINDOW_DAYS = 60L
        private const val MIN_HISTORY_DAYS = 14L
        private const val GUARD = 62

        fun compute(
            balanceUah: Double,
            recurring: List<Recurring>,
            currency: (String) -> String?,
            today: LocalDate,
            end: LocalDate,
            dailySpend: Double?,
            toUah: (Double, String) -> Double,
        ): CashForecast {
            val planned = recurring.filter { it.active && it.amount > 0 && it.type != TxType.TRANSFER }.flatMap { r ->
                val signed = toUah(r.amount, currency(r.walletId) ?: "UAH").let { if (r.type == TxType.EXPENSE) -it else it }
                val label = r.comment.ifBlank { r.category }
                buildList {
                    var date = runCatching { LocalDate.parse(r.nextDate) }.getOrNull() ?: return@buildList
                    var guard = 0
                    while (!date.isAfter(end) && guard < GUARD) {
                        if (!date.isBefore(today)) add(Triple(date, label, signed))
                        date = nextRecurringDate(date, r.frequency, r.anchorDay)
                        guard++
                    }
                }
            }.groupBy { it.first }

            val events = mutableListOf<CashEvent>()
            var balance = balanceUah
            var lowest = 0.0
            var shortfallDate: LocalDate? = null
            var day = today
            while (!day.isAfter(end)) {
                // Today's spending is already in the balance, or still unknown.
                if (day != today) balance -= dailySpend ?: 0.0
                planned[day].orEmpty().forEach { (date, label, amount) ->
                    balance += amount
                    events += CashEvent(date, label, amount, balance)
                }
                if (balance < 0 && shortfallDate == null) shortfallDate = day
                lowest = minOf(lowest, balance)
                day = day.plusDays(1)
            }
            return CashForecast(end, dailySpend, events, balance, shortfallDate, -lowest)
        }

        /**
         * Usual unplanned spending per day over the last 60 days before [today],
         * in UAH. The top tenth of days is dropped, so one laptop does not read
         * as a habit. [expenses] holds expenses only; recurring occurrences are
         * left out, since they are already planned events.
         */
        fun dailySpend(expenses: List<Transaction>, today: LocalDate, toUah: (Double, String) -> Double): Double? {
            val parsed = expenses.mapNotNull { t -> runCatching { LocalDate.parse(t.date.take(10)) }.getOrNull()?.let { it to t } }
            val first = parsed.minOfOrNull { it.first } ?: return null
            val days = minOf(WINDOW_DAYS, ChronoUnit.DAYS.between(first, today))
            if (days < MIN_HISTORY_DAYS) return null
            val from = today.minusDays(days)
            val byDay = parsed.filter { !it.first.isBefore(from) && it.first.isBefore(today) && !it.second.id.startsWith("rec_") }
                .groupBy({ it.first }, { toUah(it.second.amount, it.second.currency) })
                .mapValues { it.value.sum() }
            val all = byDay.values + List((days - byDay.size).toInt()) { 0.0 }
            val kept = all.sorted().dropLast((days / 10).toInt())
            return kept.sum() / kept.size
        }
    }
}
