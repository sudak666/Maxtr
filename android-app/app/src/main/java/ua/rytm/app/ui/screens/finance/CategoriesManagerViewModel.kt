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
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.subKey

// Mirrors js/settings-managers.js's categories-modal (addCategory/deleteCategory,
// scoped to a flat name list per type — no subcategories/icons/budgets, see
// CategoryEntity's doc comment).
class CategoriesManagerViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { CategoriesManagerViewModel(repository) }
        }
    }

    var activeType by mutableStateOf(TxType.EXPENSE)
        private set
    /** (id, name) pairs for the active type. */
    var categories by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    fun consumeError() { errorMessage = null }

    // Mirrors js/state.js's AppState.expandedCatIdx — which category row (by id,
    // not index — Android's list isn't positionally stable the way the PWA's
    // array is) currently shows its subcategory panel expanded.
    var expandedCategoryId by mutableStateOf<String?>(null)
        private set
    var subcategoriesByKey by mutableStateOf<Map<String, List<String>>>(emptyMap())
        private set
    var categoryIcons by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    // The category name currently showing the icon-picker sheet — null when
    // closed. Mirrors js/settings-managers.js's catIconPickIdx (an index
    // there; a name here, same reasoning as expandedCategoryId above for why
    // Android uses a stable key instead of a positional index).
    var iconPickerCategory by mutableStateOf<String?>(null)
        private set

    private var allByType: Map<TxType, List<Pair<String, String>>> = emptyMap()

    init {
        repository.categoryEntriesByType.onEach {
            allByType = it
            categories = it[activeType].orEmpty()
        }.launchIn(viewModelScope)
        repository.subcategoriesByKey.onEach { subcategoriesByKey = it }.launchIn(viewModelScope)
        repository.categoryIcons.onEach { categoryIcons = it }.launchIn(viewModelScope)
    }

    fun openIconPicker(categoryName: String) { iconPickerCategory = categoryName }
    fun closeIconPicker() { iconPickerCategory = null }

    fun selectIcon(iconName: String) {
        val categoryName = iconPickerCategory ?: return
        iconPickerCategory = null
        viewModelScope.launch { repository.setCategoryIcon(categoryName, iconName) }
    }

    fun setType(type: TxType) {
        activeType = type
        categories = allByType[type].orEmpty()
        expandedCategoryId = null
    }

    fun toggleExpanded(id: String) {
        expandedCategoryId = if (expandedCategoryId == id) null else id
    }

    fun subcategoriesFor(categoryName: String): List<String> =
        subcategoriesByKey[subKey(activeType.name, categoryName)].orEmpty()

    fun addSubcategory(categoryName: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (!repository.addSubcategory(activeType, categoryName, clean)) {
                errorMessage = "Така підкатегорія вже є"
            }
        }
    }

    fun deleteSubcategory(categoryName: String, name: String) {
        viewModelScope.launch { repository.deleteSubcategory(activeType, categoryName, name) }
    }

    fun addCategory(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            if (!repository.addCategory(activeType, clean)) {
                errorMessage = "Така категорія вже є"
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { repository.deleteCategory(id) }
    }
}
