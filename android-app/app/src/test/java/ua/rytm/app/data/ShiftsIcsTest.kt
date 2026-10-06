package ua.rytm.app.data

import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.ShiftType
import ua.rytm.app.ui.screens.shifts.ShiftsIcs

class ShiftsIcsTest {
    private val day = ShiftType("d", "Денна, зміна", "Д", "Д", 0xFF0000FF, 1000.0, 12.0, false)
    private val off = ShiftType("o", "Вихідний", "В", "В", 0xFF00FF00, 0.0, 0.0, true)

    @Test fun oneAllDayEventPerWorkingShiftInTheMonth() {
        val ics = ShiftsIcs.build(
            YearMonth.of(2026, 10),
            mapOf("2026-10-05" to listOf("d"), "2026-10-06" to listOf("o"), "2026-11-01" to listOf("d")),
            listOf(day, off),
            Instant.parse("2026-10-06T12:00:00Z"),
        )
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261005\r\nDTEND;VALUE=DATE:20261006"))
        assertTrue("comma escaped", ics.contains("SUMMARY:Денна\\, зміна · 12 год"))
        assertTrue(ics.contains("UID:rytm-20261005-d@rytm.app"))
        assertFalse(ics.contains("Вихідний"))
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n") && ics.endsWith("END:VCALENDAR\r\n"))
    }
}
