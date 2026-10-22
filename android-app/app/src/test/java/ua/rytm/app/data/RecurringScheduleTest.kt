package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

// AUDIT_2026-10 P3-04/P4-01: same table as tests/shift-money.mjs — the PWA and
// Android must produce identical dates (occurrence ids are built from them).
class RecurringScheduleTest {
    private fun chain(start: String, n: Int, day: Int?): List<String> {
        var d = LocalDate.parse(start)
        return List(n) { d = nextRecurringDate(d, "monthly", day); d.toString() }
    }

    @Test fun monthlyKeepsItsDayAndClampsShortMonths() {
        assertEquals(listOf("2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"), chain("2026-01-31", 4, 31))
        assertEquals(listOf("2028-02-29", "2028-03-30"), chain("2028-01-30", 2, 30))
        assertEquals(listOf("2026-02-28", "2026-03-28"), chain("2026-01-29", 2, null))
        assertEquals("2027-01-31", nextRecurringDate(LocalDate.parse("2026-12-31"), "monthly", 31).toString())
    }

    @Test fun dailyAndWeekly() {
        assertEquals("2026-03-01", nextRecurringDate(LocalDate.parse("2026-02-28"), "daily", null).toString())
        assertEquals("2027-01-05", nextRecurringDate(LocalDate.parse("2026-12-29"), "weekly", null).toString())
    }
}
