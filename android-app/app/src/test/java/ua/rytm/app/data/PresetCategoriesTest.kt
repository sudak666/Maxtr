package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.HistoryFilter
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType

class PresetCategoriesTest {
    @Test fun eachTypeHasUniqueNamesAndOtherLast() {
        listOf(PresetCategories.expense, PresetCategories.income).forEach { list ->
            assertEquals(list.size, list.map { it.name }.toSet().size)
            assertEquals(BuiltinCategories.OTHER, list.last().name)
            list.forEach { p -> assertEquals(p.subcategories.size, p.subcategories.map { it.first }.toSet().size) }
        }
        assertTrue(PresetCategories.expense.size >= 15)
    }

    @Test fun englishNamesCoverEveryPreset() {
        (PresetCategories.expense + PresetCategories.income).forEach { p ->
            assertTrue(p.name, PresetCategories.englishName(p.name) != null)
            p.subcategories.forEach { (sub, _) -> assertTrue(sub, PresetCategories.englishName(sub) != null) }
        }
        assertEquals(null, PresetCategories.englishName("Моя категорія"))
    }

    @Test fun historyFilterMatchesWalletEitherSideAmountAndRange() {
        val t = Transaction("1", TxType.TRANSFER, 500.0, date = "2026-11-02", walletId = "a", targetWalletId = "b", category = "x")
        assertTrue(HistoryFilter(walletId = "b").matches(t))
        assertTrue(HistoryFilter(minAmount = 100.0, maxAmount = 500.0).matches(t))
        assertTrue(!HistoryFilter(maxAmount = 499.0).matches(t))
        assertTrue(HistoryFilter(from = "2026-11-02", to = "2026-11-02").matches(t))
        assertTrue(!HistoryFilter(from = "2026-11-03").matches(t))
        assertEquals(3, HistoryFilter("a", 1.0, null, "2026-01-01", null).activeCount)
    }
}
