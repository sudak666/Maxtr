package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Goal
import ua.rytm.app.ui.screens.finance.SalaryAutopilot
import java.time.LocalDate

class SalaryAutopilotTest {
    private val today = LocalDate.parse("2026-10-15")
    private val currencies = mapOf("main" to "UAH", "car" to "UAH", "trip" to "UAH", "usd" to "USD")
    private val balances = mapOf("car" to 10000.0, "trip" to 0.0, "usd" to 0.0)

    private fun suggest(income: Double, goals: List<Goal>) = SalaryAutopilot.suggest(
        income, "main", "UAH", goals, { currencies[it] }, { balances[it] ?: 0.0 }, today,
    )

    @Test fun recognisesPayWithinThirtyPercent() {
        assertTrue(SalaryAutopilot.matchesExpectedPay(16400.0, 16700.0))
        assertFalse(SalaryAutopilot.matchesExpectedPay(5000.0, 16700.0))
        assertFalse(SalaryAutopilot.matchesExpectedPay(16400.0, 0.0))
    }

    @Test fun splitsByMonthsLeft() {
        // car: 40 000 left over 4 months → 10 000; trip: 6 000 over 2 months → 3 000
        val s = suggest(30000.0, listOf(Goal("g1", "car", 50000.0, "2027-02-01"), Goal("g2", "trip", 6000.0, "2026-12-20")))
        assertEquals(listOf(10000.0, 3000.0), s.map { it.amount })
    }

    @Test fun cappedAtHalfOfIncome() {
        val s = suggest(10000.0, listOf(Goal("g1", "car", 50000.0, "2026-11-01")))
        assertEquals(5000.0, s.single().amount, 0.01)
    }

    @Test fun skipsReachedOtherCurrencyAndOwnWallet() {
        val goals = listOf(
            Goal("done", "car", 9000.0, "2027-01-01"),
            Goal("usd", "usd", 1000.0, "2027-01-01"),
            Goal("own", "main", 1000.0, "2027-01-01"),
        )
        assertTrue(suggest(30000.0, goals).isEmpty())
    }
}
