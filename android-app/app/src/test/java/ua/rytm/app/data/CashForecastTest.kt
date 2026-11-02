package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.CashForecast
import ua.rytm.app.ui.screens.finance.Recurring
import ua.rytm.app.ui.screens.finance.SpendingHabit
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import java.time.LocalDate

class CashForecastTest {
    private val today = LocalDate.parse("2026-11-10")
    private val end = LocalDate.parse("2026-11-30")
    private val uah = { amount: Double, _: String -> amount }

    private fun rent(amount: Double, date: String, type: TxType = TxType.EXPENSE, active: Boolean = true) =
        Recurring("r", type, amount, "Оренда", "w", "monthly", date, active, "")

    private fun forecast(balance: Double, recurring: List<Recurring>, daily: Double?) =
        CashForecast.compute(balance, recurring, { "UAH" }, today, end, daily?.let { SpendingHabit(it, 60, 6) }, uah)

    private fun spent(id: String, date: String, amount: Double) =
        Transaction(id = id, type = TxType.EXPENSE, amount = amount, date = date, walletId = "w", category = "c")

    @Test fun enoughMoneyHasNoShortfall() {
        val f = forecast(10_000.0, listOf(rent(3_000.0, "2026-11-18")), 100.0)
        assertNull(f.shortfallDate)
        assertEquals(0.0, f.shortfall, 0.01)
        assertEquals(5_000.0, f.endBalance, 0.01)
        assertEquals(1, f.events.size)
        assertEquals(6_200.0, f.events[0].balanceAfter, 0.01)
    }

    @Test fun firstNegativeDayAndDeepestGap() {
        val f = forecast(1_000.0, listOf(rent(3_000.0, "2026-11-18")), 100.0)
        assertEquals(LocalDate.parse("2026-11-18"), f.shortfallDate)
        assertEquals(4_000.0, f.shortfall, 0.01)
    }

    // A gap before income still counts, even though the month ends in plus.
    @Test fun gapBeforeIncomeIsReported() {
        val f = forecast(1_000.0, listOf(rent(3_000.0, "2026-11-12"), rent(9_000.0, "2026-11-20", TxType.INCOME)), null)
        assertEquals(LocalDate.parse("2026-11-12"), f.shortfallDate)
        assertEquals(2_000.0, f.shortfall, 0.01)
        assertEquals(7_000.0, f.endBalance, 0.01)
    }

    @Test fun todayIsPlannedButNotChargedDailySpend() {
        val f = forecast(1_000.0, listOf(rent(100.0, "2026-11-10")), 50.0)
        assertEquals(900.0, f.events[0].balanceAfter, 0.01)
    }

    @Test fun skipsInactivePastAndBeyondEnd() {
        val f = forecast(1_000.0, listOf(rent(1.0, "2026-11-12", active = false), rent(1.0, "2026-12-01")), null)
        assertEquals(0, f.events.size)
    }

    @Test fun shortHistoryGivesNoDailySpend() {
        assertNull(CashForecast.dailySpend(emptyList(), today, uah))
        assertNull(CashForecast.dailySpend(listOf(spent("a", "2026-11-01", 100.0)), today, uah))
    }

    @Test fun dropsBiggestDaysAndRecurringOccurrences() {
        val history = (1..20).map { spent("t$it", today.minusDays(it.toLong()).toString(), 100.0) } +
            spent("laptop", today.minusDays(3).toString(), 50_000.0) +
            spent("rec_r_2026-11-05", "2026-11-05", 7_000.0)
        assertEquals(100.0, CashForecast.dailySpend(history, today, uah)!!, 0.01)
    }

    // Rent paid by hand last month is a planned payment, not a habit.
    @Test fun skipsExpensesThatRepeatAPlannedPayment() {
        // 20 days drop the 2 biggest, so a third rent day would still count without the rule.
        val history = (1..20).map { spent("t$it", today.minusDays(it.toLong()).toString(), 100.0) } +
            listOf(3L, 9L, 15L).map { Transaction(id = "m$it", type = TxType.EXPENSE, amount = 10_500.0, date = today.minusDays(it).toString(), walletId = "w", category = "Оренда") }
        assertEquals(100.0, CashForecast.dailySpend(history, today, uah, listOf(rent(11_000.0, "2026-11-20")))!!, 0.01)
    }

    @Test fun unplannedSpendCoversDaysAfterToday() {
        assertEquals(2_000.0, forecast(0.0, emptyList(), 100.0).unplannedSpend, 0.01)
    }

    @Test fun eventLabelIsCategoryWithoutTheRecurringMark() {
        assertEquals("Оренда", CashForecast.eventLabel(rent(1.0, "2026-11-20").copy(comment = "повторювана")))
        assertEquals("Оренда · квартира", CashForecast.eventLabel(rent(1.0, "2026-11-20").copy(comment = "квартира · повторювана")))
    }

    // The sheet shows these lines as a sum; it must add up exactly.
    @Test fun sheetSumAddsUp() {
        val salary = rent(30_000.0, "2026-11-19", TxType.INCOME)
        val f = forecast(982.68, listOf(rent(11_000.0, "2026-11-20"), salary), 1_073.45)
        assertEquals(30_000.0, f.plannedIn, 0.001)
        assertEquals(11_000.0, f.plannedOut, 0.001)
        assertEquals(20, f.unplannedDays)
        assertEquals(f.startBalance + f.plannedIn - f.plannedOut - f.unplannedSpend, f.endBalance, 0.001)
    }

    @Test fun habitNamesTopCategoriesOfKeptDays() {
        val history = (1..20).map { Transaction(id = "t$it", type = TxType.EXPENSE, amount = if (it % 2 == 0) 300.0 else 100.0, date = today.minusDays(it.toLong()).toString(), walletId = "w", category = if (it % 2 == 0) "Кафе" else "Продукти") } +
            Transaction(id = "tv", type = TxType.EXPENSE, amount = 20_000.0, date = today.minusDays(4).toString(), walletId = "w", category = "Техніка")
        val h = CashForecast.habit(history, today, uah)!!
        assertEquals(20, h.windowDays)
        assertEquals(2, h.droppedDays)
        assertEquals("Кафе", h.topCategories.first().first)
        assertTrue(h.topCategories.none { it.first == "Техніка" })
        assertEquals(h.perDay, kotlin.math.round(h.perDay * 100) / 100, 0.0)
    }

    @Test fun quietDaysCountAsZero() {
        val history = listOf(spent("a", today.minusDays(20).toString(), 100.0), spent("b", today.minusDays(1).toString(), 100.0))
        // 20 days, the 2 biggest dropped: 18 quiet days remain.
        assertEquals(0.0, CashForecast.dailySpend(history, today, uah)!!, 0.01)
    }
}
