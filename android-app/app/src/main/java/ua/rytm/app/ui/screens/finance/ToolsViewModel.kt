package ua.rytm.app.ui.screens.finance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import java.time.YearMonth
import androidx.compose.runtime.Immutable

enum class AnalyticsPeriod { MONTH, PREV, M3, ALL }

@Immutable
data class MonthTotal(val yearMonth: YearMonth, val income: Double, val expense: Double)

// Mirrors js/finance.js/analytics-csv.js's Tools bottom sheet
// (analyticsPredicates()/renderCatList()/computeWalletBalances()) — the
// donut/6-month chart/FX-widget/converter all read off the same
// already-synced FinanceRepository flows already used by the Finance tab,
// plus the currencyRates flow (step 36 supplement) for real FX math instead
// of a hardcoded sample rate.
class ToolsViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { ToolsViewModel(repository) }
        }
    }

    private var transactions by mutableStateOf<List<Transaction>>(emptyList())
    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set
    var currencyRates by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    var period by mutableStateOf(AnalyticsPeriod.MONTH)
        private set

    init {
        repository.transactions.onEach { transactions = it }.launchIn(viewModelScope)
        repository.wallets.onEach { wallets = it }.launchIn(viewModelScope)
        repository.currencyRates.onEach { currencyRates = it }.launchIn(viewModelScope)
    }

    fun onPeriodChange(value: AnalyticsPeriod) { period = value }

    // Mirrors analyticsPredicates(): a "yyyy-MM" date prefix (or `>=` window
    // for 3m/all) selecting which transactions count for the current period.
    private fun periodTransactions(): List<Transaction> {
        val now = YearMonth.now()
        return when (period) {
            AnalyticsPeriod.MONTH -> {
                val p = now.toString()
                transactions.filter { it.date.startsWith(p) }
            }
            AnalyticsPeriod.PREV -> {
                val p = now.minusMonths(1).toString()
                transactions.filter { it.date.startsWith(p) }
            }
            AnalyticsPeriod.M3 -> {
                val from = now.minusMonths(2).atDay(1).toString()
                transactions.filter { it.date >= from }
            }
            AnalyticsPeriod.ALL -> transactions
        }
    }

    private fun previousPeriodTransactions(): List<Transaction>? {
        val now = YearMonth.now()
        return when (period) {
            AnalyticsPeriod.MONTH -> transactions.filter { it.date.startsWith(now.minusMonths(1).toString()) }
            AnalyticsPeriod.PREV -> transactions.filter { it.date.startsWith(now.minusMonths(2).toString()) }
            AnalyticsPeriod.M3 -> {
                val from = now.minusMonths(5).atDay(1).toString()
                val until = now.minusMonths(2).atDay(1).toString()
                transactions.filter { it.date >= from && it.date < until }
            }
            AnalyticsPeriod.ALL -> null
        }
    }

    val totalIncome: Double get() = periodTransactions().filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val totalExpense: Double get() = periodTransactions().filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val difference: Double get() = totalIncome - totalExpense
    val savingsRate: Int get() = if (totalIncome > 0) ((difference / totalIncome) * 100).toInt() else 0

    val expenseChangePercent: Int?
        get() {
            val previous = previousPeriodTransactions()?.filter { it.type == TxType.EXPENSE }?.sumOf { it.amount } ?: return null
            if (previous <= 0) return null
            return (((totalExpense - previous) / previous) * 100).toInt()
        }

    val topExpenseGrowth: Pair<String, Int>?
        get() {
            val previous = previousPeriodTransactions() ?: return null
            val currentByCategory = periodTransactions().filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }
            val previousByCategory = previous.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { it.value.sumOf(Transaction::amount) }
            return currentByCategory.mapNotNull { (category, amount) ->
                val previousAmount = previousByCategory[category] ?: return@mapNotNull null
                if (previousAmount <= 0 || amount <= previousAmount) return@mapNotNull null
                category to (((amount - previousAmount) / previousAmount) * 100).toInt()
            }.maxByOrNull { it.second }
        }

    // category -> amount, sorted descending — mirrors byCatAmount()/renderCatList().
    val expenseByCategory: List<Pair<String, Double>>
        get() = periodTransactions().filter { it.type == TxType.EXPENSE }
            .groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }

    val incomeByCategory: List<Pair<String, Double>>
        get() = periodTransactions().filter { it.type == TxType.INCOME }
            .groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }

    // Last 6 calendar months (oldest first) — mirrors the PWA's 6-month chart.
    val sixMonthTotals: List<MonthTotal>
        get() {
            val now = YearMonth.now()
            return (5 downTo 0).map { offset ->
                val ym = now.minusMonths(offset.toLong())
                val prefix = ym.toString()
                val monthTxs = transactions.filter { it.date.startsWith(prefix) }
                MonthTotal(
                    yearMonth = ym,
                    income = monthTxs.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                    expense = monthTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
                )
            }
        }

    // ---- Currency converter ----
    var converterAmount by mutableStateOf("1")
        private set
    var converterFrom by mutableStateOf("USD")
        private set
    var converterTo by mutableStateOf("UAH")
        private set

    fun onConverterAmountChange(value: String) { converterAmount = value }
    fun onConverterFromChange(value: String) { converterFrom = value }
    fun onConverterToChange(value: String) { converterTo = value }
    fun swapConverter() { val f = converterFrom; converterFrom = converterTo; converterTo = f }

    val converterResult: Double
        get() = repository.convertCurrency(converterAmount.toDoubleOrNull() ?: 0.0, converterFrom, converterTo, currencyRates)

    // Real synced rates take priority; SEED_RATES-backed currencies are
    // offered too so the converter/FX list aren't empty on a fresh account
    // that never opened Settings → FX rates.
    val availableCurrencies: List<String>
        get() = (listOf("UAH") + currencyRates.keys + SEED_RATES.keys).distinct()
}
