package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Contribution
import ua.rytm.app.ui.screens.finance.Settlement
import ua.rytm.app.ui.screens.finance.settleEqually

class SettleEquallyTest {
    private fun c(name: String, expense: Double) = Contribution(name, false, 0.0, expense)

    @Test fun twoPeople() {
        assertEquals(listOf(Settlement("Оля", "Макс", 1500.0)), settleEqually(listOf(c("Макс", 5000.0), c("Оля", 2000.0))))
    }

    @Test fun threePeopleTotalsBalance() {
        val s = settleEqually(listOf(c("A", 900.0), c("B", 300.0), c("C", 0.0)))
        // share 400: B owes 100, C owes 400, A is owed 500
        assertEquals(500.0, s.filter { it.to == "A" }.sumOf { it.amount }, 0.01)
        assertTrue(s.size <= 2)
    }

    @Test fun alreadyEqualOrSingle() {
        assertTrue(settleEqually(listOf(c("A", 100.0), c("B", 100.4))).isEmpty())
        assertTrue(settleEqually(listOf(c("A", 100.0))).isEmpty())
    }
}
