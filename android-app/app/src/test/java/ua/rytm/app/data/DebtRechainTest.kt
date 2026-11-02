package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.debt.DebtEntry
import ua.rytm.app.ui.screens.debt.rechainedEntries

class DebtRechainTest {
    private fun e(id: Long, amount: String, balance: Double) = DebtEntry(id = id, amount = amount, balance = balance, date = "02.11.2026")

    @Test fun loanSavedWithoutAmountIsRecountedOnceItIsEntered() {
        val entries = listOf(e(1, "100", -100.0), e(2, "50", -150.0))
        assertEquals(listOf(900.0, 850.0), rechainedEntries(entries, 0.0, 1000.0)!!.map { it.balance })
    }

    @Test fun handTypedBalancesStay() {
        assertNull(rechainedEntries(listOf(e(1, "100", 500.0)), 0.0, 1000.0))
        assertNull(rechainedEntries(listOf(e(1, "за березень", -100.0)), 0.0, 1000.0))
    }

    @Test fun nothingToDoWithoutChange() {
        assertNull(rechainedEntries(emptyList(), 0.0, 1000.0))
        assertNull(rechainedEntries(listOf(e(1, "100", 900.0)), 1000.0, 1000.0))
    }
}
