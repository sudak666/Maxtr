package ua.zminka.app.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.zminka.app.data.model.FinanceDoc
import ua.zminka.app.data.model.Transaction
import ua.zminka.app.data.model.Wallet
import ua.zminka.app.data.repository.FinanceRepository

data class FinanceUiState(
    val wallets: List<Wallet> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val walletBalances: Map<String, Double> = emptyMap(),
    val totalBalanceUah: Double = 0.0,
)

class FinanceViewModel(
    private val repo: FinanceRepository = FinanceRepository(),
) : ViewModel() {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val state: StateFlow<FinanceUiState> = uid?.let { currentUid ->
        combine(
            repo.observeFinanceDoc(currentUid),
            repo.observeTransactions(currentUid),
        ) { doc: FinanceDoc, txs: List<Transaction> ->
            val balances = repo.walletBalances(txs)
            val totalUah = balances.entries.sumOf { (walletId, balance) ->
                val currency = doc.wallets.find { it.id == walletId }?.currency ?: "UAH"
                convertToBase(balance, currency, doc.currencyRates)
            }
            FinanceUiState(
                wallets = doc.wallets,
                transactions = txs,
                walletBalances = balances,
                totalBalanceUah = totalUah,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinanceUiState())
    } ?: MutableStateFlow(FinanceUiState())

    fun addTransaction(
        type: String,
        amount: Double,
        currency: String,
        category: String,
        walletId: String,
        date: String,
        comment: String,
    ) {
        val currentUid = uid ?: return
        val tx = Transaction(
            id = System.currentTimeMillis(),
            type = type,
            amount = amount,
            currency = currency,
            category = category,
            wallet = walletId,
            date = date,
            comment = comment,
        )
        viewModelScope.launch { repo.saveTransaction(currentUid, tx) }
    }

    fun deleteTransaction(id: Long) {
        val currentUid = uid ?: return
        viewModelScope.launch { repo.deleteTransaction(currentUid, id) }
    }

    /** Mirrors js/core.js's convertCurrency()/toBase() — UAH is the base currency. */
    private fun convertToBase(amount: Double, fromCode: String, rates: Map<String, Double>): Double {
        if (fromCode == "UAH") return amount
        val fromRate = rates[fromCode] ?: 1.0
        val toRate = rates["UAH"] ?: 1.0
        return amount * fromRate / toRate
    }
}
