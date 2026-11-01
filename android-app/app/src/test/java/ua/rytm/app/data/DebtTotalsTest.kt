package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.openTotals

class DebtTotalsTest {
    private fun debt(amount: Double, direction: String, currency: String = "UAH") =
        Debt(1, "d", "", currency, amount, "", emptyList(), direction)

    @Test fun totalsSplitByDirectionAndCurrency() {
        val debts = listOf(debt(100.0, "owe"), debt(50.0, "owe"), debt(20.0, "owe", "USD"), debt(30.0, "owed"), debt(0.0, "owed"))
        assertEquals(mapOf("UAH" to 150.0, "USD" to 20.0), debts.openTotals(false))
        assertEquals(mapOf("UAH" to 30.0), debts.openTotals(true))
    }
}
