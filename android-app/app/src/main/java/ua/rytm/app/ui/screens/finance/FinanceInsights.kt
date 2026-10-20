package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure calculations behind the Finance screen, split out of FinanceViewModel
 * so they are unit-testable and the ViewModel only owns state + actions.
 * [toUah] converts an amount in a currency to UAH.
 */
object FinanceInsights {

    /**
     * "Safe to spend": a daily allowance until the month ends — (balance −
     * recurring expenses still due this month) ÷ days left, today included.
     * Expected shift pay is left out on purpose: it often lands next month.
     * Null when the balance does not even cover the bills (the UI says so).
     */
    fun safeToSpendPerDay(balanceUah: Double, recurringOutUah: Double, today: LocalDate): Double? {
        val daysLeft = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1
        val free = balanceUah - recurringOutUah
        return if (free <= 0) null else free / daysLeft
    }

    /** Income or expense of [month] in UAH (transfers never count). */
    fun monthTotalUah(transactions: List<Transaction>, type: TxType, month: YearMonth, toUah: (Double, String) -> Double): Double {
        val prefix = month.toString()
        return transactions.filter { it.type == type && it.date.startsWith(prefix) }.sumOf { toUah(it.amount, it.currency) }
    }

    /**
     * Recurring payments still due between [today] and the month's end, in UAH,
     * plus the shift earnings still expected ([shiftsEarnings]).
     */
    fun monthOutlook(
        recurring: List<Recurring>,
        walletCurrency: (String) -> String?,
        today: LocalDate,
        shiftsEarnings: Double,
        toUah: (Double, String) -> Double,
    ): MonthOutlook {
        val end = YearMonth.from(today).atEndOfMonth()
        var outgoing = 0.0
        var incoming = 0.0
        recurring.filter { it.active && it.amount > 0 }.forEach { r ->
            val currency = walletCurrency(r.walletId) ?: "UAH"
            var date = runCatching { LocalDate.parse(r.nextDate) }.getOrNull() ?: return@forEach
            var guard = 0
            while (!date.isAfter(end) && guard < 62) {
                if (!date.isBefore(today)) {
                    val uah = toUah(r.amount, currency)
                    if (r.type == TxType.EXPENSE) outgoing += uah else incoming += uah
                }
                date = when (r.frequency) { "daily" -> date.plusDays(1); "weekly" -> date.plusWeeks(1); else -> date.plusMonths(1) }
                guard++
            }
        }
        return MonthOutlook(shiftsEarnings = shiftsEarnings, recurringOut = outgoing, recurringIn = incoming)
    }

    /**
     * Shared profile, [month]: income/expense per person who added the entries.
     * Empty unless at least two people added something; the current user first.
     */
    fun contributions(transactions: List<Transaction>, month: YearMonth, myUid: String?, toUah: (Double, String) -> Double): List<Contribution> {
        val prefix = month.toString()
        val byPerson = transactions.asSequence()
            .filter { it.date.startsWith(prefix) && it.type != TxType.TRANSFER }
            .mapNotNull { t -> t.createdBy?.let { it to t } }
            .groupBy({ it.first }, { it.second })
        if (byPerson.size < 2) return emptyList()
        return byPerson.map { (uid, txs) ->
            Contribution(
                name = txs.firstNotNullOfOrNull { it.createdByName } ?: "?",
                isMe = uid == myUid,
                income = txs.filter { it.type == TxType.INCOME }.sumOf { toUah(it.amount, it.currency) },
                expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { toUah(it.amount, it.currency) },
            )
        }.sortedByDescending { it.isMe }
    }

    /** Most used categories of [type] since [since] among [known] — the form's one-tap picks. */
    fun frequentCategories(transactions: List<Transaction>, type: TxType, known: Set<String>, since: LocalDate, limit: Int = 6): List<String> {
        if (type == TxType.TRANSFER) return emptyList()
        val from = since.toString()
        return transactions.asSequence()
            .filter { it.type == type && it.date >= from && it.category in known }
            .groupingBy { it.category }.eachCount()
            .entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** Mirrors renderFinance()'s filter chain: type -> period -> category -> search -> newest first. */
    fun filter(
        transactions: List<Transaction>,
        typeFilter: TxTypeFilter,
        periodFilter: PeriodFilter,
        categoryFilter: String?,
        search: String,
        today: LocalDate,
        walletName: (String?) -> String,
        tagNames: Map<String, String>,
    ): List<Transaction> {
        var result = when (typeFilter) {
            TxTypeFilter.ALL -> transactions
            TxTypeFilter.INCOME -> transactions.filter { it.type == TxType.INCOME }
            TxTypeFilter.EXPENSE -> transactions.filter { it.type == TxType.EXPENSE }
            TxTypeFilter.TRANSFER -> transactions.filter { it.type == TxType.TRANSFER }
        }
        result = when (periodFilter) {
            PeriodFilter.DAY -> { val d = today.toString(); result.filter { it.date == d } }
            PeriodFilter.MONTH -> { val p = YearMonth.from(today).toString(); result.filter { it.date.startsWith(p) } }
            PeriodFilter.ALL -> result
        }
        categoryFilter?.let { cat -> result = result.filter { it.category == cat } }
        if (search.isNotBlank()) {
            // "кафе жовтень більше 200": month/date/amount words become filters (SmartSearch).
            val smart = SmartSearch.parse(search, today)
            if (smart.hasFilters) result = result.filter(smart::matches)
            val q = smart.text.trim()
            // "#кава" finds the tag "кава"; a bare word matches tag names too.
            val tagQuery = q.removePrefix("#")
            if (q.isNotEmpty()) result = result.filter { t ->
                listOfNotNull(t.comment, t.category, t.subcategory, walletName(t.walletId), walletName(t.targetWalletId), t.currency, t.targetCurrency)
                    .any { it.lowercase().contains(q) } ||
                    (tagQuery.isNotEmpty() && t.tags.any { tagNames[it]?.lowercase()?.contains(tagQuery) == true })
            }
        }
        return result.sortedByDescending { it.date }
    }
}
