package ua.zminka.app.ui.debt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.zminka.app.data.model.Debt
import ua.zminka.app.data.model.DebtDoc
import ua.zminka.app.data.model.DebtEntry
import ua.zminka.app.data.repository.DebtRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebtUiState(
    val debts: List<Debt> = emptyList(),
    val currentDebtId: Long? = null,
) {
    val currentDebt: Debt?
        get() = debts.find { it.id == currentDebtId } ?: debts.firstOrNull()

    /** Mirrors js/debt.js's debtCurrentBalance(). */
    val balance: Double
        get() = currentDebt?.let { it.entries.lastOrNull()?.balance ?: it.startAmount } ?: 0.0

    val paid: Double
        get() = (currentDebt?.startAmount ?: 0.0) - balance
}

class DebtViewModel(
    private val repo: DebtRepository = DebtRepository(),
) : ViewModel() {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val state: StateFlow<DebtUiState> = uid?.let { currentUid ->
        repo.observeDebt(currentUid).map { doc: DebtDoc ->
            DebtUiState(debts = doc.data.debts, currentDebtId = doc.data.currentDebtId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DebtUiState())
    } ?: MutableStateFlow(DebtUiState())

    fun addDebt(name: String) {
        val currentUid = uid ?: return
        val trimmed = name.trim().ifBlank { "Новий борг" }
        val newDebt = Debt(id = System.currentTimeMillis(), name = trimmed)
        val updated = state.value.debts + newDebt
        viewModelScope.launch { repo.saveDebts(currentUid, updated, newDebt.id) }
    }

    fun switchDebt(id: Long) {
        val currentUid = uid ?: return
        viewModelScope.launch { repo.saveDebts(currentUid, state.value.debts, id) }
    }

    fun addEntry(amountText: String, balanceOverride: Double?) {
        val currentUid = uid ?: return
        val current = state.value
        val debt = current.currentDebt ?: return
        if (amountText.isBlank()) return
        val plainAmount = amountText.trim().toDoubleOrNull()
        val balance = balanceOverride ?: plainAmount?.let { current.balance - it } ?: return
        val entry = DebtEntry(
            id = System.currentTimeMillis(),
            amount = amountText.trim(),
            balance = balance,
            date = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date()),
        )
        val updatedDebts = current.debts.map {
            if (it.id == debt.id) it.copy(entries = it.entries + entry) else it
        }
        viewModelScope.launch { repo.saveDebts(currentUid, updatedDebts, current.currentDebtId) }
    }
}
