package ua.rytm.app.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet
import java.time.LocalDate

class BankQuickSaveTest {
    private val today = LocalDate.of(2026, 10, 20)
    private fun tx(cat: String, type: TxType = TxType.EXPENSE, date: String = "2026-10-10", comment: String? = null) =
        Transaction(id = cat + date + Math.random(), type = type, amount = 1.0, date = date, walletId = "w", category = cat, comment = comment)

    @Test fun quickCategoriesPutRuleThenMerchantHistoryFirst() {
        val cats = listOf("Продукти", "Здоров'я", "Кафе", "Інше")
        val txs = listOf(tx("Продукти"), tx("Продукти"), tx("Кафе"), tx("Здоров'я", comment = "Apteka AGP 12 KIYEV UA"))
        // Same chain, another branch → its past category leads.
        assertEquals(listOf("Здоров'я", "Продукти", "Кафе"), BankQuickSaveReceiver.quickCategories(txs, cats, false, today, merchant = "Apteka AGP 56 KIYEV UA"))
        // An auto-rule beats history; unknown rule categories are dropped.
        val rules = listOf("apteka" to "Кафе", "fora" to "Видалена")
        assertEquals(listOf("Кафе", "Здоров'я", "Продукти"), BankQuickSaveReceiver.quickCategories(txs, cats, false, today, "Apteka AGP 56", rules))
        assertEquals(listOf("Продукти", "Кафе", "Здоров'я"), BankQuickSaveReceiver.quickCategories(txs, cats, false, today, "Fora KIYEV UA", rules))
    }

    @Test fun merchantKeyIsFirstWord() {
        assertEquals("apteka", BankQuickSaveReceiver.merchantKey("Apteka AGP 56 KIYEV UA"))
        assertNull(BankQuickSaveReceiver.merchantKey("UA"))
        assertNull(BankQuickSaveReceiver.merchantKey("12345 shop"))
        assertNull(BankQuickSaveReceiver.merchantKey(null))
    }

    @Test fun quickCategoriesPreferFrequentThenTopUp() {
        val txs = listOf(tx("Кафе"), tx("Кафе"), tx("Транспорт"), tx("Старе", date = "2026-01-01"))
        val cats = listOf("Продукти", "Кафе", "Транспорт", "Старе", "Інше")
        assertEquals(listOf("Кафе", "Транспорт", "Продукти"), BankQuickSaveReceiver.quickCategories(txs, cats, isIncome = false, today = today))
    }

    @Test fun quickCategoriesUseTheRightTypeAndCapAtThree() {
        val txs = listOf(tx("Зарплата", TxType.INCOME))
        assertEquals(listOf("Зарплата", "Премія"), BankQuickSaveReceiver.quickCategories(txs, listOf("Зарплата", "Премія"), isIncome = true, today = today))
        assertEquals(3, BankQuickSaveReceiver.quickCategories(emptyList(), listOf("a", "b", "c", "d"), false, today).size)
    }

    @Test fun walletPickPrefersCardDigitsThenBankThenCurrency() {
        val wallets = listOf(
            Wallet("cash", "Готівка", 0, "UAH"),
            Wallet("usd", "Долари", 0, "USD"),
            Wallet("mono", "monobank", 0, "UAH"),
            Wallet("pumb", "ПУМБ 5536", 0, "UAH"),
        )
        assertEquals("pumb", WalletMatcher.pick(wallets, "*5536", "com.fuib.android.spot.online", "UAH")?.id)
        assertEquals("mono", WalletMatcher.pick(wallets, null, "com.ftband.mono", "UAH")?.id)
        assertEquals("usd", WalletMatcher.pick(wallets, null, null, "USD")?.id)
        assertNull(WalletMatcher.pick(emptyList(), null, null, "UAH"))
    }
}
