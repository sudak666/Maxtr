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
import ua.rytm.app.data.BudgetsSyncRepository
import androidx.annotation.StringRes
import ua.rytm.app.R
import ua.rytm.app.data.FinanceRepository

// Mirrors js/settings-managers.js's budgets-modal (openBudgetsManager()/
// renderBudgetsManagerList()/updateBudget()) — same collapsed-summary-
// row-with-pencil-toggle-to-expand shape as ShiftTypesManagerViewModel,
// scoped to EXPENSE categories only (the PWA never lets a budget target an
// income category).
class BudgetsManagerViewModel(private val repository: FinanceRepository, private val syncRepository: BudgetsSyncRepository, private val uid: String, private val profileId: String) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: BudgetsSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { BudgetsManagerViewModel(repository, syncRepository, uid, profileId) }
        }
    }

    /** (category name, current limit or 0 if none) pairs, expense categories only. */
    var rows by mutableStateOf<List<Pair<String, Double>>>(emptyList())
        private set
    var expandedCategory by mutableStateOf<String?>(null)
        private set
    var categoryIcons by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }
    private val mutationMutex = Mutex()

    init {
        combine(repository.categoriesByType, repository.budgets) { byType, budgets ->
            byType[TxType.EXPENSE].orEmpty().map { name -> name to (budgets[name] ?: 0.0) }
        }.onEach { rows = it }.launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.launchIn(viewModelScope)
    }

    fun toggleEdit(category: String) {
        expandedCategory = if (expandedCategory == category) null else category
    }

    fun updateBudget(category: String, amount: Double) {
        viewModelScope.launch { mutationMutex.withLock {
            val before = repository.budgetRollbackSnapshot()
            try {
                repository.setBudget(category, amount)
                syncRepository.saveBudgetsSnapshot(uid, profileId)
            } catch (_: Exception) {
                repository.replaceBudgets(before)
                errorMessageRes = R.string.budgets_save_failed
            }
        } }
    }
}
