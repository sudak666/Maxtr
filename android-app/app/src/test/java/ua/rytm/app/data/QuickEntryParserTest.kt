package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.QuickEntryParser
import ua.rytm.app.ui.screens.finance.Wallet

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
}
