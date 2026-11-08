package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.QuickEntryParser
import ua.rytm.app.ui.screens.finance.Wallet
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType

class QuickEntryParserTest {
    private val wallets = listOf(
        Wallet("cash", "Готівка", 0),
        Wallet("mono", "monobank", 0),
        Wallet("pumb", "ПУМБ 5536", 0),
    )

    @Test fun expenseWithWalletAlias() {
        val r = QuickEntryParser.parse("кава 65 моно", wallets)!!
        assertEquals(65.0, r.amount, 0.001)
        assertFalse(r.isIncome)
        assertEquals("mono", r.walletId)
        assertEquals("кава", r.comment)
    }

    @Test fun incomeByPlusAndWalletPrefix() {
        val r = QuickEntryParser.parse("+25 000 зарплата пумб", wallets)!!
        assertEquals(25000.0, r.amount, 0.001)
        assertTrue(r.isIncome)
        assertEquals("pumb", r.walletId)
        assertEquals("зарплата", r.comment)
    }

    @Test fun incomeByKeywordWithoutPlus() {
        assertTrue(QuickEntryParser.parse("аванс 8000", wallets)!!.isIncome)
    }

    @Test fun decimalCommaAndCurrencyWordDropped() {
        val r = QuickEntryParser.parse("таксі 120,50 грн", wallets)!!
        assertEquals(120.5, r.amount, 0.001)
        assertNull(r.walletId)
        assertEquals("таксі", r.comment)
    }

    @Test fun amountFirstAndCyrillicWallet() {
        val r = QuickEntryParser.parse("300 продукти готівка", wallets)!!
        assertEquals(300.0, r.amount, 0.001)
        assertEquals("cash", r.walletId)
        assertEquals("продукти", r.comment)
    }

    @Test fun noAmountIsNull() {
        assertNull(QuickEntryParser.parse("кава моно", wallets))
        assertNull(QuickEntryParser.parse("   ", wallets))
    }

    @Test fun digitsInsideWordAreNotAmount() {
        val r = QuickEntryParser.parse("АТБ5 оплата 45", wallets)!!
        assertEquals(45.0, r.amount, 0.001)
    }

    @Test fun shortWordsDoNotMatchWallets() {
        assertNull(QuickEntryParser.parse("на 50", wallets)!!.walletId)
    }

    @Test fun latinTypedFindsCyrillicWallet() {
        assertEquals("pumb", QuickEntryParser.parse("kava 65 pumb", wallets)!!.walletId)
    }

    private fun tx(comment: String?, category: String, type: TxType = TxType.EXPENSE) =
        Transaction(id = comment + category, type = type, amount = 10.0, date = "2026-11-01", walletId = "cash", category = category, comment = comment)

    @Test fun guessCategoryFromHistory() {
        val history = listOf(tx("Кава з собою", "Кафе"), tx("кава!", "Кафе"), tx("Кава", "Продукти"), tx("Таксі додому", "Транспорт"), tx("кава", "Зарплата", TxType.INCOME))
        val cats = listOf("Кафе", "Продукти", "Транспорт")
        assertEquals("Кафе", QuickEntryParser.guessCategory("кава", TxType.EXPENSE, history, cats))
        assertEquals("Транспорт", QuickEntryParser.guessCategory("Таксі", TxType.EXPENSE, history, cats))
        assertNull("no history", QuickEntryParser.guessCategory("бензин", TxType.EXPENSE, history, cats))
        assertNull("too short to trust", QuickEntryParser.guessCategory("ок", TxType.EXPENSE, history, cats))
        assertNull("deleted category is not offered", QuickEntryParser.guessCategory("таксі", TxType.EXPENSE, history, listOf("Кафе")))
        assertNull("blank", QuickEntryParser.guessCategory("", TxType.EXPENSE, history, cats))
    }
}
