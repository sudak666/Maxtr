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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.DebtRepository
import ua.rytm.app.data.TransactionSyncState
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.annotation.StringRes
import ua.rytm.app.R

class DebtViewModel(private val app: RytmApplication) : ViewModel() {
    private val repository = app.debtRepository

    companion object {
        fun factory(app: RytmApplication) = viewModelFactory {
            initializer { DebtViewModel(app) }
        }
    }

    var debts by mutableStateOf<List<Debt>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
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

    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var syncState by mutableStateOf<TransactionSyncState?>(null)
        private set
    fun consumeError() { errorMessageRes = null }

    init {
        repository.debts
            .onEach { list ->
                debts = list
                loading = false
                loadFailed = false
                if (currentDebtId == null || list.none { it.id == currentDebtId }) currentDebtId = list.firstOrNull()?.id
            }
            .catch { loading = false; loadFailed = true }
            .launchIn(viewModelScope)
        app.debtSyncRepository.operationState
            .onEach { syncState = it }
            .launchIn(viewModelScope)
    }

    fun switchDebt(id: Long) { currentDebtId = id }

    fun addDebt(name: String, fallbackName: String) {
        val clean = name.trim().ifBlank { fallbackName }
        val debt = Debt(id = System.currentTimeMillis(), name = clean, note = "", currency = "UAH", startAmount = 0.0, dueDate = "", entries = emptyList())
        mutate(debt.id) { owner, profile -> repository.addDebt(debt, owner, profile) }
        currentDebtId = debt.id
    }

    fun requestDeleteCurrentDebt() {
        if (debts.size <= 1) { errorMessageRes = R.string.debt_last_required; return }
        pendingDeleteDebt = true
    }

    fun confirmDeleteDebt() {
        val id = currentDebtId ?: return
        mutate(null) { owner, profile -> repository.deleteDebt(id, owner, profile) }
        pendingDeleteDebt = false
    }

    fun cancelDeleteDebt() { pendingDeleteDebt = false }

    fun updateInfo(name: String, note: String, currency: String, startAmount: Double, dueDate: String) {
        val cd = currentDebt ?: return
        mutate(cd.id) { owner, profile ->
            repository.updateDebt(cd.copy(name = name.trim().ifBlank { cd.name }, note = note.trim(), currency = currency.ifBlank { "UAH" }, startAmount = startAmount, dueDate = dueDate), owner, profile)
        }
    }

    fun toggleInfoPanel() { infoExpanded = !infoExpanded }
    fun toggleHistoryPanel() { historyExpanded = !historyExpanded }

    fun openNewEntrySheet() { newEntrySheetOpen = true }
    fun closeNewEntrySheet() { newEntrySheetOpen = false }

    /** Mirrors addDebtEntry()'s auto-fill: a plain-number amount computes balance from the running total. */
    fun autoFillBalance(amountText: String): String {
        val cd = currentDebt ?: return ""
        return autoDebtBalance(cd.currentBalance(), amountText)
    }

    fun addEntry(amountText: String, balanceText: String, dateText: String) {
        val cd = currentDebt ?: return
        val amount = amountText.trim()
        if (amount.isEmpty()) { errorMessageRes = R.string.debt_amount_required; return }
        var balance = balanceText.toDoubleOrNull()
        if (balance == null) {
            val plain = parsePlainDebtAmount(amount)
            balance = if (plain != null) cd.currentBalance() - plain else { errorMessageRes = R.string.debt_balance_required; return }
        }
        val date = normalizeDebtEntryDate(dateText).ifBlank { todayLabel() }
        mutate(cd.id) { owner, profile -> repository.addEntry(cd.id, DebtEntry(id = System.currentTimeMillis(), amount = amount, balance = balance, date = date), owner, profile) }
        newEntrySheetOpen = false
    }

    fun toggleEntryEdit(id: Long) { entryEditId = if (entryEditId == id) null else id }

    fun updateEntryAmount(entry: DebtEntry, amount: String) {
        val cd = currentDebt ?: return
        mutate(cd.id) { owner, profile -> repository.updateEntry(cd.id, entry.copy(amount = amount), owner, profile) }
    }

    fun updateEntryBalance(entry: DebtEntry, balanceText: String) {
        val cd = currentDebt ?: return
        mutate(cd.id) { owner, profile -> repository.updateEntry(cd.id, entry.copy(balance = balanceText.toDoubleOrNull() ?: 0.0), owner, profile) }
    }

    fun updateEntryDate(entry: DebtEntry, date: String) {
        val cd = currentDebt ?: return
        mutate(cd.id) { owner, profile -> repository.updateEntry(cd.id, entry.copy(date = normalizeDebtEntryDate(date)), owner, profile) }
    }

    fun requestDeleteEntry(id: Long) { pendingDeleteEntryId = id }
    fun confirmDeleteEntry() {
        val id = pendingDeleteEntryId ?: return
        mutate(currentDebtId) { owner, profile -> repository.deleteEntry(id, owner, profile) }
        pendingDeleteEntryId = null
        if (entryEditId == id) entryEditId = null
    }
    fun cancelDeleteEntry() { pendingDeleteEntryId = null }

    private fun mutate(nextCurrentDebtId: Long?, change: suspend (String, String) -> Unit) {
        if (saving) return
        viewModelScope.launch {
            val accountUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val profileId = app.activeProfileStore.getActiveProfileId(accountUid)
            val activeOwnerUid = app.activeProfileStore.getActiveProfileOwnerUid(accountUid)
            if (!app.profilesRepository.canEditProfile(accountUid, activeOwnerUid, profileId)) {
                errorMessageRes = R.string.profile_read_only
                return@launch
            }
            val ownerUid = activeOwnerUid ?: accountUid
            saving = true
            try {
                app.debtSyncRepository.queueSnapshot(ownerUid, profileId, nextCurrentDebtId) {
                    change(ownerUid, profileId)
                }
            } catch (e: Exception) {
                errorMessageRes = R.string.common_save_failed
            } finally {
                saving = false
            }
        }
    }
}

fun Debt.paid(): Double = (startAmount) - currentBalance()

fun formatDebtNumber(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

fun todayLabel(): String = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(java.util.Date())
