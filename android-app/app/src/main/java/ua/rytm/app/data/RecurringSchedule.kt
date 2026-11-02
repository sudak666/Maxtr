package ua.rytm.app.data

import java.time.LocalDate
import java.time.YearMonth

/**
 * The occurrence after [date]. Monthly entries keep their day of month
 * ([anchorDay], falling back to the date's own day) and clamp it to short
 * months: the 31st runs 31.01 -> 28.02 -> 31.03, never drifting to the 28th.
 * Same rule as js/shift-money.js's nextRecurringDate() - both clients must
 * produce the same dates, since the occurrence id is built from the date.
 */
internal fun nextRecurringDate(date: LocalDate, frequency: String, anchorDay: Int?): LocalDate = when (frequency) {
    "daily" -> date.plusDays(1)
    "weekly" -> date.plusWeeks(1)
    else -> {
        val month = YearMonth.from(date).plusMonths(1)
        month.atDay(minOf(anchorDay?.takeIf { it in 1..31 } ?: date.dayOfMonth, month.lengthOfMonth()))
    }
}

/**
 * A start date the user picks in the past moves to its first occurrence from
 * [today] on. Otherwise the app would add every missed one at once (four
 * "Продукти" back to July, 2026-11-02). Catch-up after the app was closed is
 * unaffected: it runs from a stored date, not through here.
 */
internal fun firstDueFrom(date: LocalDate, frequency: String, anchorDay: Int?, today: LocalDate): LocalDate {
    var d = date
    var guard = 0
    while (d.isBefore(today) && guard < 1000) { d = nextRecurringDate(d, frequency, anchorDay); guard++ }
    return d
}
