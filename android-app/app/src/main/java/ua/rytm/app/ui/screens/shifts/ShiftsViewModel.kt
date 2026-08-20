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
import java.time.LocalDate
import java.time.YearMonth

// Mirrors js/calendar.js's renderCalendar()/openModal()/saveModalSelection()
// — scoped per SHIFTS_SCREEN_SPEC.md (no quick-fill/autofill/income chart yet).
class ShiftsViewModel(private val repository: ShiftsRepository) : ViewModel() {

    companion object {
        fun factory(repository: ShiftsRepository) = viewModelFactory {
            initializer { ShiftsViewModel(repository) }
        }
    }

    var shiftTypes by mutableStateOf<List<ShiftType>>(emptyList())
        private set
    private var shiftsByDate by mutableStateOf<Map<String, List<String>>>(emptyMap())

    var visibleMonth by mutableStateOf(YearMonth.now())
        private set

    var dayModalDateKey by mutableStateOf<String?>(null)
        private set
    var dayModalSelection by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.shiftTypes.onEach { shiftTypes = it }.launchIn(viewModelScope)
        repository.shiftsByDate.onEach { shiftsByDate = it }.launchIn(viewModelScope)
    }

    fun goToPreviousMonth() { visibleMonth = visibleMonth.minusMonths(1) }
    fun goToNextMonth() { visibleMonth = visibleMonth.plusMonths(1) }
    fun goToToday() { visibleMonth = YearMonth.now() }

    fun shiftsFor(dateKey: String): List<ShiftType> =
        shiftsByDate[dateKey].orEmpty().mapNotNull { id -> shiftTypes.firstOrNull { it.id == id } }

    fun openDayModal(dateKey: String) {
        dayModalDateKey = dateKey
        dayModalSelection = shiftsByDate[dateKey].orEmpty().toSet()
    }

    fun toggleDayModalType(id: String) {
        dayModalSelection = if (id in dayModalSelection) dayModalSelection - id else dayModalSelection + id
    }

    fun closeDayModal() { dayModalDateKey = null }

    fun saveDayModal() {
        val dateKey = dayModalDateKey ?: return
        viewModelScope.launch { repository.setShiftsForDay(dateKey, dayModalSelection.toList()) }
        dayModalDateKey = null
    }

    data class MonthStats(val earned: Double, val hours: Double, val shiftsCount: Int, val offCount: Int)

    val monthStats: MonthStats
        get() {
            val prefix = visibleMonth.toString() // "yyyy-MM"
            var earned = 0.0; var hours = 0.0; var shiftsCount = 0; var offCount = 0
            shiftsByDate.forEach { (dateKey, ids) ->
                if (!dateKey.startsWith(prefix)) return@forEach
                ids.forEach { id ->
                    val t = shiftTypes.firstOrNull { it.id == id } ?: return@forEach
                    earned += t.amount; hours += t.hours
                    if (t.isOff) offCount++ else shiftsCount++
                }
            }
            return MonthStats(earned, hours, shiftsCount, offCount)
        }

    val today: LocalDate get() = LocalDate.now()
}
