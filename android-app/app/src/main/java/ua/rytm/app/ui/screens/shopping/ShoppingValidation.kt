package ua.rytm.app.ui.screens.shopping

data class ShoppingValidation(val nameInvalid: Boolean, val quantityInvalid: Boolean) {
    val valid: Boolean get() = !nameInvalid && !quantityInvalid
}

fun validateShoppingDraft(name: String, quantity: String): ShoppingValidation = ShoppingValidation(
    nameInvalid = name.trim().isEmpty(),
    quantityInvalid = quantity.isNotBlank() && (quantity.toIntOrNull()?.takeIf { it >= 1 } == null),
)
