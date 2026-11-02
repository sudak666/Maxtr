package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.nextRecurringDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
    /** [dailySpend] times the days left after today. */
    val unplannedSpend: Double,
    val events: List<CashEvent>,
    val endBalance: Double,
    val shortfallDate: LocalDate?,
    val shortfall: Double,
) {
    companion object {
        private const val WINDOW_DAYS = 60L
        private const val MIN_HISTORY_DAYS = 14L
        private const val GUARD = 62
        private const val RECURRING_MARK = "повторювана"

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
                val label = eventLabel(r)
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
            val unplanned = (dailySpend ?: 0.0) * ChronoUnit.DAYS.between(today, end).coerceAtLeast(0)
            return CashForecast(end, dailySpend, unplanned, events, balance, shortfallDate, -lowest)
        }

        /** The category, as in the history list, plus the user's own note. */
        internal fun eventLabel(r: Recurring): String {
            val note = r.comment.split(" · ").map(String::trim).filter { it.isNotEmpty() && it != RECURRING_MARK }.joinToString(" · ")
            return if (note.isEmpty() || note.equals(r.category, ignoreCase = true)) r.category else "${r.category} · $note"
        }

        /** An expense that repeats a planned payment (rent paid by hand, say) is not a habit. */
        private fun repeatsPlanned(t: Transaction, planned: List<Recurring>) = planned.any {
            it.type == TxType.EXPENSE && it.category == t.category && abs(t.amount - it.amount) <= it.amount * 0.1
        }

        /**
         * Usual unplanned spending per day over the last 60 days before [today],
         * in UAH. The top tenth of days is dropped, so one laptop does not read
         * as a habit. [expenses] holds expenses only; recurring occurrences and
         * expenses that repeat one of [planned] are left out, since they are
         * already planned events.
         */
        fun dailySpend(expenses: List<Transaction>, today: LocalDate, toUah: (Double, String) -> Double, planned: List<Recurring> = emptyList()): Double? {
            val active = planned.filter { it.active && it.amount > 0 }
            val parsed = expenses.mapNotNull { t -> runCatching { LocalDate.parse(t.date.take(10)) }.getOrNull()?.let { it to t } }
            val first = parsed.minOfOrNull { it.first } ?: return null
            val days = minOf(WINDOW_DAYS, ChronoUnit.DAYS.between(first, today))
            if (days < MIN_HISTORY_DAYS) return null
            val from = today.minusDays(days)
            val byDay = parsed.filter { !it.first.isBefore(from) && it.first.isBefore(today) && !it.second.id.startsWith("rec_") && !repeatsPlanned(it.second, active) }
                .groupBy({ it.first }, { toUah(it.second.amount, it.second.currency) })
                .mapValues { it.value.sum() }
            val all = byDay.values + List((days - byDay.size).toInt()) { 0.0 }
            val kept = all.sorted().dropLast((days / 10).toInt())
            return kept.sum() / kept.size
        }
    }
}
