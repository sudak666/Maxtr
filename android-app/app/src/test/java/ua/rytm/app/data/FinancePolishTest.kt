package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.icons.Person
import ua.rytm.app.ui.icons.Receipt
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Sell
import ua.rytm.app.ui.screens.finance.CashForecast
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet
import ua.rytm.app.ui.screens.finance.categoryIcon
import ua.rytm.app.ui.screens.finance.collapsedHistory
import ua.rytm.app.ui.screens.finance.isSpendable
import java.time.LocalDate

class FinancePolishTest {
    private fun tx(id: String, date: String) = Transaction(id = id, type = TxType.EXPENSE, amount = 1.0, date = date, walletId = "w", category = "Інше")
    private fun days(vararg counts: Int): List<Transaction> =
        counts.withIndex().flatMap { (day, n) -> (0 until n).map { tx("$day-$it", "2026-11-%02d".format(26 - day)) } }

    @Test
    fun `money owed to you stays out of the forecast but in the capital`() {
        val wallets = listOf(
            Wallet("card", "ПУМБ", 0, openingBalance = 460.0),
            Wallet("owed", "Мені винні", 0, kind = "receivable", openingBalance = 2000.0),
        )
        assertTrue(wallets[0].isSpendable)
        assertFalse(wallets[1].isSpendable)
        assertTrue(wallets[1].inCapital)
        val forecast = CashForecast.forProfile(wallets, emptyList(), emptyList(), LocalDate.parse("2026-11-26"), LocalDate.parse("2026-11-30")) { a, _ -> a }
        assertEquals(460.0, forecast.startBalance, 0.001)
    }

    @Test
    fun `collapsed history finishes the last day it shows`() {
        assertEquals(7, collapsedHistory(days(7, 3), 5, 10).size)
        assertEquals(5, collapsedHistory(days(2, 3, 4), 5, 10).size)
        assertEquals(3, collapsedHistory(days(3, 12), 5, 10).size)
        assertEquals(5, collapsedHistory(days(30), 5, 10).size)
        assertEquals(4, collapsedHistory(days(4), 5, 10).size)
    }

    @Test
    fun `an unknown category gets one neutral icon, not a random one`() {
        assertEquals(RytmIcons.Sell, categoryIcon("Робокод"))
        assertEquals(RytmIcons.Sell, categoryIcon("Ключі від гаража"))
        assertEquals(RytmIcons.Receipt, categoryIcon("Комісії"))
        assertEquals(RytmIcons.Person, categoryIcon("Син"))
    }

    @Test
    fun `monobank wallets are named by card type and last digits`() {
        val black = MonobankAccount("a", "account", "444111******5129", "UAH", type = "black")
        assertEquals("Mono Чорна ••5129", MonobankNames.walletName(black))
        assertEquals("Mono Біла USD ••4276", MonobankNames.walletName(MonobankAccount("b", "account", "444111******4276", "USD", type = "white")))
        assertEquals("Mono Картка ••0158", MonobankNames.walletName(MonobankAccount("c", "account", "537541******0158", "UAH", type = "new")))
        assertTrue(MonobankNames.isLegacyDefault("Monobank 444111******5129", black))
        assertFalse(MonobankNames.isLegacyDefault("Основна", black))
    }
}
