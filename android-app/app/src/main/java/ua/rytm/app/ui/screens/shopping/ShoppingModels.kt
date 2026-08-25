package ua.rytm.app.ui.screens.shopping
import androidx.compose.runtime.Immutable

// 1:1 with the ShoppingItem typedef in js/state.js. createdAt is carried
// through (not just a Room-only column) so toggling `done` doesn't
// accidentally reset insertion order — see ShoppingRepository.setDone().
@Immutable
data class ShoppingItem(
    val id: String,
    val name: String,
    val qty: Int,
    val done: Boolean,
    val createdAt: Long = 0,
)

// SampleFinanceData (see FINANCE_SCREEN_SPEC.md §8).
