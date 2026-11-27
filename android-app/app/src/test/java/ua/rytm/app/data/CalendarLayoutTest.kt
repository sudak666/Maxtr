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
        val feb = java.time.YearMonth.of(2026, 2) // 28 days
        assertEquals((1..28).toList(), daysForShiftPattern(feb, "every"))
        assertEquals((1..28 step 2).toList(), daysForShiftPattern(feb, "alt"))
        assertEquals(listOf(1, 2, 5, 6, 9, 10), daysForShiftPattern(feb, "2_2").take(6))
        assertEquals(listOf(1, 2, 3, 7, 8, 9), daysForShiftPattern(feb, "3_3").take(6))
    }

    @org.junit.Test
    fun `month fill follows the cycle's first day, also backwards`() {
        val nov = java.time.YearMonth.of(2026, 11)
        // 2/2 whose first working day was 3 Nov: 1–2 Nov are the off days before it.
        assertEquals(listOf(3, 4, 7, 8, 11, 12), daysForShiftPattern(nov, "2_2", java.time.LocalDate.of(2026, 11, 3)).take(6))
        // Anchored months earlier, as the autofill does: 10 Jul 2026 + k·4 days.
        val fromJuly = daysForShiftPattern(nov, "2_2", java.time.LocalDate.of(2026, 7, 10))
        val julyStart = java.time.LocalDate.of(2026, 7, 10)
        fromJuly.forEach { d -> assertEquals(true, java.time.temporal.ChronoUnit.DAYS.between(julyStart, nov.atDay(d)) % 4 < 2) }
        // An anchor after the month still lines up (cycle continued backwards).
        // 1 Dec is a working day, so 27–28 Nov, 23–24 Nov … 3–4 Nov are too.
        assertEquals(listOf(3, 4, 7, 8), daysForShiftPattern(nov, "2_2", java.time.LocalDate.of(2026, 12, 1)).take(4))
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
