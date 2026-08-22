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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.RecurringSyncRepository

// Mirrors js/settings-managers.js's recurring-modal (openRecurringManager()/
// renderRecurringList()/updateRecurring()/addRecurring()/deleteRecurring()) —
// same collapsed-summary-row-with-pencil-toggle-to-expand shape as
// BudgetsManagerViewModel/ShiftTypesManagerViewModel.
class RecurringManagerViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: RecurringSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: RecurringSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { RecurringManagerViewModel(repository, syncRepository, uid, profileId) }
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
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private val mutationMutex = Mutex()

    fun consumeError() { errorMessage = null }

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
        mutateAndSync { repository.addRecurring() }
    }

    fun updateType(r: Recurring, type: TxType) {
        mutateAndSync { repository.updateRecurringType(r, type) }
    }

    fun updateAmount(r: Recurring, amount: Double) {
        mutateAndSync { repository.updateRecurringAmount(r, amount) }
    }

    fun updateCategory(r: Recurring, category: String) {
        mutateAndSync { repository.updateRecurringCategory(r, category) }
    }

    fun updateWallet(r: Recurring, walletId: String) {
        mutateAndSync { repository.updateRecurringWallet(r, walletId) }
    }

    fun updateFrequency(r: Recurring, frequency: String) {
        mutateAndSync { repository.updateRecurringFrequency(r, frequency) }
    }

    fun updateNextDate(r: Recurring, nextDate: String) {
        mutateAndSync { repository.updateRecurringNextDate(r, nextDate) }
    }

    fun updateActive(r: Recurring, active: Boolean) {
        mutateAndSync { repository.updateRecurringActive(r, active) }
    }

    fun requestDelete(id: String) {
        pendingDeleteId = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        mutateAndSync { repository.deleteRecurring(id) }
        pendingDeleteId = null
        if (expandedId == id) expandedId = null
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }

    private fun mutateAndSync(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                val before = repository.recurringSnapshot()
                isSaving = true
                errorMessage = null
                try {
                    mutation()
                    syncRepository.saveRecurringSnapshot(uid, profileId)
                } catch (_: Exception) {
                    repository.replaceRecurring(before)
                    errorMessage = "Не вдалося зберегти зміни. Спробуйте ще раз."
                } finally {
                    isSaving = false
                }
            }
        }
    }
}
