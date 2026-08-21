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

// Mirrors js/goals-profile.js's goals-modal (openGoalsManager()/
// renderGoalsManagerList()/updateGoal()/confirmAddGoal()/deleteGoal()).
class GoalsManagerViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { GoalsManagerViewModel(repository) }
        }
    }

    var goals by mutableStateOf<List<Goal>>(emptyList())
        private set
    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set
    var walletBalances by mutableStateOf<Map<String, Double>>(emptyMap())
        private set
    var expandedId by mutableStateOf<String?>(null)
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    init {
        combine(repository.goals, repository.wallets) { goals, wallets -> goals to wallets }
            .onEach { (goals, wallets) -> this.goals = goals; this.wallets = wallets }
            .launchIn(viewModelScope)
        // A goal's "saved" amount is the linked wallet's own current balance,
        // not a separate accumulator (see GoalEntity's own doc comment) —
        // reuses the same FinanceRepository.walletBalance() FinanceViewModel
        // uses for the hero card's wallet chips.
        combine(repository.transactions, repository.wallets) { txs, wallets ->
            wallets.associate { w -> w.id to FinanceRepository.walletBalance(txs, w.id) }
        }.onEach { walletBalances = it }.launchIn(viewModelScope)
    }

    fun toggleEdit(id: String) {
        expandedId = if (expandedId == id) null else id
    }

    fun addGoal() {
        viewModelScope.launch { repository.addGoal() }
    }

    fun updateWallet(goal: Goal, walletId: String) {
        viewModelScope.launch { repository.updateGoalWallet(goal, walletId) }
    }

    fun updateTargetAmount(goal: Goal, amount: Double) {
        viewModelScope.launch { repository.updateGoalTargetAmount(goal, amount) }
    }

    fun updateTargetDate(goal: Goal, date: String) {
        viewModelScope.launch { repository.updateGoalTargetDate(goal, date) }
    }

    fun requestDelete(id: String) {
        pendingDeleteId = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        viewModelScope.launch { repository.deleteGoal(id) }
        pendingDeleteId = null
        if (expandedId == id) expandedId = null
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }
}
