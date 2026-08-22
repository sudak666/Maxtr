package ua.rytm.app.ui.screens.shifts

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
import ua.rytm.app.data.ShiftsRepository
import ua.rytm.app.ui.screens.finance.PALETTE
import java.util.UUID

// Mirrors js/settings-managers.js's openShiftTypesManager()/renderShiftTypesList()/
// updateShiftType()/addShiftType()/deleteShiftType() — collapsed-summary-row-with-
// pencil-toggle-to-expand, same shape as CLAUDE.md's "Compact manager row" convention.
class ShiftTypesManagerViewModel(private val repository: ShiftsRepository, private val uid: String, private val profileId: String) : ViewModel() {

    companion object {
        fun factory(repository: ShiftsRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { ShiftTypesManagerViewModel(repository, uid, profileId) }
        }
    }

    var shiftTypes by mutableStateOf<List<ShiftType>>(emptyList())
        private set

    var expandedId by mutableStateOf<String?>(null)
        private set

    var pendingDeleteId by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private fun persist(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { errorMessage = "Не вдалося зберегти зміни" }
    }

    init {
        repository.shiftTypes.onEach { shiftTypes = it }.launchIn(viewModelScope)
    }

    fun toggleEdit(id: String) {
        expandedId = if (expandedId == id) null else id
    }

    fun addShiftType() {
        val color = PALETTE[shiftTypes.size % PALETTE.size]
        val name = "Нова зміна"
        persist {
            repository.addShiftType(uid, profileId,
                ShiftType(id = UUID.randomUUID().toString(), name = name, short = name.take(4), code = "", colorHex = color, amount = 0.0, hours = 8.0, isOff = false)
            )
        }
    }

    fun updateName(type: ShiftType, name: String) {
        val clean = name.trim().ifBlank { "Зміна" }
        persist { repository.updateShiftType(uid, profileId, type.copy(name = clean, short = clean.take(4))) }
    }

    fun updateAmount(type: ShiftType, amount: Double) {
        persist { repository.updateShiftType(uid, profileId, type.copy(amount = amount)) }
    }

    fun updateHours(type: ShiftType, hours: Double) {
        persist { repository.updateShiftType(uid, profileId, type.copy(hours = hours)) }
    }

    fun updateIsOff(type: ShiftType, isOff: Boolean) {
        persist { repository.updateShiftType(uid, profileId, type.copy(isOff = isOff)) }
    }

    fun requestDelete(id: String) {
        pendingDeleteId = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId ?: return
        persist { repository.deleteShiftType(uid, profileId, id) }
        pendingDeleteId = null
        if (expandedId == id) expandedId = null
    }

    fun cancelDelete() {
        pendingDeleteId = null
    }
}
