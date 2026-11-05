package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.components.TapThrottle

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
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.FinanceSyncRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.annotation.StringRes
import ua.rytm.app.R

// Mirrors js/settings-managers.js's openWalletsManager()/renderWalletsList()/
// updateWallet()/addWallet()/deleteWallet()/walletInUse() — see
// FINANCE_SCREEN_SPEC.md §10 for the exact rules ported here.
/** A wallet the user wants gone that still has operations: where can they go. */
data class WalletMoveRequest(val walletId: String, val walletName: String, val useCount: Int, val targets: List<Wallet>)

class WalletsManagerViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: FinanceSyncRepository,
    private val recurringSyncRepository: ua.rytm.app.data.RecurringSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: FinanceSyncRepository, recurringSyncRepository: ua.rytm.app.data.RecurringSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { WalletsManagerViewModel(repository, syncRepository, recurringSyncRepository, uid, profileId) }
        }
    }

    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set

    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }

    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    var pendingMove by mutableStateOf<WalletMoveRequest?>(null)
        private set

    private val mutationMutex = Mutex()

    init {
        repository.wallets.onEach { wallets = it }.launchIn(viewModelScope)
    }

    private val addThrottle = TapThrottle()

    fun addWallet(name: String, currency: String, kind: String, openingBalance: Double) {
        if (!addThrottle.allow()) return
        val color = PALETTE[wallets.size % PALETTE.size]
        mutateAndSync {
            repository.addWallet(Wallet(id = java.util.UUID.randomUUID().toString(), name = name.trim(), colorHex = color, currency = currency, kind = kind, openingBalance = openingBalance))
        }
    }

    fun renameWallet(wallet: Wallet, newName: String) {
        val name = newName.trim().ifBlank { wallet.name }
        mutateAndSync { repository.updateWallet(wallet.copy(name = name)) }
    }

    fun changeCurrency(wallet: Wallet, currency: String) {
        mutateAndSync { repository.updateWallet(wallet.copy(currency = currency)) }
    }

    fun changeColor(wallet: Wallet, colorHex: Long) {
        mutateAndSync { repository.updateWallet(wallet.copy(colorHex = colorHex)) }
    }

    fun update(wallet: Wallet) {
        mutateAndSync { repository.updateWallet(wallet) }
    }

    fun requestDelete(id: String) {
        viewModelScope.launch {
            if (wallets.size <= 1) {
                errorMessageRes = R.string.wallet_last_required
                return@launch
            }
            if (repository.isWalletInUse(id)) {
                // Used to stop here with a line at the top of the sheet, which was off
                // screen while deleting a wallet further down: the tap seemed to do nothing.
                val wallet = wallets.firstOrNull { it.id == id } ?: return@launch
                val blocked = repository.transferCounterparts(id)
                val targets = wallets.filter { it.id != id && it.currency == wallet.currency && it.id !in blocked }
                pendingMove = WalletMoveRequest(id, wallet.name, repository.walletUseCount(id), targets)
                return@launch
            }
            pendingDeleteId = id
        }
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        mutateAndSync { repository.deleteWallet(id) }
        pendingDeleteId = null
    }

    fun cancelDelete() { pendingDeleteId = null }

    fun cancelMove() { pendingMove = null }

    /** Moves the operations to [targetId], then deletes the wallet. */
    fun confirmMove(targetId: String) {
        val request = pendingMove ?: return
        pendingMove = null
        viewModelScope.launch {
            mutationMutex.withLock {
                try {
                    val moved = ua.rytm.app.data.LocalWriteLock.mutex.withLock {
                        repository.moveWalletOperations(request.walletId, targetId).also { repository.deleteWallet(request.walletId) }
                    }
                    syncRepository.saveMovedTransactions(uid, profileId, moved)
                    recurringSyncRepository.saveRecurringSnapshot(uid, profileId)
                    syncRepository.saveWalletsSnapshot(uid, profileId)
                } catch (_: Exception) {
                    errorMessageRes = R.string.common_save_retry
                }
            }
        }
    }

    private fun mutateAndSync(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                val before = repository.walletSnapshot()
                try {
                    mutation()
                    syncRepository.saveWalletsSnapshot(uid, profileId)
                } catch (_: Exception) {
                    repository.replaceWallets(before)
                    errorMessageRes = R.string.common_save_retry
                }
            }
        }
    }
}
