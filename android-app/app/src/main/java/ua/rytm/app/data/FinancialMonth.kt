package ua.rytm.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * The user's month: it starts on [startDay] (say, payday) and is labelled by the
 * calendar month it starts in. [startDay] is snapshot state, so derived totals
 * recompute when it changes. Capped at 28, so every month has that day.
 */
object FinancialMonth {
    const val MAX_START_DAY = 28
    var startDay by mutableIntStateOf(1)

    fun of(date: LocalDate): YearMonth =
        YearMonth.from(date).let { if (date.dayOfMonth < startDay) it.minusMonths(1) else it }

    fun firstDay(month: YearMonth): LocalDate = month.atDay(startDay)
    fun lastDay(month: YearMonth): LocalDate = month.plusMonths(1).atDay(startDay).minusDays(1)

    /** [date] is "yyyy-MM-dd…"; plain string order is date order. */
    fun contains(month: YearMonth, date: String): Boolean {
        val d = date.take(10)
        return d >= firstDay(month).toString() && d <= lastDay(month).toString()
    }

    /** 1 on the month's first day. */
    fun dayNumber(date: LocalDate): Int = ChronoUnit.DAYS.between(firstDay(of(date)), date).toInt() + 1

    /** The last date of [month] for a comparison over its first [days] days. */
    fun cutoff(month: YearMonth, days: Int): LocalDate =
        minOf(firstDay(month).plusDays(days - 1L), lastDay(month))
}
