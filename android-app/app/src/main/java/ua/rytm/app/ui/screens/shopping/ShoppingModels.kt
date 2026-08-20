package ua.rytm.app.ui.screens.shopping

// 1:1 with the ShoppingItem typedef in js/state.js. createdAt is carried
// through (not just a Room-only column) so toggling `done` doesn't
// accidentally reset insertion order — see ShoppingRepository.setDone().
data class ShoppingItem(
    val id: String,
    val name: String,
    val qty: Int,
    val done: Boolean,
    val createdAt: Long = 0,
)

// SAMPLE DATA — one-time seed only, same convention as finance's
// SampleFinanceData (see FINANCE_SCREEN_SPEC.md §8).
object SampleShoppingData {
    val items = listOf(
        ShoppingItem(id = "s1", name = "Молоко", qty = 2, done = false),
        ShoppingItem(id = "s2", name = "Хліб", qty = 1, done = false),
        ShoppingItem(id = "s3", name = "Яблука", qty = 1, done = true),
    )
}
