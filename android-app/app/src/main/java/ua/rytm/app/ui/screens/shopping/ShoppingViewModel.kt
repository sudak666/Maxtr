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
import ua.rytm.app.data.ShoppingRepository

// Mirrors js/shopping.js's addShoppingItem()/toggleShoppingItem()/
// deleteShoppingItem()/clearBoughtShopping()/renderShoppingList() — see
// SHOPPING_SCREEN_SPEC.md for the exact rules (sort order, qty default,
// clear-bought confirm).
class ShoppingViewModel(private val repository: ShoppingRepository) : ViewModel() {

    companion object {
        fun factory(repository: ShoppingRepository) = viewModelFactory {
            initializer { ShoppingViewModel(repository) }
        }
    }

    private var items by mutableStateOf<List<ShoppingItem>>(emptyList())

    var nameInput by mutableStateOf("")
        private set
    var qtyInput by mutableStateOf("")
        private set
    var clearConfirmVisible by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.items.onEach { items = it }.launchIn(viewModelScope)
    }

    fun onNameChange(value: String) { nameInput = value }
    fun onQtyChange(value: String) { qtyInput = value.filter { it.isDigit() } }

    fun addItem() {
        val name = nameInput.trim()
        if (name.isEmpty()) return
        val qty = qtyInput.toIntOrNull()?.takeIf { it >= 1 } ?: 1
        viewModelScope.launch { repository.addItem(name, qty) }
        nameInput = ""
        qtyInput = ""
    }

    fun toggle(item: ShoppingItem, done: Boolean) {
        viewModelScope.launch { repository.setDone(item, done) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun requestClearBought() { clearConfirmVisible = true }
    fun cancelClearBought() { clearConfirmVisible = false }
    fun confirmClearBought() {
        viewModelScope.launch { repository.clearBought() }
        clearConfirmVisible = false
    }

    val remainingCount: Int get() = items.count { !it.done }
    val boughtCount: Int get() = items.count { it.done }

    /** Unbought first, bought trail behind — stable within each group (mirrors the JS sort). */
    val sortedItems: List<ShoppingItem>
        get() = items.sortedBy { if (it.done) 1 else 0 }
}
