package ua.rytm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.FinanceInsights
import ua.rytm.app.ui.screens.finance.PeriodFilter
import ua.rytm.app.ui.screens.finance.Recurring
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.TxTypeFilter
import java.time.LocalDate
import java.time.YearMonth

class FinanceInsightsTest {
    private val uah: (Double, String) -> Double = { a, c -> if (c == "USD") a * 40 else a }
    private fun tx(id: String, type: TxType, amount: Double, date: String, by: String? = null, cur: String = "UAH", cat: String = "Кафе", comment: String? = null) =
        Transaction(id = id, type = type, amount = amount, currency = cur, date = date, walletId = "w", category = cat, comment = comment, createdBy = by, createdByName = by)

    @Test fun monthTotalCountsOnlyThatMonthAndConverts() {
        val txs = listOf(
            tx("1", TxType.EXPENSE, 100.0, "2026-10-05"),
            tx("2", TxType.EXPENSE, 1.0, "2026-10-06", cur = "USD"),
            tx("3", TxType.EXPENSE, 999.0, "2026-09-30"),
            tx("4", TxType.INCOME, 50.0, "2026-10-01"),
        )
        assertEquals(140.0, FinanceInsights.monthTotalUah(txs, TxType.EXPENSE, YearMonth.of(2026, 10), uah), 0.001)
    }

    @Test fun contributionsNeedTwoPeopleAndPutMeFirst() {
        val month = YearMonth.of(2026, 10)
        assertTrue(FinanceInsights.contributions(listOf(tx("1", TxType.EXPENSE, 10.0, "2026-10-01", by = "a")), month, "a", uah).isEmpty())
        val c = FinanceInsights.contributions(
            listOf(
                tx("1", TxType.EXPENSE, 10.0, "2026-10-01", by = "b"),
                tx("2", TxType.EXPENSE, 30.0, "2026-10-02", by = "a"),
                tx("3", TxType.TRANSFER, 500.0, "2026-10-02", by = "a"),
                tx("4", TxType.EXPENSE, 7.0, "2026-10-02", by = null),
            ), month, "a", uah,
        )
        assertEquals(listOf(true, false), c.map { it.isMe })
        assertEquals(30.0, c[0].expense, 0.001)
    }

    @Test fun outlookCountsOnlyRemainingOccurrences() {
        val r = Recurring("r", TxType.EXPENSE, 100.0, "Кафе", "w", "weekly", "2026-10-01", active = true, comment = "")
        val o = FinanceInsights.monthOutlook(listOf(r), { "UAH" }, LocalDate.of(2026, 10, 20), 0.0, uah)
        // 22, 29 Oct remain (1, 8, 15 are past).
        assertEquals(200.0, o.recurringOut, 0.001)
    }

    @Test fun filterAppliesSearchByTagAndSortsNewestFirst() {
        val txs = listOf(
            tx("1", TxType.EXPENSE, 1.0, "2026-10-01", comment = "капучино").copy(tags = listOf("t")),
            tx("2", TxType.EXPENSE, 1.0, "2026-10-03"),
            tx("3", TxType.INCOME, 1.0, "2026-10-02"),
        )
        val all = FinanceInsights.filter(txs, TxTypeFilter.EXPENSE, PeriodFilter.ALL, null, "", LocalDate.of(2026, 10, 20), { "" }, emptyMap())
        assertEquals(listOf("2", "1"), all.map { it.id })
        val byTag = FinanceInsights.filter(txs, TxTypeFilter.ALL, PeriodFilter.ALL, null, "#кав", LocalDate.of(2026, 10, 20), { "" }, mapOf("t" to "кава"))
        assertEquals(listOf("1"), byTag.map { it.id })
    }

    @Test fun frequentCategoriesIgnoreTransfersAndUnknown() {
        val txs = listOf(tx("1", TxType.EXPENSE, 1.0, "2026-10-01", cat = "A"), tx("2", TxType.EXPENSE, 1.0, "2026-10-02", cat = "A"), tx("3", TxType.EXPENSE, 1.0, "2026-10-02", cat = "Z"))
        assertEquals(listOf("A"), FinanceInsights.frequentCategories(txs, TxType.EXPENSE, setOf("A"), LocalDate.of(2026, 9, 1)))
        assertTrue(FinanceInsights.frequentCategories(txs, TxType.TRANSFER, setOf("A"), LocalDate.of(2026, 9, 1)).isEmpty())
    }
}
