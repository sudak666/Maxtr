package ua.rytm.app.ui.screens.finance

import java.time.LocalDate

/**
 * The «Капітал» sheet's numbers: capital (wallets marked «in capital») at the
 * end of every day of a period, where the money sits now, and in which
 * currencies. Past days use today's rates — the app keeps no rate history, so
 * the line shows what the user did with money, not exchange-rate noise.
 */
object CapitalHistory {
    data class Point(val date: LocalDate, val uah: Double)
    data class Share(val label: String, val uah: Double, val fraction: Double)

    fun series(
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        from: LocalDate,
        to: LocalDate,
        toUah: (Double, String) -> Double,
    ): List<Point> {
        val counted = wallets.filter { it.inCapital }.associateBy { it.id }
        if (counted.isEmpty() || from > to) return emptyList()
        val balances = counted.mapValues { it.value.openingBalance }.toMutableMap()
        fun apply(t: Transaction) {
            when (t.type) {
                TxType.INCOME -> balances.computeIfPresent(t.walletId) { _, b -> b + t.amount }
                TxType.EXPENSE -> balances.computeIfPresent(t.walletId) { _, b -> b - t.amount }
                TxType.TRANSFER -> {
                    balances.computeIfPresent(t.walletId) { _, b -> b - t.amount }
                    t.targetWalletId?.let { id -> balances.computeIfPresent(id) { _, b -> b + (t.targetAmount ?: t.amount) } }
                }
            }
        }
        val byDate = transactions.groupBy { it.date }
        val fromIso = from.toString()
        transactions.filter { it.date < fromIso }.forEach(::apply)
        fun total() = balances.entries.sumOf { (id, b) -> toUah(b, counted.getValue(id).currency) }
        val out = ArrayList<Point>()
        var day = from
        while (day <= to) {
            byDate[day.toString()]?.forEach(::apply)
            out += Point(day, total())
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * Percent change, or null when it would mislead: from zero or a negative
     * start (−20 ₴ → 4 980 ₴ is not «+25 000 %»), or past ±1000 %.
     */
    fun percentChange(first: Double, last: Double): Double? {
        if (first <= 0.0) return null
        val pct = (last - first) / first * 100
        return pct.takeIf { kotlin.math.abs(it) < 1000 }
    }

    /** Positive balances only: a credit card's debt is not «where the money is». */
    fun shares(items: List<Pair<String, Double>>): List<Share> {
        val positive = items.filter { it.second > 0.005 }
        val sum = positive.sumOf { it.second }
        if (sum <= 0) return emptyList()
        return positive.groupBy({ it.first }, { it.second })
            .map { (label, values) -> Share(label, values.sum(), values.sum() / sum) }
            .sortedByDescending { it.uah }
    }
}
