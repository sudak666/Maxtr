package ua.rytm.app.ui.screens.shopping

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
import com.google.firebase.auth.FirebaseAuth
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.ShoppingRepository

// Mirrors js/shopping.js's addShoppingItem()/toggleShoppingItem()/
// deleteShoppingItem()/clearBoughtShopping()/renderShoppingList() — see
// SHOPPING_SCREEN_SPEC.md for the exact rules (sort order, qty default,
// clear-bought confirm).
class ShoppingViewModel(private val app: RytmApplication) : ViewModel() {
    private val repository = app.shoppingRepository

    companion object {
        fun factory(app: RytmApplication) = viewModelFactory {
            initializer { ShoppingViewModel(app) }
        }
    }

    private var items by mutableStateOf<List<ShoppingItem>>(emptyList())

    var nameInput by mutableStateOf("")
        private set
    var qtyInput by mutableStateOf("")
        private set
    var clearConfirmVisible by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.items.onEach { items = it }.launchIn(viewModelScope)
    }

    fun onNameChange(value: String) { nameInput = value }
    fun onQtyChange(value: String) { qtyInput = value.filter { it.isDigit() } }

    fun addItem() {
        val name = nameInput.trim()
        if (name.isEmpty()) { errorMessage = "Введи назву покупки"; return }
        val qty = if (qtyInput.isBlank()) 1 else qtyInput.toIntOrNull()?.takeIf { it >= 1 }
        if (qty == null) { errorMessage = "Кількість має бути цілим числом від 1"; return }
        mutate { repository.addItem(name, qty) }
        nameInput = ""
        qtyInput = ""
    }

    fun toggle(item: ShoppingItem, done: Boolean) {
        mutate { repository.setDone(item, done) }
    }

    fun delete(id: String) {
        mutate { repository.delete(id) }
    }

    fun requestClearBought() { clearConfirmVisible = true }
    fun cancelClearBought() { clearConfirmVisible = false }
    fun confirmClearBought() {
        mutate { repository.clearBought() }
        clearConfirmVisible = false
    }

    val remainingCount: Int get() = items.count { !it.done }
    val boughtCount: Int get() = items.count { it.done }

    /** Unbought first, bought trail behind — stable within each group (mirrors the JS sort). */
    val sortedItems: List<ShoppingItem>
        get() = items.sortedBy { if (it.done) 1 else 0 }

    private fun mutate(change: suspend () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            val accountUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val profileId = app.activeProfileStore.getActiveProfileId(accountUid)
            val activeOwnerUid = app.activeProfileStore.getActiveProfileOwnerUid(accountUid)
            if (!app.profilesRepository.canEditProfile(accountUid, activeOwnerUid, profileId)) {
                errorMessage = "Профіль доступний лише для перегляду"
                return@launch
            }
            val ownerUid = activeOwnerUid ?: accountUid
            val before = repository.snapshot()
            saving = true
            try {
                change()
                app.shoppingSyncRepository.saveSnapshot(ownerUid, profileId)
            } catch (e: Exception) {
                repository.restore(before)
                errorMessage = e.localizedMessage ?: "Не вдалося зберегти зміни"
            } finally {
                saving = false
            }
        }
    }
}
