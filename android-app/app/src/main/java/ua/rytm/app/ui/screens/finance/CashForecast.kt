package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.nextRecurringDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A planned recurring payment or income. [amountUah] is negative for an expense. */
data class CashEvent(val date: LocalDate, val label: String, val amountUah: Double, val balanceAfter: Double)

/**
 * Usual unplanned spending, as the sheet explains it: [perDay] is the average
 * over [windowDays] days with the [droppedDays] biggest days left out;
 * [topCategories] are the categories behind it, biggest first, in UAH over the
 * kept days.
 */
data class SpendingHabit(
    val perDay: Double,
    val windowDays: Int,
    val droppedDays: Int,
    val topCategories: List<Pair<String, Double>> = emptyList(),
)

/**
 * The balance day by day until the month's last day [end], built so the sheet
 * can show it as a sum: [startBalance] + [plannedIn] − [plannedOut] −
 * [unplannedSpend] = [endBalance]. [habit] is null with too little history;
 * then only planned events count. [shortfallDate] is the first day below zero,
 * [shortfall] the deepest gap, reached on [lowestDate].
 * Shift pay stays out on purpose: it arrives as the salary, which the user
 * enters as a recurring income, so counting both would count it twice.
 */
data class CashForecast(
    val end: LocalDate,
    val startBalance: Double,
    val habit: SpendingHabit?,
    val plannedIn: Double,
    val plannedOut: Double,
    /** [SpendingHabit.perDay] times [unplannedDays], the days left after today. */
    val unplannedSpend: Double,
    val unplannedDays: Int,
    val events: List<CashEvent>,
    val endBalance: Double,
    val shortfallDate: LocalDate?,
    val shortfall: Double,
    val lowestDate: LocalDate?,
) {
    val dailySpend: Double? get() = habit?.perDay

    companion object {
        private const val WINDOW_DAYS = 60L
        private const val MIN_HISTORY_DAYS = 14L
        private const val GUARD = 62
        private const val TOP_CATEGORIES = 3
        private const val RECURRING_MARK = "повторювана"

        /**
         * The forecast for spendable wallets (in capital, not deposit or
         * credit) — one entry point, so Finance and the home-screen widget can
         * never show two different numbers.
         */
        fun forProfile(
            wallets: List<Wallet>,
            transactions: List<Transaction>,
            recurring: List<Recurring>,
            today: LocalDate,
            end: LocalDate,
            toUah: (Double, String) -> Double,
        ): CashForecast {
            val spendableWallets = wallets.filter { it.inCapital && it.kind != "deposit" && it.kind != "credit" }
            val spendable = spendableWallets.map { it.id }.toSet()
            val balance = spendableWallets.sumOf { w -> toUah(ua.rytm.app.data.FinanceRepository.walletBalance(transactions, w.id, w.openingBalance), w.currency) }
            val expenses = transactions.forStats(wallets).filter { it.type == TxType.EXPENSE && it.walletId in spendable }
            val planned = recurring.filter { it.walletId in spendable }
            return compute(balance, planned, { id -> wallets.firstOrNull { it.id == id }?.currency }, today, end, habit(expenses, today, toUah, planned), toUah)
        }

        fun compute(
            balanceUah: Double,
            recurring: List<Recurring>,
            currency: (String) -> String?,
            today: LocalDate,
            end: LocalDate,
            habit: SpendingHabit?,
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

            val daily = habit?.perDay ?: 0.0
            val events = mutableListOf<CashEvent>()
            var balance = balanceUah
            var lowest = 0.0
            var lowestDate: LocalDate? = null
            var shortfallDate: LocalDate? = null
            var day = today
            while (!day.isAfter(end)) {
                // Today's spending is already in the balance, or still unknown.
                if (day != today) balance -= daily
                planned[day].orEmpty().forEach { (date, label, amount) ->
                    balance += amount
                    events += CashEvent(date, label, amount, balance)
                }
                if (balance < 0 && shortfallDate == null) shortfallDate = day
                if (balance < lowest) { lowest = balance; lowestDate = day }
                day = day.plusDays(1)
            }
            val days = ChronoUnit.DAYS.between(today, end).coerceAtLeast(0).toInt()
            return CashForecast(
                end = end,
                startBalance = balanceUah,
                habit = habit,
                plannedIn = events.filter { it.amountUah > 0 }.sumOf { it.amountUah },
                plannedOut = -events.filter { it.amountUah < 0 }.sumOf { it.amountUah },
                unplannedSpend = daily * days,
                unplannedDays = days,
                events = events,
                endBalance = balance,
                shortfallDate = shortfallDate,
                shortfall = -lowest,
                lowestDate = lowestDate,
            )
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
        fun habit(expenses: List<Transaction>, today: LocalDate, toUah: (Double, String) -> Double, planned: List<Recurring> = emptyList()): SpendingHabit? {
            val active = planned.filter { it.active && it.amount > 0 }
            val parsed = expenses.mapNotNull { t -> runCatching { LocalDate.parse(t.date.take(10)) }.getOrNull()?.let { it to t } }
            val first = parsed.minOfOrNull { it.first } ?: return null
            val days = minOf(WINDOW_DAYS, ChronoUnit.DAYS.between(first, today))
            if (days < MIN_HISTORY_DAYS) return null
            val from = today.minusDays(days)
            val byDay = parsed.filter { !it.first.isBefore(from) && it.first.isBefore(today) && !it.second.id.startsWith("rec_") && !repeatsPlanned(it.second, active) }
                .groupBy({ it.first }, { it.second })
            val totals = byDay.mapValues { (_, txs) -> txs.sumOf { toUah(it.amount, it.currency) } }
            val dropCount = (days / 10).toInt()
            // Quiet days are zero and never among the dropped ones.
            val dropped = totals.entries.sortedByDescending { it.value }.take(dropCount).map { it.key }.toSet()
            val keptDays = byDay.filterKeys { it !in dropped }
            val keptSum = keptDays.keys.sumOf { totals.getValue(it) }
            val top = keptDays.values.flatten()
                .groupBy({ it.category }, { toUah(it.amount, it.currency) })
                .mapValues { it.value.sum() }
                .entries.sortedByDescending { it.value }.take(TOP_CATEGORIES).map { it.key to it.value }
            // Whole kopecks, so the sheet's sum adds up to the last digit.
            val perDay = kotlin.math.round(keptSum / (days - dropCount) * 100) / 100
            return SpendingHabit(perDay, days.toInt(), dropCount, top)
        }

        fun dailySpend(expenses: List<Transaction>, today: LocalDate, toUah: (Double, String) -> Double, planned: List<Recurring> = emptyList()): Double? =
            habit(expenses, today, toUah, planned)?.perDay
    }
}
