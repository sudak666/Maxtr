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
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.TagsSyncRepository
import androidx.annotation.StringRes
import ua.rytm.app.R

// Mirrors js/finance.js's tags-modal (addTag()/updateTag()/deleteTag()) — same
// collapsed-row-with-pencil-toggle pattern as WalletsManagerViewModel, color
// fixed at creation (PALETTE rotation), no interactive picker yet.
class TagsManagerViewModel(private val repository: FinanceRepository, private val syncRepository: TagsSyncRepository, private val uid: String, private val profileId: String) : ViewModel() {

    companion object {
        fun factory(repository: FinanceRepository, syncRepository: TagsSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { TagsManagerViewModel(repository, syncRepository, uid, profileId) }
        }
    }

    var tags by mutableStateOf<List<Tag>>(emptyList())
        private set
    var pendingDeleteId by mutableStateOf<String?>(null)
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }
    private val mutationMutex = Mutex()

    init {
        repository.tags.onEach { tags = it }.launchIn(viewModelScope)
    }

    fun addTag(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val color = PALETTE[tags.size % PALETTE.size]
        mutateAndSync { repository.addTag(clean, color) }
    }

    fun renameTag(tag: Tag, newName: String) {
        val name = newName.trim().ifBlank { tag.name }
        mutateAndSync { repository.renameTag(tag.id, name, tag.colorHex) }
    }

    fun requestDelete(id: String) { pendingDeleteId = id }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        mutateAndSync { repository.deleteTag(id) }
        pendingDeleteId = null
    }

    fun cancelDelete() { pendingDeleteId = null }

    private fun mutateAndSync(mutation: suspend () -> Unit) {
        viewModelScope.launch { mutationMutex.withLock {
            val before = repository.tagsRollbackSnapshot()
            try {
                mutation()
                val after = repository.tagsRollbackSnapshot()
                val beforeById = before.transactions.associateBy { it.id }
                val changedTransactions = after.transactions.filter { beforeById[it.id]?.tags != it.tags }
                if (changedTransactions.isEmpty()) syncRepository.saveTagsSnapshot(uid, profileId)
                else syncRepository.saveTagsAndChangedTransactions(uid, profileId, changedTransactions)
            } catch (_: Exception) {
                repository.restoreTagsSnapshot(before)
                errorMessageRes = R.string.tags_save_failed
            }
        } }
    }
}
