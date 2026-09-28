package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.AutoFillSchedule
import ua.rytm.app.ui.screens.shifts.EarningsForecast
import ua.rytm.app.ui.screens.shifts.GoalOutlook
import ua.rytm.app.ui.screens.shifts.ShiftType
import java.time.LocalDate
import java.time.YearMonth

class EarningsForecastTest {
    private val day = ShiftType("d", "Day", "Д", "Д", 0, 1500.0, 8.0, false)
    private val night = ShiftType("n", "Night", "Н", "Н", 0, 2000.0, 12.0, false)
    private val off = ShiftType("o", "Off", "В", "В", 0, 0.0, 0.0, true)
    private val types = listOf(day, night, off)
    private val sep = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 15)
    private val noSchedule = AutoFillSchedule()

    @Test fun splitsEarnedAndPlannedAroundToday() {
        val shifts = mapOf(
            "2026-09-01" to listOf("d"),
            "2026-09-15" to listOf("n"), // today counts as earned
            "2026-09-20" to listOf("d", "o"),
            "2026-10-01" to listOf("d"), // other month ignored
        )
        val f = EarningsForecast.forMonth(sep, today, shifts, types, noSchedule)
        assertEquals(3500.0, f.earned, 0.0)
        assertEquals(1500.0, f.planned, 0.0)
        assertEquals(1, f.plannedShifts) // day off doesn't count as a shift
        assertEquals(0.0, f.fromSchedule, 0.0)
        assertEquals(5000.0, f.total, 0.0)
    }

    @Test fun scheduleFillsOnlyEmptyFutureDays() {
        // 2/2 from Sep 1: on Sep 1-2, 5-6, 9-10, ... ; future empty "on" days
        // after the 15th: 17,18,21,22,25,26,29,30 = 8 — minus the 21st, taken.
        val schedule = AutoFillSchedule(enabled = true, typeId = "d", pattern = "2_2", anchorDate = "2026-09-01")
        val shifts = mapOf("2026-09-21" to listOf("n"))
        val f = EarningsForecast.forMonth(sep, today, shifts, types, schedule)
        assertEquals(7, f.scheduleShifts)
        assertEquals(7 * 1500.0, f.fromSchedule, 0.0)
        assertEquals(2000.0, f.planned, 0.0)
    }

    @Test fun disabledOrOffTypeScheduleAddsNothing() {
        val disabled = AutoFillSchedule(enabled = false, typeId = "d", pattern = "every", anchorDate = "2026-09-01")
        val offType = AutoFillSchedule(enabled = true, typeId = "o", pattern = "every", anchorDate = "2026-09-01")
        assertEquals(0.0, EarningsForecast.forMonth(sep, today, emptyMap(), types, disabled).fromSchedule, 0.0)
        assertEquals(0.0, EarningsForecast.forMonth(sep, today, emptyMap(), types, offType).fromSchedule, 0.0)
    }

    @Test fun anchorInTheFutureStartsThere() {
        val schedule = AutoFillSchedule(enabled = true, typeId = "d", pattern = "every", anchorDate = "2026-09-28")
        val f = EarningsForecast.forMonth(sep, today, emptyMap(), types, schedule)
        assertEquals(3, f.scheduleShifts) // 28, 29, 30
    }

    @Test fun nextMonthIsEntirelyAhead() {
        val schedule = AutoFillSchedule(enabled = true, typeId = "n", pattern = "alt", anchorDate = "2026-09-01")
        val f = EarningsForecast.forMonth(YearMonth.of(2026, 10), today, emptyMap(), types, schedule)
        assertEquals(0.0, f.earned, 0.0)
        assertEquals(16, f.scheduleShifts) // Oct 1 is day 30 from anchor: even -> on; 31 days -> 16
    }

    @Test fun typicalPayPrefersScheduleTypeThenMostUsed() {
        val schedule = AutoFillSchedule(enabled = true, typeId = "n")
        assertEquals(2000.0, EarningsForecast.typicalShiftPay(emptyMap(), types, schedule)!!, 0.0)
        val shifts = mapOf("a" to listOf("d"), "b" to listOf("d"), "c" to listOf("n"))
        assertEquals(1500.0, EarningsForecast.typicalShiftPay(shifts, types, noSchedule)!!, 0.0)
        assertNull(EarningsForecast.typicalShiftPay(emptyMap(), listOf(off), noSchedule))
    }

    @Test fun outlookRoundsShiftsUp() {
        assertEquals(GoalOutlook.Reached, EarningsForecast.outlook(20000.0, 20000.0, 1500.0))
        assertEquals(GoalOutlook.Short(3100.0, 3), EarningsForecast.outlook(16900.0, 20000.0, 1500.0))
        assertEquals(GoalOutlook.Short(100.0, null), EarningsForecast.outlook(19900.0, 20000.0, null))
        assertEquals(GoalOutlook.Reached, EarningsForecast.outlook(0.0, 0.0, 1500.0))
    }
}
