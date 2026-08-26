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

fun daysForShiftPattern(daysInMonth: Int, pattern: String): List<Int> {
    val (on, off) = SHIFT_PATTERN_CYCLES[pattern] ?: SHIFT_PATTERN_CYCLES.getValue("every")
    val period = on + off
    return (1..daysInMonth).filter { day -> period > 0 && ((day - 1) % period) < on }
}

fun toggleShiftSelection(selection: Set<String>, id: String): Set<String> =
    if (id in selection) selection - id else selection + id
