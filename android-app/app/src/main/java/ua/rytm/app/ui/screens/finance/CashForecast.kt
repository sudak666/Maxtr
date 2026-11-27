package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.nextRecurringDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A planned recurring payment or income. [amountUah] is negative for an expense. */
data class CashEvent(val date: LocalDate, val label: String, val amountUah: Double, val balanceAfter: Double)

/** One day of the path: the balance at its end, after usual spending and that day's [events]. */
data class ForecastDay(val date: LocalDate, val balance: Double, val events: List<CashEvent> = emptyList())

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
    /** Every day from today to [end], so the screen can draw the path and pick the days that matter. */
    val path: List<ForecastDay> = emptyList(),
) {
    val dailySpend: Double? get() = habit?.perDay

    /**
     * The days worth a row: planned events, the day the money runs out, the
     * deepest day and the day it is back above zero (outside review,
     * 2026-11-27: the list held only the salary, while the gap it explained
     * happened on two days not in it).
     */
    val keyDays: List<ForecastDay>
        get() {
            val c = crossings
            return path.filter { it.events.isNotEmpty() || it.date == shortfallDate || it.date == lowestDate || it.date in c }
        }

    /**
     * Every day the balance crosses zero: true going below, false coming back.
     * A month-long horizon can dip twice (before one salary and the next).
     */
    val crossings: Map<LocalDate, Boolean>
        get() {
            val out = LinkedHashMap<LocalDate, Boolean>()
            var previous = startBalance
            path.forEach { d ->
                if (previous >= 0 && d.balance < 0) out[d.date] = true
                else if (previous < 0 && d.balance >= 0) out[d.date] = false
                previous = d.balance
            }
            return out
        }

    /** The first day back at or above zero after [shortfallDate]; null without a gap or a way back. */
    val recoveredDate: LocalDate?
        get() = shortfallDate?.let { gap -> path.firstOrNull { it.date.isAfter(gap) && it.balance >= 0 }?.date }

    companion object {
        private const val WINDOW_DAYS = 60L
        private const val MIN_HISTORY_DAYS = 14L
        private const val GUARD = 62
        private const val TOP_CATEGORIES = 3
        private const val RECURRING_MARK = "повторювана"
        /** Fewer days left than this and the forecast reaches to the next month's end. */
        private const val MIN_HORIZON_DAYS = 14L

        /**
         * The last day of the forecast: this financial month's end, or the next
         * one's once fewer than two weeks are left. On the 27th a month-end
         * forecast covered three days and could not say what comes after the
         * salary (Copilot and Monarch look a month ahead for the same reason).
         */
        fun horizonEnd(today: LocalDate): LocalDate {
            val month = ua.rytm.app.data.FinancialMonth.of(today)
            val end = ua.rytm.app.data.FinancialMonth.lastDay(month)
            return if (ChronoUnit.DAYS.between(today, end) < MIN_HORIZON_DAYS) ua.rytm.app.data.FinancialMonth.lastDay(month.plusMonths(1)) else end
        }

        /**
         * The forecast for spendable wallets (in capital, not deposit,
         * credit or money owed to you) — one entry point, so Finance and the home-screen widget can
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
            val spendableWallets = wallets.filter { it.isSpendable }
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
            val path = mutableListOf<ForecastDay>()
            var balance = balanceUah
            var lowest = 0.0
            var lowestDate: LocalDate? = null
            var shortfallDate: LocalDate? = null
            var day = today
            while (!day.isAfter(end)) {
                // Today's spending is already in the balance, or still unknown.
                if (day != today) balance -= daily
                val dayEvents = planned[day].orEmpty().map { (date, label, amount) ->
                    balance += amount
                    CashEvent(date, label, amount, balance)
                }
                events += dayEvents
                path += ForecastDay(day, balance, dayEvents)
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
                path = path,
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
