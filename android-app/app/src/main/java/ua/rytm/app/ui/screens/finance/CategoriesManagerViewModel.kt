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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ua.rytm.app.data.CategoriesSyncRepository
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.local.CategoryEntity
import ua.rytm.app.data.subKey

// Mirrors js/settings-managers.js's categories-modal (addCategory/deleteCategory,
// scoped to a flat name list per type — no subcategories/icons/budgets, see
// CategoryEntity's doc comment).
class CategoriesManagerViewModel(
    private val repository: FinanceRepository,
    private val syncRepository: CategoriesSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: CategoriesSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { CategoriesManagerViewModel(repository, syncRepository, uid, profileId) }
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
    private val mutationMutex = Mutex()

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
        mutateAndSync(syncRepository::saveCategoryIconsSnapshot) {
            repository.setCategoryIcon(categoryName, iconName)
            true
        }
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
        mutateAndSync(syncRepository::saveSubcategoriesSnapshot) {
            val added = repository.addSubcategory(activeType, categoryName, clean)
            if (!added) {
                errorMessage = "Така підкатегорія вже є"
            }
            added
        }
    }

    fun deleteSubcategory(categoryName: String, name: String) {
        mutateAndSync(syncRepository::saveSubcategoriesSnapshot) {
            repository.deleteSubcategory(activeType, categoryName, name)
            true
        }
    }

    fun addCategory(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        mutateAndSync(syncRepository::saveCategoriesSnapshot) {
            val added = repository.addCategory(activeType, clean)
            if (!added) {
                errorMessage = "Така категорія вже є"
            }
            added
        }
    }

    fun deleteCategory(id: String) {
        mutateAndSync(syncRepository::saveAllCategorySnapshots) {
            repository.deleteCategory(id)
            true
        }
    }

    private fun mutateAndSync(save: suspend (String, String) -> Unit, mutate: suspend () -> Boolean) {
        viewModelScope.launch {
            mutationMutex.withLock {
                val categoriesBefore = repository.categorySnapshot()
                val subcategoriesBefore = repository.subcategorySnapshot()
                val iconsBefore = repository.categoryIconSnapshot()
                val budgetsBefore = repository.categoryBudgetSnapshot()
                val recurringBefore = repository.categoryRecurringSnapshot()
                if (!mutate()) return@withLock
                runCatching { save(uid, profileId) }.onFailure {
                    repository.restoreCategoryMutationSnapshot(
                        categoriesBefore, subcategoriesBefore, iconsBefore, budgetsBefore, recurringBefore,
                    )
                    errorMessage = "Не вдалося синхронізувати зміни"
                }
            }
        }
    }
}
