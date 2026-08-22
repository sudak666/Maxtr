package ua.rytm.app.ui.screens.finance

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

// Mirrors js/settings-managers.js's openWalletsManager()/renderWalletsList()/
// updateWallet()/addWallet()/deleteWallet()/walletInUse() — see
// FINANCE_SCREEN_SPEC.md §10 for the exact rules ported here.
class WalletsManagerViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: FinanceSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: FinanceSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { WalletsManagerViewModel(repository, syncRepository, uid, profileId) }
        }
    }

    var wallets by mutableStateOf<List<Wallet>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    private val mutationMutex = Mutex()

    init {
        repository.wallets.onEach { wallets = it }.launchIn(viewModelScope)
    }

    fun addWallet() {
        val name = "Новий гаманець"
        val color = PALETTE[wallets.size % PALETTE.size]
        mutateAndSync {
            repository.addWallet(Wallet(id = java.util.UUID.randomUUID().toString(), name = name, colorHex = color, currency = "UAH"))
        }
    }

    fun renameWallet(wallet: Wallet, newName: String) {
        val name = newName.trim().ifBlank { "Гаманець" }
        mutateAndSync { repository.updateWallet(wallet.copy(name = name)) }
    }

    fun changeCurrency(wallet: Wallet, currency: String) {
        mutateAndSync { repository.updateWallet(wallet.copy(currency = currency)) }
    }

    fun requestDelete(id: String) {
        viewModelScope.launch {
            if (wallets.size <= 1) {
                errorMessage = "Має лишитись хоча б один гаманець"
                return@launch
            }
            if (repository.isWalletInUse(id)) {
                errorMessage = "Гаманець використовується в операціях"
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

    private fun mutateAndSync(mutation: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                val before = repository.walletSnapshot()
                try {
                    mutation()
                    syncRepository.saveWalletsSnapshot(uid, profileId)
                } catch (_: Exception) {
                    repository.replaceWallets(before)
                    errorMessage = "Не вдалося зберегти зміни. Спробуйте ще раз."
                }
            }
        }
    }
}
