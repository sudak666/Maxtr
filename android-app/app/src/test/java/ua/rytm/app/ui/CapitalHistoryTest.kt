package ua.rytm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.CapitalHistory
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet
import java.time.LocalDate

class CapitalHistoryTest {
    private val card = Wallet("card", "Картка", 0, openingBalance = 100.0)
    private val usd = Wallet("usd", "Долари", 0, currency = "USD")
    private val loan = Wallet("loan", "Кредитка", 0).copy(inCapital = false)
    private fun tx(id: String, type: TxType, amount: Double, date: String, wallet: String, target: String? = null, targetAmount: Double? = null) =
        Transaction(id, type, amount, date = date, walletId = wallet, targetWalletId = target, targetAmount = targetAmount, category = "x")
    private val toUah: (Double, String) -> Double = { a, c -> if (c == "USD") a * 40 else a }

    @Test fun endOfDayCapitalCountsEarlierEntriesAndSkipsExcludedWallets() {
        val txs = listOf(
            tx("1", TxType.INCOME, 50.0, "2026-10-30", "card"),
            tx("2", TxType.EXPENSE, 30.0, "2026-11-01", "card"),
            tx("3", TxType.TRANSFER, 400.0, "2026-11-02", "card", target = "usd", targetAmount = 10.0),
            tx("4", TxType.EXPENSE, 999.0, "2026-11-02", "loan"),
        )
        val s = CapitalHistory.series(listOf(card, usd, loan), txs, LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-02"), toUah)
        assertEquals(listOf(120.0, 120.0), s.map { it.uah })
    }

    @Test fun percentOnlyWhenItMeansSomething() {
        assertNull(CapitalHistory.percentChange(-20.0, 4980.0))
        assertNull(CapitalHistory.percentChange(10.0, 4980.0))
        assertEquals(50.0, CapitalHistory.percentChange(1000.0, 1500.0)!!, 1e-9)
    }

    @Test fun sharesIgnoreDebtsAndGroup() {
        val s = CapitalHistory.shares(listOf("UAH" to 300.0, "USD" to 100.0, "UAH" to 100.0, "UAH" to -50.0))
        assertEquals(listOf("UAH" to 0.8, "USD" to 0.2), s.map { it.label to it.fraction })
    }
}
