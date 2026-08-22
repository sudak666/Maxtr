package ua.rytm.app.data

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.monthCalendarCells

class CalendarLayoutTest {
    @Test fun mondayFirstMonthHasNoLeadingCells() {
        val cells = monthCalendarCells(YearMonth.of(2026, 6))
        assertEquals(1, cells.first().date?.dayOfMonth)
        assertEquals(DayOfWeek.MONDAY, cells.first().date?.dayOfWeek)
    }

    @Test fun sundayStartHasSixLeadingCells() {
        val cells = monthCalendarCells(YearMonth.of(2026, 2))
        assertEquals(6, cells.takeWhile { it.date == null }.size)
    }

    @Test fun leapFebruaryContains29Days() {
        val dated = monthCalendarCells(YearMonth.of(2024, 2)).filter { it.date != null }
        assertEquals(29, dated.size)
        assertEquals(29, dated.last().date?.dayOfMonth)
    }

    @Test fun weekendStateComesFromRealDayOfWeek() {
        val cells = monthCalendarCells(YearMonth.of(2026, 8)).filter { it.date != null }
        assertTrue(cells.first { it.date?.dayOfWeek == DayOfWeek.SATURDAY }.isWeekend)
        assertTrue(cells.first { it.date?.dayOfWeek == DayOfWeek.SUNDAY }.isWeekend)
        assertFalse(cells.first { it.date?.dayOfWeek == DayOfWeek.MONDAY }.isWeekend)
    }
}
