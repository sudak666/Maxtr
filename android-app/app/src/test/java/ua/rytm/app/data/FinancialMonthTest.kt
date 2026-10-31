package ua.rytm.app.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinancialMonthTest {
    @After fun reset() { FinancialMonth.startDay = 1 }

    @Test fun calendarMonthByDefault() {
        val m = FinancialMonth.of(LocalDate.of(2026, 2, 28))
        assertEquals(YearMonth.of(2026, 2), m)
        assertEquals(LocalDate.of(2026, 2, 28), FinancialMonth.lastDay(m))
    }

    @Test fun beforeStartDayBelongsToPreviousMonth() {
        FinancialMonth.startDay = 10
        assertEquals(YearMonth.of(2025, 12), FinancialMonth.of(LocalDate.of(2026, 1, 9)))
        assertEquals(YearMonth.of(2026, 1), FinancialMonth.of(LocalDate.of(2026, 1, 10)))
    }

    @Test fun boundsAreInclusiveAndAcceptTimestamps() {
        FinancialMonth.startDay = 10
        val oct = YearMonth.of(2026, 10)
        assertTrue(FinancialMonth.contains(oct, "2026-10-10"))
        assertTrue(FinancialMonth.contains(oct, "2026-11-09T23:59"))
        assertFalse(FinancialMonth.contains(oct, "2026-10-09"))
        assertFalse(FinancialMonth.contains(oct, "2026-11-10"))
    }

    @Test fun dayNumberAndCutoff() {
        FinancialMonth.startDay = 25
        assertEquals(8, FinancialMonth.dayNumber(LocalDate.of(2026, 3, 4)))
        assertEquals(LocalDate.of(2026, 2, 24), FinancialMonth.cutoff(YearMonth.of(2026, 1), 40))
    }
}
