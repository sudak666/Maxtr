package ua.rytm.app.ui.screens.shifts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import ua.rytm.app.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import ua.rytm.app.data.ShiftsRepository
import ua.rytm.app.data.ShiftsSyncRepository
import ua.rytm.app.data.TransactionSyncState
import java.time.LocalDate
import java.time.YearMonth

// Owns calendar editing, quick fill, auto-fill and six-month chart state.
class ShiftsViewModel(
    private val repository: ShiftsRepository,
    syncRepository: ShiftsSyncRepository,
    private val uid: String,
    private val profileId: String,
) : ViewModel() {

    companion object {
        fun factory(repository: ShiftsRepository, syncRepository: ShiftsSyncRepository, uid: String, profileId: String) = viewModelFactory {
            initializer { ShiftsViewModel(repository, syncRepository, uid, profileId) }
        }
    }

    var shiftTypes by mutableStateOf<List<ShiftType>>(emptyList())
        private set
    private var shiftsByDate by mutableStateOf<Map<String, List<String>>>(emptyMap())
    private var typesLoaded = false
    private var shiftsLoaded = false
    var loading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var syncState by mutableStateOf<TransactionSyncState?>(null)
        private set

    private fun markLoaded() { loading = !(typesLoaded && shiftsLoaded); loadFailed = false }
    private fun markLoadFailed() { loading = false; loadFailed = true }

    var visibleMonth by mutableStateOf(YearMonth.now())
        private set

    var dayModalDateKey by mutableStateOf<String?>(null)
        private set
    var dayModalSelection by mutableStateOf<Set<String>>(emptySet())
        private set

    // Quick-fill panel — collapsed by default (mirrors #tools-panel-body's
    // inline `style="display:none"`), plus its own template selections.
    var quickFillExpanded by mutableStateOf(false)
        private set
    var templateTypeId by mutableStateOf<String?>(null)
        private set
    var templatePattern by mutableStateOf("every")
        private set

    var autoFillSchedule by mutableStateOf(AutoFillSchedule())
        private set
    // Draft config fields for the autofill sub-panel — only committed to the
    // repository (and re-processed) on "Зберегти", mirroring
    // saveAutoFillConfig() reading the <select>/<input> DOM values rather
    // than writing on every keystroke.
    var autoFillDraftTypeId by mutableStateOf<String?>(null)
        private set
    var autoFillDraftPattern by mutableStateOf("every")
        private set
    var autoFillDraftAnchorDate by mutableStateOf("")
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }
    private fun launchMutation(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { errorMessageRes = R.string.common_save_failed }
    }

    init {
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.shiftTypes.onEach { types ->
            shiftTypes = types
            typesLoaded = true
            markLoaded()
            val firstNonOff = types.firstOrNull { !it.isOff }?.id
            if (templateTypeId == null || types.none { it.id == templateTypeId }) templateTypeId = firstNonOff
            if (autoFillDraftTypeId == null || types.none { it.id == autoFillDraftTypeId }) autoFillDraftTypeId = firstNonOff
        }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.shiftsByDate.onEach { shiftsByDate = it; shiftsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        syncRepository.operationState.onEach { syncState = it }.launchIn(viewModelScope)
        repository.autoFillSchedule.onEach { schedule ->
            autoFillSchedule = schedule
            autoFillDraftPattern = schedule.pattern
            autoFillDraftAnchorDate = schedule.anchorDate.ifBlank { LocalDate.now().toString() }
            if (schedule.typeId.isNotBlank()) autoFillDraftTypeId = schedule.typeId
        }.launchIn(viewModelScope)
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
        dayModalSelection = toggleShiftSelection(dayModalSelection, id)
    }

    fun closeDayModal() { dayModalDateKey = null }

    fun saveDayModal() {
        val dateKey = dayModalDateKey ?: return
        launchMutation { repository.setShiftsForDay(uid, profileId, dateKey, dayModalSelection.toList()) }
        dayModalDateKey = null
    }

    fun toggleQuickFillExpanded() { quickFillExpanded = !quickFillExpanded }
    fun setTemplateType(id: String) { templateTypeId = id }
    fun onTemplatePatternChanged(pattern: String) { templatePattern = pattern }

    fun applyTemplate() {
        val typeId = templateTypeId ?: return
        val monthPrefix = visibleMonth.toString() // "yyyy-MM"
        launchMutation { repository.applyTemplate(uid, profileId, monthPrefix, visibleMonth.lengthOfMonth(), typeId, templatePattern) }
    }

    fun clearCurrentMonth() {
        val monthPrefix = visibleMonth.toString()
        launchMutation { repository.clearMonth(uid, profileId, monthPrefix) }
    }

    fun setAutoFillEnabled(enabled: Boolean) {
        val next = autoFillSchedule.copy(
            enabled = enabled,
            typeId = autoFillSchedule.typeId.ifBlank { autoFillDraftTypeId.orEmpty() },
            anchorDate = autoFillSchedule.anchorDate.ifBlank { LocalDate.now().toString() },
        )
        launchMutation { repository.setAutoFillSchedule(uid, profileId, next, processDays = enabled) }
    }

    fun setAutoFillDraftType(id: String) { autoFillDraftTypeId = id }
    fun onAutoFillDraftPatternChanged(pattern: String) { autoFillDraftPattern = pattern }
    fun onAutoFillDraftAnchorDateChanged(date: String) { autoFillDraftAnchorDate = date }

    fun saveAutoFillConfig() {
        val typeId = autoFillDraftTypeId ?: return
        val next = autoFillSchedule.copy(
            typeId = typeId,
            pattern = autoFillDraftPattern,
            anchorDate = autoFillDraftAnchorDate.ifBlank { LocalDate.now().toString() },
        )
        launchMutation { repository.setAutoFillSchedule(uid, profileId, next, processDays = true) }
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

    data class MonthEarning(val yearMonth: YearMonth, val earned: Double)

    // Mirrors js/calendar.js's renderIncomeChart() — trailing 6 months ending
    // on the currently visible one's calendar month is NOT what the PWA
    // does (it's always "now", not the navigated month) — matched exactly:
    // always anchored on today's real month, independent of visibleMonth nav.
    val sixMonthEarnings: List<MonthEarning>
        get() {
            val now = YearMonth.now()
            return (5 downTo 0).map { i ->
                val ym = now.minusMonths(i.toLong())
                val prefix = ym.toString()
                var earned = 0.0
                shiftsByDate.forEach { (dateKey, ids) ->
                    if (!dateKey.startsWith(prefix)) return@forEach
                    ids.forEach { id -> shiftTypes.firstOrNull { it.id == id }?.let { earned += it.amount } }
                }
                MonthEarning(ym, earned)
            }
        }

    val today: LocalDate get() = LocalDate.now()
}
