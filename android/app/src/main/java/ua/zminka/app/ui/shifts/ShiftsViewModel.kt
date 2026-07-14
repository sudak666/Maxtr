package ua.zminka.app.ui.shifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ua.zminka.app.data.model.ShiftType
import ua.zminka.app.data.repository.ShiftsRepository
import java.util.Calendar
import java.util.Locale

data class ShiftsUiState(
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val month: Int = Calendar.getInstance().get(Calendar.MONTH), // 0-based, matches java.util.Calendar
    val shiftTypes: List<ShiftType> = emptyList(),
    val dayShiftTypeIds: Map<Int, List<String>> = emptyMap(), // day-of-month -> shift type ids
    val earned: Double = 0.0,
    val hours: Double = 0.0,
    val shiftsCount: Int = 0,
    val daysOffCount: Int = 0,
)

class ShiftsViewModel(
    private val repo: ShiftsRepository = ShiftsRepository(),
) : ViewModel() {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private val monthCursor = MutableStateFlow(
        Calendar.getInstance().get(Calendar.YEAR) to Calendar.getInstance().get(Calendar.MONTH),
    )

    val state: StateFlow<ShiftsUiState> = uid?.let { currentUid ->
        combine(repo.observeShifts(currentUid), monthCursor) { doc, (year, month) ->
            doc to (year to month)
        }.map { (doc, ym) ->
            val (year, month) = ym
            val prefix = "%04d-%02d-".format(Locale.US, year, month + 1)
            var earn = 0.0
            var hrs = 0.0
            var shiftsCount = 0
            var offCount = 0
            val dayMap = mutableMapOf<Int, List<String>>()

            doc.data.forEach { (dateKey, typeIds) ->
                if (!dateKey.startsWith(prefix)) return@forEach
                val day = dateKey.substringAfterLast('-').toIntOrNull() ?: return@forEach
                dayMap[day] = typeIds
                typeIds.forEach { id ->
                    val t = doc.shiftTypes.find { it.id == id } ?: return@forEach
                    earn += t.amount
                    hrs += t.hours
                    if (t.isOff) offCount++ else shiftsCount++
                }
            }

            ShiftsUiState(
                year = year,
                month = month,
                shiftTypes = doc.shiftTypes,
                dayShiftTypeIds = dayMap,
                earned = earn,
                hours = hrs,
                shiftsCount = shiftsCount,
                daysOffCount = offCount,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShiftsUiState())
    } ?: MutableStateFlow(ShiftsUiState())

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Int) {
        val (year, month) = monthCursor.value
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        cal.add(Calendar.MONTH, delta)
        monthCursor.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    /** Toggles a shift type for a given day: removes it if already assigned, adds it otherwise. */
    fun toggleShiftType(day: Int, shiftTypeId: String) {
        val currentUid = uid ?: return
        val (year, month) = monthCursor.value
        val dateKey = "%04d-%02d-%02d".format(Locale.US, year, month + 1, day)
        val current = state.value.dayShiftTypeIds[day] ?: emptyList()
        val updated = if (shiftTypeId in current) current - shiftTypeId else current + shiftTypeId
        viewModelScope.launch { repo.setDayShiftTypes(currentUid, dateKey, updated) }
    }
}
