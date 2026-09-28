package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet
import ua.rytm.app.ui.screens.shifts.AutoFillSchedule
import ua.rytm.app.ui.screens.shifts.ShiftType
import ua.rytm.app.widget.WidgetSnapshot
import java.time.LocalDate

class WidgetSnapshotTest {
    private val today = LocalDate.of(2026, 9, 28)
    private val day = ShiftType("d", "Денна", "Д", "Д", 0xFF00FF00, 1500.0, 8.0, false)
    private val off = ShiftType("o", "Вихідний", "В", "В", 0, 0.0, 0.0, true)
    private val wallets = listOf(Wallet("uah", "Cash", 0, "UAH"), Wallet("eur", "Euro", 0, "EUR"))
    private val txs = listOf(
        Transaction("1", TxType.INCOME, 1000.0, "UAH", "2026-09-01", "uah", category = "Salary"),
        Transaction("2", TxType.EXPENSE, 10.0, "EUR", "2026-09-02", "eur", category = "Food"),
    )

    private fun snap(shifts: Map<String, List<String>>, masked: Boolean = false) =
        WidgetSnapshot.compute(today, wallets, txs, mapOf("EUR" to 50.0), listOf(day, off), shifts, AutoFillSchedule(), masked)

    @Test fun balanceIsConvertedToUah() = assertEquals(500.0, snap(emptyMap()).balanceUah, 0.001)

    @Test fun nextShiftSkipsPastAndDaysOff() {
        val s = snap(mapOf("2026-09-27" to listOf("d"), "2026-09-28" to listOf("o"), "2026-09-30" to listOf("d"), "2026-10-02" to listOf("d")))
        assertEquals(LocalDate.of(2026, 9, 30), s.nextShift!!.date)
        assertEquals("Денна", s.nextShift!!.name)
    }

    @Test fun todayCountsAsNext() = assertEquals(today, snap(mapOf("2026-09-28" to listOf("d"))).nextShift!!.date)

    @Test fun noUpcomingShift() = assertNull(snap(mapOf("2026-09-01" to listOf("d"))).nextShift)

    @Test fun forecastUsesCurrentMonth() = assertEquals(3000.0, snap(mapOf("2026-09-01" to listOf("d"), "2026-09-30" to listOf("d"))).monthForecastUah, 0.0)

    @Test fun shiftCodeFallsBackToFirstLetter() {
        val noCode = day.copy(code = "")
        val s = WidgetSnapshot.compute(today, wallets, txs, emptyMap(), listOf(noCode), mapOf("2026-09-29" to listOf("d")), AutoFillSchedule(), false)
        assertEquals("Д", s.nextShift!!.code)
    }

    @Test fun maskedFlagPassesThrough() = assertEquals(true, snap(emptyMap(), masked = true).masked)
}
