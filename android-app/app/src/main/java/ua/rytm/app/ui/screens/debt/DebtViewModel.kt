package ua.rytm.app.ui.screens.debt

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ua.rytm.app.data.DebtRepository
import java.text.SimpleDateFormat
import java.util.Locale

// Mirrors js/debt.js's getCurrentDebt()/renderDebt()/addDebtEntry()/etc.
// Deliberately scoped like SHIFTS_SCREEN_SPEC.md's precedent: the SVG/Preact
// payoff-forecast burndown chart is NOT ported in this step (see
// ANDROID_MIGRATION.md's "chesno not done" for this step) — everything else
// (chips, hero balance, progress bar, due chip, collapsible info/history,
// payment CRUD with swipe-to-delete) is real.
class DebtViewModel(private val repository: DebtRepository) : ViewModel() {

    companion object {
        fun factory(repository: DebtRepository) = viewModelFactory {
            initializer { DebtViewModel(repository) }
        }
    }

    var debts by mutableStateOf<List<Debt>>(emptyList())
        private set

    var currentDebtId by mutableStateOf<Long?>(null)
        private set

    val currentDebt: Debt?
        get() = debts.firstOrNull { it.id == currentDebtId } ?: debts.firstOrNull()

    var entryEditId by mutableStateOf<Long?>(null)
        private set

    var infoExpanded by mutableStateOf(false)
        private set

    var historyExpanded by mutableStateOf(true)
        private set

    var newEntrySheetOpen by mutableStateOf(false)
        private set

    var pendingDeleteDebt by mutableStateOf(false)
        private set

    var pendingDeleteEntryId by mutableStateOf<Long?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.debts.onEach { list ->
            debts = list
            if (currentDebtId == null || list.none { it.id == currentDebtId }) {
                currentDebtId = list.firstOrNull()?.id
            }
        }.launchIn(viewModelScope)
    }

    fun switchDebt(id: Long) { currentDebtId = id }

    fun addDebt(name: String) {
        val clean = name.trim().ifBlank { "Новий розрахунок" }
        val debt = Debt(id = System.currentTimeMillis(), name = clean, note = "", currency = "грн", startAmount = 0.0, dueDate = "", entries = emptyList())
        viewModelScope.launch { repository.addDebt(debt) }
        currentDebtId = debt.id
    }

    fun requestDeleteCurrentDebt() {
        if (debts.size <= 1) { errorMessage = "Має лишитись хоча б один розрахунок"; return }
        pendingDeleteDebt = true
    }

    fun confirmDeleteDebt() {
        val id = currentDebtId ?: return
        viewModelScope.launch { repository.deleteDebt(id) }
        pendingDeleteDebt = false
    }

    fun cancelDeleteDebt() { pendingDeleteDebt = false }

    fun updateInfo(name: String, note: String, currency: String, startAmount: Double, dueDate: String) {
        val cd = currentDebt ?: return
        viewModelScope.launch {
            repository.updateDebt(cd.copy(name = name.trim().ifBlank { "Розрахунок" }, note = note.trim(), currency = currency.ifBlank { "у.о." }, startAmount = startAmount, dueDate = dueDate))
        }
    }

    fun toggleInfoPanel() { infoExpanded = !infoExpanded }
    fun toggleHistoryPanel() { historyExpanded = !historyExpanded }

    fun openNewEntrySheet() { newEntrySheetOpen = true }
    fun closeNewEntrySheet() { newEntrySheetOpen = false }

    /** Mirrors addDebtEntry()'s auto-fill: a plain-number amount computes balance from the running total. */
    fun autoFillBalance(amountText: String): String {
        val cd = currentDebt ?: return ""
        val raw = amountText.trim()
        val plain = raw.toDoubleOrNull()
        return if (raw.isNotEmpty() && plain != null) formatDebtNumber(cd.currentBalance() - plain) else ""
    }

    fun addEntry(amountText: String, balanceText: String, dateText: String) {
        val cd = currentDebt ?: return
        val amount = amountText.trim()
        if (amount.isEmpty()) { errorMessage = "Вкажіть суму"; return }
        var balance = balanceText.toDoubleOrNull()
        if (balance == null) {
            val plain = amount.toDoubleOrNull()
            balance = if (plain != null) cd.currentBalance() - plain else { errorMessage = "Вкажіть залишок"; return }
        }
        val date = dateText.trim().ifBlank { todayLabel() }
        viewModelScope.launch { repository.addEntry(cd.id, DebtEntry(id = System.currentTimeMillis(), amount = amount, balance = balance, date = date)) }
        newEntrySheetOpen = false
    }

    fun toggleEntryEdit(id: Long) { entryEditId = if (entryEditId == id) null else id }

    fun updateEntryAmount(entry: DebtEntry, amount: String) {
        val cd = currentDebt ?: return
        viewModelScope.launch { repository.updateEntry(cd.id, entry.copy(amount = amount)) }
    }

    fun updateEntryBalance(entry: DebtEntry, balanceText: String) {
        val cd = currentDebt ?: return
        viewModelScope.launch { repository.updateEntry(cd.id, entry.copy(balance = balanceText.toDoubleOrNull() ?: 0.0)) }
    }

    fun updateEntryDate(entry: DebtEntry, date: String) {
        val cd = currentDebt ?: return
        viewModelScope.launch { repository.updateEntry(cd.id, entry.copy(date = date)) }
    }

    fun requestDeleteEntry(id: Long) { pendingDeleteEntryId = id }
    fun confirmDeleteEntry() {
        val id = pendingDeleteEntryId ?: return
        viewModelScope.launch { repository.deleteEntry(id) }
        pendingDeleteEntryId = null
        if (entryEditId == id) entryEditId = null
    }
    fun cancelDeleteEntry() { pendingDeleteEntryId = null }
}

fun Debt.paid(): Double = (startAmount) - currentBalance()

fun formatDebtNumber(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

fun todayLabel(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(java.util.Date())
