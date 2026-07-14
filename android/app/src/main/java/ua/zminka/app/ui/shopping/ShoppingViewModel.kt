package ua.zminka.app.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.zminka.app.data.model.ShoppingItem
import ua.zminka.app.data.repository.FinanceRepository

data class ShoppingUiState(
    val items: List<ShoppingItem> = emptyList(),
) {
    // Bought items sort to the bottom — mirrors js/shopping.js's
    // renderShoppingList() ordering (done items visually demoted).
    val sorted: List<ShoppingItem>
        get() = items.sortedBy { it.done }
}

class ShoppingViewModel(
    private val repo: FinanceRepository = FinanceRepository(),
) : ViewModel() {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val state: StateFlow<ShoppingUiState> = uid?.let { currentUid ->
        repo.observeFinanceDoc(currentUid).map { doc ->
            ShoppingUiState(items = doc.shoppingList)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShoppingUiState())
    } ?: MutableStateFlow(ShoppingUiState())

    fun addItem(name: String, qty: Int) {
        val currentUid = uid ?: return
        if (name.isBlank()) return
        val item = ShoppingItem(
            id = "item_${System.currentTimeMillis()}",
            name = name.trim(),
            qty = qty.coerceAtLeast(1),
            createdAt = System.currentTimeMillis(),
        )
        val updated = state.value.items + item
        viewModelScope.launch { repo.saveShoppingList(currentUid, updated) }
    }

    fun toggle(id: String) {
        val currentUid = uid ?: return
        val updated = state.value.items.map { if (it.id == id) it.copy(done = !it.done) else it }
        viewModelScope.launch { repo.saveShoppingList(currentUid, updated) }
    }

    fun delete(id: String) {
        val currentUid = uid ?: return
        val updated = state.value.items.filter { it.id != id }
        viewModelScope.launch { repo.saveShoppingList(currentUid, updated) }
    }

    fun clearBought() {
        val currentUid = uid ?: return
        val updated = state.value.items.filter { !it.done }
        viewModelScope.launch { repo.saveShoppingList(currentUid, updated) }
    }
}
