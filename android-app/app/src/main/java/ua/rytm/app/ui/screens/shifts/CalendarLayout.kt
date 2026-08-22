package ua.rytm.app.ui.screens.shifts

import java.time.LocalDate
import java.time.YearMonth

data class CalendarCell(val date: LocalDate?, val isWeekend: Boolean = false)

/** Monday-first calendar cells shared by UI and deterministic tests. */
fun monthCalendarCells(month: YearMonth): List<CalendarCell> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    return List(leading) { CalendarCell(null) } +
        (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            CalendarCell(date, date.dayOfWeek.value >= 6)
        }
}

fun daysForShiftPattern(daysInMonth: Int, pattern: String): List<Int> {
    val (on, off) = SHIFT_PATTERN_CYCLES[pattern] ?: SHIFT_PATTERN_CYCLES.getValue("every")
    val period = on + off
    return (1..daysInMonth).filter { day -> period > 0 && ((day - 1) % period) < on }
}
