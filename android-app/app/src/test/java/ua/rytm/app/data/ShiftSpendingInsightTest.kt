package ua.rytm.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.ShiftSpending
import ua.rytm.app.ui.screens.shifts.ShiftType

class ShiftSpendingInsightTest {
    private val today = LocalDate.of(2026, 10, 6)
    private val work = ShiftType("d", "Day", "Д", "Д", 0xFF0000FF, 1000.0, 12.0, false)
    private val off = ShiftType("o", "Off", "В", "В", 0xFF00FF00, 0.0, 0.0, true)

    /** Alternating work/off days over the whole window, fixed spend per kind. */
    private fun data(workSpend: Double, offSpend: Double): Pair<Map<String, List<String>>, Map<String, Double>> {
        val shifts = mutableMapOf<String, List<String>>()
        val spend = mutableMapOf<String, Double>()
        var d = today.minusDays(ShiftSpending.WINDOW_DAYS)
        var i = 0
        while (d.isBefore(today)) {
            val isWork = i % 2 == 0
            shifts[d.toString()] = listOf(if (isWork) "d" else "o")
            spend[d.toString()] = if (isWork) workSpend else offSpend
            d = d.plusDays(1); i++
        }
        return shifts to spend
    }

    @Test fun reportsHigherSpendingOnShiftDays() {
        val (shifts, spend) = data(480.0, 300.0)
        val insight = ShiftSpending.compute(today, spend, shifts, listOf(work, off))!!
        assertEquals(480.0, insight.avgOnShiftDays, 0.001)
        assertEquals(300.0, insight.avgOnOffDays, 0.001)
        assertEquals(60, insight.diffPercent)
    }

    @Test fun weakDifferenceIsNotShown() {
        val (shifts, spend) = data(310.0, 300.0)
        assertNull(ShiftSpending.compute(today, spend, shifts, listOf(work, off)))
    }

    @Test fun daysBeforeTheFirstShiftAreIgnored() {
        // Only 6 days of shift history → too little to say anything.
        val shifts = (1..6L).associate { today.minusDays(it).toString() to listOf("d") }
        val spend = (1..90L).associate { today.minusDays(it).toString() to 100.0 }
        assertNull(ShiftSpending.compute(today, spend, shifts, listOf(work, off)))
    }
}
