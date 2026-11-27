package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.MonthSummary
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import java.time.YearMonth

class MonthSummaryTest {
    private fun tx(id: String, type: TxType, amount: Double, date: String, category: String = "Продукти", currency: String = "UAH") =
        Transaction(id = id, type = type, amount = amount, currency = currency, date = date, walletId = "w", category = category)

    private val rates = mapOf("UAH" to 1.0, "USD" to 41.0)
    private val toUah: (Transaction) -> Double = { it.amount * (rates[it.currency] ?: 1.0) }

    @Test fun totalsTopCategoriesAndChange() {
        val txs = listOf(
            tx("1", TxType.EXPENSE, 1000.0, "2026-09-03"),
            tx("2", TxType.EXPENSE, 500.0, "2026-09-10", "Транспорт"),
            tx("3", TxType.EXPENSE, 10.0, "2026-09-12", "Кава", "USD"),
            tx("4", TxType.INCOME, 20000.0, "2026-09-15", "Зарплата"),
            tx("5", TxType.TRANSFER, 3000.0, "2026-09-16", "Переказ"),
            tx("6", TxType.EXPENSE, 1000.0, "2026-08-20"),
            tx("7", TxType.EXPENSE, 1000.0, "2026-10-01"),
        )
        val s = MonthSummary.compute(txs, YearMonth.of(2026, 9), toUah = toUah)
        assertEquals(20000.0, s.income, 0.01)
        assertEquals(1910.0, s.expense, 0.01)
        assertEquals(18090.0, s.net, 0.01)
        assertEquals(91, s.expenseChangePercent)
        assertEquals(listOf("Продукти", "Транспорт", "Кава"), s.topCategories.map { it.category })
        assertEquals(1000.0, s.topCategories[0].previousAmount, 0.01)
        assertEquals("1", s.biggestExpense!!.id)
        assertEquals(4, s.transactionCount)
    }

    // The story cards' own facts (outside review 2026-11-27: they used to retell the sheet).
    @Test fun storyFacts() {
        FinancialMonth.startDay = 1
        val txs = listOf(
            tx("1", TxType.EXPENSE, 100.0, "2026-09-01"), // Tue
            tx("2", TxType.EXPENSE, 300.0, "2026-09-04"), // Fri
            tx("3", TxType.EXPENSE, 200.0, "2026-09-04"),
            tx("4", TxType.EXPENSE, 400.0, "2026-09-10"), // Thu
        )
        val s = MonthSummary.compute(txs, YearMonth.of(2026, 9), upToDay = 10, toUah = toUah)
        assertEquals(10, s.days)
        assertEquals(7, s.noSpendDays)
        assertEquals(5, s.longestNoSpendRun) // 5–9 Sep
        assertEquals(java.time.DayOfWeek.FRIDAY, s.priciestWeekday)
        assertEquals(500.0, s.priciestWeekdayAverage, 0.01)
        assertEquals(4, s.expenseCount)
        assertEquals(250.0, s.averageExpense, 0.01)
    }

    @Test fun emptyMonth() {
        val s = MonthSummary.compute(emptyList(), YearMonth.of(2026, 9), toUah = toUah)
        assertTrue(s.isEmpty)
        assertNull(s.expenseChangePercent)
        assertNull(s.biggestExpense)
    }

    @Test fun monthInProgressComparesSameDaysOfPreviousMonth() {
        val txs = listOf(
            tx("1", TxType.EXPENSE, 100.0, "2026-09-05"),
            tx("2", TxType.EXPENSE, 900.0, "2026-09-25"),
            tx("3", TxType.EXPENSE, 100.0, "2026-10-04"),
        )
        val s = MonthSummary.compute(txs, YearMonth.of(2026, 10), upToDay = 21, toUah = toUah)
        assertEquals(100.0, s.previousExpense, 0.01)
        assertEquals(0, s.expenseChangePercent)
    }
}
