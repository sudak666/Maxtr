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

// Mirrors js/finance.js's tags-modal (addTag()/updateTag()/deleteTag()) — same
// collapsed-row-with-pencil-toggle pattern as WalletsManagerViewModel, color
// fixed at creation (PALETTE rotation), no interactive picker yet.
class TagsManagerViewModel(private val repository: FinanceRepository) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository) = viewModelFactory {
            initializer { TagsManagerViewModel(repository) }
        }
    }

    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set

    init {
        repository.tags.onEach { tags = it }.launchIn(viewModelScope)
    }

    fun addTag(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val color = PALETTE[tags.size % PALETTE.size]
        viewModelScope.launch { repository.addTag(clean, color) }
    }

    fun renameTag(tag: Tag, newName: String) {
        val name = newName.trim().ifBlank { "Тег" }
        viewModelScope.launch { repository.renameTag(tag.id, name, tag.colorHex) }
    }

    fun requestDelete(id: String) { pendingDeleteId = id }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        viewModelScope.launch { repository.deleteTag(id) }
        pendingDeleteId = null
    }

    fun cancelDelete() { pendingDeleteId = null }
}
