package ua.rytm.app.ui.screens.finance

const val CATEGORY_NAME_MAX = 120

enum class CategoryNameValidation { VALID, EMPTY, TOO_LONG }

fun validateCategoryName(value: String): CategoryNameValidation {
    val clean = value.trim()
    return when {
        clean.isEmpty() -> CategoryNameValidation.EMPTY
        clean.length > CATEGORY_NAME_MAX -> CategoryNameValidation.TOO_LONG
        else -> CategoryNameValidation.VALID
    }
}
