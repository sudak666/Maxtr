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
import kotlinx.coroutines.launch
import ua.rytm.app.data.FinanceRepository

// Mirrors js/settings-managers.js's recurring-modal (openRecurringManager()/
// renderRecurringList()/updateRecurring()/addRecurring()/deleteRecurring()) —
// same collapsed-summary-row-with-pencil-toggle-to-expand shape as
// BudgetsManagerViewModel/ShiftTypesManagerViewModel.
class RecurringManagerViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { RecurringManagerViewModel(repository) }
        }
    }

    var rows by mutableStateOf<List<Recurring>>(emptyList())
        private set
    var categoriesByType by mutableStateOf<Map<TxType, List<String>>>(emptyMap())
        private set
    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set
    var categoryIcons by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var expandedId by mutableStateOf<String?>(null)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    init {
        repository.recurring.onEach { rows = it }.launchIn(viewModelScope)
        combine(repository.categoriesByType, repository.wallets) { cats, wallets -> cats to wallets }
            .onEach { (cats, wallets) -> categoriesByType = cats; this.wallets = wallets }
            .launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.launchIn(viewModelScope)
    }

    fun toggleEdit(id: String) {
        expandedId = if (expandedId == id) null else id
    }

    fun addRecurring() {
        viewModelScope.launch { repository.addRecurring() }
    }

    fun updateType(r: Recurring, type: TxType) {
        viewModelScope.launch { repository.updateRecurringType(r, type) }
    }

    fun updateAmount(r: Recurring, amount: Double) {
        viewModelScope.launch { repository.updateRecurringAmount(r, amount) }
    }

    fun updateCategory(r: Recurring, category: String) {
        viewModelScope.launch { repository.updateRecurringCategory(r, category) }
    }

    fun updateWallet(r: Recurring, walletId: String) {
        viewModelScope.launch { repository.updateRecurringWallet(r, walletId) }
    }

    fun updateFrequency(r: Recurring, frequency: String) {
        viewModelScope.launch { repository.updateRecurringFrequency(r, frequency) }
    }

    fun updateNextDate(r: Recurring, nextDate: String) {
        viewModelScope.launch { repository.updateRecurringNextDate(r, nextDate) }
    }

    fun updateActive(r: Recurring, active: Boolean) {
        viewModelScope.launch { repository.updateRecurringActive(r, active) }
    }

    fun requestDelete(id: String) {
        pendingDeleteId = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        viewModelScope.launch { repository.deleteRecurring(id) }
        pendingDeleteId = null
        if (expandedId == id) expandedId = null
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }
}
