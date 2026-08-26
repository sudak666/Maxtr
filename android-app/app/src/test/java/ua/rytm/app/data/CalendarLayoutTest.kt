package ua.rytm.app.data

import java.time.DayOfWeek
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.monthCalendarCells
import ua.rytm.app.ui.screens.shifts.daysForShiftPattern
import ua.rytm.app.ui.screens.shifts.toggleShiftSelection
import java.util.TimeZone

class CalendarLayoutTest {
    @Test fun freshProfileUsesOnlyExactPwaDefaultShiftTypes() {
        val defaults = defaultShiftTypeEntities()
        assertEquals(listOf("st_day", "st_night", "st_off"), defaults.map { it.id })
        assertEquals(listOf(8.0, 12.0, 0.0), defaults.map { it.hours })
        assertEquals(listOf(false, false, true), defaults.map { it.isOff })
    }
    @Test fun mondayFirstMonthStartsOnItsFirstDay() {
        val cells = monthCalendarCells(YearMonth.of(2026, 6))
        assertEquals(1, cells.first().date?.dayOfMonth)
        assertEquals(DayOfWeek.MONDAY, cells.first().date?.dayOfWeek)
        assertTrue(cells.first().isCurrentMonth)
    }

    @Test fun sundayStartShowsSixDaysFromPreviousMonth() {
        val cells = monthCalendarCells(YearMonth.of(2026, 2))
        assertEquals(6, cells.takeWhile { !it.isCurrentMonth }.size)
        assertEquals(26, cells.first().date?.dayOfMonth)
    }

    @Test fun leapFebruaryContains29Days() {
        val dated = monthCalendarCells(YearMonth.of(2024, 2)).filter { it.isCurrentMonth }
        assertEquals(29, dated.size)
        assertEquals(29, dated.last().date?.dayOfMonth)
    }

    @Test fun weekendStateComesFromRealDayOfWeek() {
        val cells = monthCalendarCells(YearMonth.of(2026, 8))
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

    @Test fun monthWithoutAssignmentsStillProducesEveryCalendarDay() {
        val cells = monthCalendarCells(YearMonth.of(2025, 4))
        assertEquals(42, cells.size)
        assertEquals(30, cells.count { it.isCurrentMonth })
    }

    @Test fun multiShiftSelectionAddsAndRemovesWithoutDroppingOtherTypes() {
        val selected = toggleShiftSelection(setOf("st_day"), "st_night")
        assertEquals(setOf("st_day", "st_night"), selected)
        assertEquals(setOf("st_night"), toggleShiftSelection(selected, "st_day"))
    }

    @Test fun calendarLayoutIsTimezoneAndDstIndependent() {
        val original = TimeZone.getDefault()
        try {
            val month = YearMonth.of(2026, 3)
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Kyiv"))
            val kyiv = monthCalendarCells(month)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(kyiv, monthCalendarCells(month))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
