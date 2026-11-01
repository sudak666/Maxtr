package ua.rytm.app.ui.screens.debt

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Typical payment and, when dates allow, the typical gap between payments. */
data class DebtPace(val typicalPayment: Double, val typicalGapDays: Long?, val recentPayment: Double = typicalPayment)

object DebtPaceCalc {
    const val WINDOW = 6
    private const val RECENT_WINDOW = 3

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
        // Median, not mean: a lump sum among the last three must not set the pace either.
        val latest = if (downs.size > RECENT_WINDOW) median(downs.takeLast(RECENT_WINDOW).map { it.first }) else typical
        return DebtPace(typical, if (gaps.size >= 2) Math.round(median(gaps.map { it.toDouble() })) else null, latest)
    }

    fun finishDate(balance: Double, payment: Double, gapDays: Long, today: LocalDate): LocalDate =
        today.plusDays(gapDays * ceil(balance / payment).toLong().coerceAtLeast(1))

    /** A round payment about twice the typical one, for "what if I paid more". */
    fun whatIfPayment(typical: Double): Double {
        val step = when { typical < 100 -> 10.0; typical < 1000 -> 50.0; else -> 100.0 }
        return ceil(typical * 2 / step) * step
    }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private val formats = listOf(DateTimeFormatter.ofPattern("dd.MM.yyyy"), DateTimeFormatter.ISO_LOCAL_DATE)
    private fun parseDate(s: String): LocalDate? = formats.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s.trim(), f) }.getOrNull() }
}
