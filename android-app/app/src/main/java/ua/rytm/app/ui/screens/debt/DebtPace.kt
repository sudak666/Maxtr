package ua.rytm.app.ui.screens.debt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Typical payment and, when dates allow, the typical gap between payments. */
data class DebtPace(val typicalPayment: Double, val typicalGapDays: Long?)

object DebtPaceCalc {
    const val WINDOW = 6

    /**
     * Median of the last [WINDOW] paydowns (payments that actually lowered the
     * balance). The all-time mean let one early lump sum dominate: a 75 000 $
     * debt with one 20 000 $ payment and then ~120 $ a month showed "≈ 47
     * payments left, average 1 166 $" — off by ~10x.
     */
    fun compute(start: Double, entries: List<DebtEntry>): DebtPace? {
        var prev = start
        val downs = mutableListOf<Pair<Double, String>>()
        entries.forEach { e ->
            val d = prev - e.balance
            if (d > 0) downs += d to e.date
            prev = e.balance
        }
        if (downs.isEmpty()) return null
        val recent = downs.takeLast(WINDOW)
        val typical = median(recent.map { it.first })
        val dates = recent.mapNotNull { parseDate(it.second) }.sorted()
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.filter { it > 0 }
        return DebtPace(typical, if (gaps.size >= 2) median(gaps.map { it.toDouble() }).toLong() else null)
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private val formats = listOf(DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ISO_LOCAL_DATE)
    private fun parseDate(s: String): LocalDate? = formats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s.trim(), f) }.getOrNull() }
}
