package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.debt.DebtEntry
import ua.rytm.app.ui.screens.debt.DebtPaceCalc

class DebtPaceTest {
    private fun e(balance: Double, date: String) = DebtEntry(0, "", balance, date)

    // The owner's real shape: one early lump, then small monthly payments.
    @Test fun earlyLumpSumDoesNotDominateTypicalPayment() {
        val entries = listOf(
            e(55_000.0, "10.01.2026"),            // 20 000 lump
            e(54_870.0, "10.02.2026"),
            e(54_750.0, "10.03.2026"),
            e(54_640.0, "10.04.2026"),
            e(54_520.0, "10.05.2026"),
            e(54_391.0, "10.06.2026"),
            e(54_287.0, "11.07.2026"),
            e(54_176.0, "10.08.2026"),
            e(54_021.0, "10.09.2026"),
        )
        val pace = DebtPaceCalc.compute(75_000.0, entries)!!
        // last 6 paydowns: 110,120,129,104,111,155 → median 115.5
        assertEquals(115.5, pace.typicalPayment, 0.001)
        assertEquals(31L, pace.typicalGapDays)
    }

    @Test fun balanceIncreasesAreNotPaydowns() {
        val pace = DebtPaceCalc.compute(1000.0, listOf(e(900.0, "x"), e(950.0, "y"), e(850.0, "z")))!!
        assertEquals(100.0, pace.typicalPayment, 0.001)
        assertNull("unparseable dates → no finish estimate", pace.typicalGapDays)
    }

    @Test fun noPaydownsNoPace() {
        assertNull(DebtPaceCalc.compute(1000.0, listOf(e(1000.0, "a"), e(1100.0, "b"))))
    }
}
