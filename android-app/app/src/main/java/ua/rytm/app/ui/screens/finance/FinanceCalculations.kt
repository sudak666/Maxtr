package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.YearMonth

internal object FinanceCalculations {
    fun monthlyTotal(
        transactions: List<Transaction>,
        type: TxType,
        month: YearMonth,
        toBaseCurrency: (Double, String) -> Double,
    ): Double = transactions
        .asSequence()
        .filter { it.type == type && it.date.startsWith(month.toString()) }
        .sumOf { toBaseCurrency(it.amount, it.currency) }

    fun filterTransactions(
        transactions: List<Transaction>,
        wallets: List<Wallet>,
        typeFilter: TxTypeFilter,
        periodFilter: PeriodFilter,
        categoryFilter: String?,
        search: String,
        today: LocalDate = LocalDate.now(),
    ): List<Transaction> {
        val monthPrefix = YearMonth.from(today).toString()
        val walletNames = wallets.associate { it.id to it.name.lowercase() }
        val query = search.trim().lowercase()
        return transactions.asSequence()
            .filter { transaction ->
                typeFilter == TxTypeFilter.ALL || transaction.type.name == typeFilter.name
            }
            .filter { transaction ->
                when (periodFilter) {
                    PeriodFilter.DAY -> transaction.date == today.toString()
                    PeriodFilter.MONTH -> transaction.date.startsWith(monthPrefix)
                    PeriodFilter.ALL -> true
                }
            }
            .filter { categoryFilter == null || it.category == categoryFilter }
            .filter { transaction ->
                query.isEmpty() || listOfNotNull(
                    transaction.comment,
                    transaction.category,
                    transaction.subcategory,
                    walletNames[transaction.walletId],
                    walletNames[transaction.targetWalletId],
                    transaction.currency,
                    transaction.targetCurrency,
                ).any { it.lowercase().contains(query) }
            }
            .sortedByDescending(Transaction::date)
            .toList()
    }
}
