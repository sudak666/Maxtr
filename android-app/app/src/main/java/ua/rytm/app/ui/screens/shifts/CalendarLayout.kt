package ua.rytm.app.ui.screens.shifts

import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.runtime.Immutable

@Immutable
data class CalendarCell(
    val date: LocalDate?,
    val isWeekend: Boolean = false,
    val isCurrentMonth: Boolean = true,
)

/** Monday-first calendar cells shared by UI and deterministic tests. */
fun monthCalendarCells(month: YearMonth): List<CalendarCell> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val gridStart = month.atDay(1).minusDays(leading.toLong())
    return List(42) { index ->
        val date = gridStart.plusDays(index.toLong())
        CalendarCell(date, date.dayOfWeek.value >= 6, YearMonth.from(date) == month)
    }
}

/**
 * Days of [month] that are working days of [pattern] counted from [anchor],
 * the first working day of the cycle. Filling a month and the autofill now
 * share that anchor: the month fill used to start every cycle on the 1st, so
 * a 2/2 filled by hand and the same 2/2 filled by autofill disagreed. Days
 * before the anchor continue the cycle backwards.
 */
fun daysForShiftPattern(month: java.time.YearMonth, pattern: String, anchor: java.time.LocalDate = month.atDay(1)): List<Int> {
    val (on, off) = SHIFT_PATTERN_CYCLES[pattern] ?: SHIFT_PATTERN_CYCLES.getValue("every")
    val period = on + off
    if (period <= 0) return emptyList()
    return (1..month.lengthOfMonth()).filter { day ->
        val diff = java.time.temporal.ChronoUnit.DAYS.between(anchor, month.atDay(day))
        Math.floorMod(diff, period.toLong()) < on
    }
}

fun toggleShiftSelection(selection: Set<String>, id: String): Set<String> =
    if (id in selection) selection - id else selection + id
