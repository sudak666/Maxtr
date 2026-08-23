package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FinanceCalculationsTest {
    private val wallets = listOf(
        Wallet("cash", "Готівка", 0, "UAH"),
        Wallet("bank", "Bank", 0, "USD"),
    )
    private val transactions = listOf(
        tx("new", TxType.EXPENSE, "2026-08-23", "Продукти", "cash", comment = "Хліб"),
        tx("income", TxType.INCOME, "2026-08-22", "Зарплата", "bank", amount = 100.0, currency = "USD"),
        tx("old", TxType.EXPENSE, "2026-07-01", "Оренда", "cash"),
    )

    @Test fun filtersComposeAndKeepNewestFirst() {
        assertEquals(
            listOf("new"),
            FinanceCalculations.filterTransactions(
                transactions,
                wallets,
                TxTypeFilter.EXPENSE,
                PeriodFilter.MONTH,
                "Продукти",
                "готів",
                LocalDate.of(2026, 8, 23),
            ).map(Transaction::id),
        )
    }

    @Test fun searchIncludesCommentAndTargetWallet() {
        assertEquals("new", filtered("хліб").single().id)
        val transfer = tx("transfer", TxType.TRANSFER, "2026-08-23", "", "cash", targetWalletId = "bank")
        assertEquals("transfer", FinanceCalculations.filterTransactions(listOf(transfer), wallets, TxTypeFilter.ALL, PeriodFilter.ALL, null, "bank").single().id)
    }

    @Test fun monthlyTotalUsesInjectedConversion() {
        assertEquals(
            4000.0,
            FinanceCalculations.monthlyTotal(transactions, TxType.INCOME, YearMonth.of(2026, 8)) { amount, currency ->
                if (currency == "USD") amount * 40 else amount
            },
            0.001,
        )
    }

    private fun filtered(search: String) = FinanceCalculations.filterTransactions(
        transactions, wallets, TxTypeFilter.ALL, PeriodFilter.ALL, null, search, LocalDate.of(2026, 8, 23),
    )

    private fun tx(
        id: String,
        type: TxType,
        date: String,
        category: String,
        walletId: String,
        amount: Double = 10.0,
        currency: String = "UAH",
        comment: String? = null,
        targetWalletId: String? = null,
    ) = Transaction(id, type, amount, currency, date, walletId, targetWalletId = targetWalletId, category = category, comment = comment)
}
