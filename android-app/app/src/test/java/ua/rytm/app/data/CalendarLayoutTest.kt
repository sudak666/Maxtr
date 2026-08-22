package ua.rytm.app.data

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.monthCalendarCells
import ua.rytm.app.ui.screens.shifts.daysForShiftPattern

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

    @Test fun allQuickFillPatternsMatchPwaCycles() {
        assertEquals((1..10).toList(), daysForShiftPattern(10, "every"))
        assertEquals(listOf(1, 3, 5, 7, 9), daysForShiftPattern(10, "alt"))
        assertEquals(listOf(1, 2, 5, 6, 9, 10), daysForShiftPattern(10, "2_2"))
        assertEquals(listOf(1, 2, 3, 7, 8, 9), daysForShiftPattern(10, "3_3"))
    }
}
