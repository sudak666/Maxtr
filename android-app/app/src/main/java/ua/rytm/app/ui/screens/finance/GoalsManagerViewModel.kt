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
import ua.rytm.app.data.GoalsSyncRepository

// Mirrors js/goals-profile.js's goals-modal (openGoalsManager()/
// renderGoalsManagerList()/updateGoal()/confirmAddGoal()/deleteGoal()).
class GoalsManagerViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: GoalsSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: GoalsSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { GoalsManagerViewModel(repository, syncRepository, uid, profileId) }
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
    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private val mutationMutex = Mutex()

    fun consumeError() { errorMessage = null }

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
        mutateAndSync { repository.addGoal() }
    }

    fun updateWallet(goal: Goal, walletId: String) {
        mutateAndSync { repository.updateGoalWallet(goal, walletId) }
    }

    fun updateTargetAmount(goal: Goal, amount: Double) {
        mutateAndSync { repository.updateGoalTargetAmount(goal, amount) }
    }

    fun updateTargetDate(goal: Goal, date: String) {
        mutateAndSync { repository.updateGoalTargetDate(goal, date) }
    }

    fun requestDelete(id: String) {
        pendingDeleteId = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        mutateAndSync { repository.deleteGoal(id) }
        pendingDeleteId = null
        if (expandedId == id) expandedId = null
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }

    private fun mutateAndSync(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                val before = repository.goalsSnapshot()
                isSaving = true
                errorMessage = null
                try {
                    mutation()
                    syncRepository.saveGoalsSnapshot(uid, profileId)
                } catch (_: Exception) {
                    repository.replaceGoals(before)
                    errorMessage = "Не вдалося зберегти зміни. Спробуйте ще раз."
                } finally {
                    isSaving = false
                }
            }
        }
    }
}
