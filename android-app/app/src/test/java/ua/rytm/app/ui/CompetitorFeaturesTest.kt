package ua.rytm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.FinanceInsights
import ua.rytm.app.ui.screens.finance.MerchantBrands
import java.time.LocalDate

class CompetitorFeaturesTest {
    @Test fun safeToSpendDividesFreeMoneyByDaysLeftIncludingToday() {
        // Oct 20 → 12 days left (20..31).
        assertEquals(100.0, FinanceInsights.safeToSpendPerDay(1500.0, 300.0, LocalDate.of(2026, 10, 20))!!, 0.001)
        assertEquals(500.0, FinanceInsights.safeToSpendPerDay(500.0, 0.0, LocalDate.of(2026, 10, 31))!!, 0.001)
        assertNull(FinanceInsights.safeToSpendPerDay(200.0, 300.0, LocalDate.of(2026, 10, 20)))
    }

    @Test fun merchantsAreRecognisedFromBankComments() {
        assertEquals("Коло", MerchantBrands.match("KOLO KIYEV UA")?.name)
        assertEquals("Сільпо", MerchantBrands.match("Сільпо, Київ")?.name)
        assertEquals("YouTube", MerchantBrands.match("Google YouTube London GB")?.name)
        assertEquals("АТБ", MerchantBrands.match("ATB-Market 123")?.name)
        assertNull(MerchantBrands.match("кава з друзями"))
        assertNull(MerchantBrands.match(null))
        // "коло" inside another word must not match.
        assertNull(MerchantBrands.match("околиця"))
    }

    @Test fun smartSearchTurnsWordsIntoFilters() {
        val today = LocalDate.of(2026, 10, 20)
        val q = ua.rytm.app.ui.screens.finance.SmartSearch.parse("Кафе у жовтні більше 200", today)
        assertEquals("кафе", q.text)
        assertEquals(java.time.YearMonth.of(2026, 10), q.month)
        assertEquals(200.0, q.minAmount!!, 0.0)
        val r = ua.rytm.app.ui.screens.finance.SmartSearch.parse("від 100 до 300", today)
        assertEquals(100.0, r.minAmount!!, 0.0); assertEquals(300.0, r.maxAmount!!, 0.0); assertEquals("", r.text)
        // A future month means last year's.
        assertEquals(java.time.YearMonth.of(2025, 12), ua.rytm.app.ui.screens.finance.SmartSearch.parse("грудень", today).month)
        assertEquals(today.minusDays(1), ua.rytm.app.ui.screens.finance.SmartSearch.parse("вчора", today).date)
        // Words that only look like months stay text.
        assertEquals("червоний market", ua.rytm.app.ui.screens.finance.SmartSearch.parse("червоний market", today).text)
        assertEquals(null, ua.rytm.app.ui.screens.finance.SmartSearch.parse("червоний market", today).month)
    }

    @Test fun smartSearchFiltersTheHistory() {
        fun tx(id: String, amount: Double, date: String, comment: String) = ua.rytm.app.ui.screens.finance.Transaction(
            id = id, type = ua.rytm.app.ui.screens.finance.TxType.EXPENSE, amount = amount, date = date, walletId = "w", category = "Кафе", comment = comment)
        val txs = listOf(tx("a", 150.0, "2026-10-03", "Aroma"), tx("b", 450.0, "2026-10-05", "Aroma"), tx("c", 450.0, "2026-09-05", "Aroma"))
        val out = FinanceInsights.filter(txs, ua.rytm.app.ui.screens.finance.TxTypeFilter.ALL, ua.rytm.app.ui.screens.finance.PeriodFilter.ALL, null,
            "aroma жовтень більше 200", LocalDate.of(2026, 10, 20), { "" }, emptyMap())
        assertEquals(listOf("b"), out.map { it.id })
    }

}
