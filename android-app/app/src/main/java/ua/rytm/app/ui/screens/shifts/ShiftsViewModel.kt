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
import ua.rytm.app.data.WidgetSettingsSyncRepository
import ua.rytm.app.data.local.SettingsStore
import java.time.LocalDate
import java.time.YearMonth

// Mirrors js/calendar.js's renderCalendar()/openModal()/saveModalSelection()/
// applyTemplate()/toggleAutoFill()/renderIncomeChart() — full parity as of
// step 39 (quick-fill/autofill/6-month chart, previously deferred by
// SHIFTS_SCREEN_SPEC.md's step 8 scoping).
class ShiftsViewModel(
    private val repository: ShiftsRepository,
    private val uid: String,
    private val profileId: String,
    expensesUahByDateFlow: kotlinx.coroutines.flow.Flow<Map<String, Double>> = kotlinx.coroutines.flow.flowOf(emptyMap()),
    private val settingsStore: SettingsStore? = null,
    private val widgetSettingsSync: WidgetSettingsSyncRepository? = null,
    private val accountUid: String = uid,
) : ViewModel() {

    companion object {
        fun factory(
            repository: ShiftsRepository,
            uid: String,
            profileId: String,
            expensesUahByDateFlow: kotlinx.coroutines.flow.Flow<Map<String, Double>> = kotlinx.coroutines.flow.flowOf(emptyMap()),
            settingsStore: SettingsStore? = null,
            widgetSettingsSync: WidgetSettingsSyncRepository? = null,
            accountUid: String = uid,
        ) = viewModelFactory {
            initializer { ShiftsViewModel(repository, uid, profileId, expensesUahByDateFlow, settingsStore, widgetSettingsSync, accountUid) }
        }
    }

    private var expensesUahByDate by mutableStateOf<Map<String, Double>>(emptyMap())

    var shiftTypes by mutableStateOf<List<ShiftType>>(emptyList())
        private set
    private var shiftsByDate by mutableStateOf<Map<String, List<String>>>(emptyMap())
    private var typesLoaded = false
    private var shiftsLoaded = false
    var loading by mutableStateOf(true)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    private fun markLoaded() { loading = !(typesLoaded && shiftsLoaded); loadFailed = false }
    private fun markLoadFailed() { loading = false; loadFailed = true }

    var visibleMonth by mutableStateOf(YearMonth.now())
        private set

    var dayModalDateKey by mutableStateOf<String?>(null)
        private set
    var dayModalSelection by mutableStateOf<Set<String>>(emptySet())
        private set

    // Quick-fill panel — collapsed by default.
    var quickFillExpanded by mutableStateOf(false)
        private set

    var autoFillSchedule by mutableStateOf(AutoFillSchedule())
        private set
    // One template for both quick-fill actions (outside review, 2026-11-27):
    // filling the month and the autofill used to be two copies of the same
    // form, and the month fill ignored the cycle's first day. It starts from
    // the saved schedule; while autofill is on, every change is saved to it.
    var templateTypeId by mutableStateOf<String?>(null)
        private set
    var templatePattern by mutableStateOf("every")
        private set
    var templateAnchorDate by mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())
        private set
    @get:StringRes
    var errorMessageRes by mutableStateOf<Int?>(null)
        private set
    fun consumeError() { errorMessageRes = null }
    private fun launchMutation(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { errorMessageRes = R.string.common_save_failed }
    }

    /** Device-local goal first, then the shared profile copy (queued; server rejections reach SyncWriteErrors). */
    fun saveSalaryGoal(amount: Double) = launchMutation {
        settingsStore?.setSalaryGoal(accountUid, amount)
        widgetSettingsSync?.saveSalaryGoal(uid, profileId, amount)
    }

    init {
        expensesUahByDateFlow.onEach { expensesUahByDate = it }.catch { }.launchIn(viewModelScope)
        viewModelScope.launch { repository.seedIfEmpty() }
        repository.shiftTypes.onEach { types ->
            shiftTypes = types
            typesLoaded = true
            markLoaded()
            val firstNonOff = types.firstOrNull { !it.isOff }?.id
            if (templateTypeId == null || types.none { it.id == templateTypeId }) templateTypeId = firstNonOff
        }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.shiftsByDate.onEach { shiftsByDate = it; shiftsLoaded = true; markLoaded() }.catch { markLoadFailed() }.launchIn(viewModelScope)
        repository.autoFillSchedule.onEach { schedule ->
            autoFillSchedule = schedule
            templatePattern = schedule.pattern
            if (schedule.anchorDate.isNotBlank()) templateAnchorDate = schedule.anchorDate
            if (schedule.typeId.isNotBlank()) templateTypeId = schedule.typeId
        }.launchIn(viewModelScope)
    }

    fun goToPreviousMonth() { visibleMonth = visibleMonth.minusMonths(1) }
    fun goToNextMonth() { visibleMonth = visibleMonth.plusMonths(1) }
    fun goToToday() { visibleMonth = YearMonth.now() }

    /** iCalendar text for the visible month (see ShiftsIcs). */
    fun visibleMonthIcs(): String = ShiftsIcs.build(visibleMonth, shiftsByDate, shiftTypes)

    fun shiftsFor(dateKey: String): List<ShiftType> =
        shiftsByDate[dateKey].orEmpty().mapNotNull { id -> shiftTypes.firstOrNull { it.id == id } }

    // Paint mode: with a shift type picked as the "brush", tapping a day
    // toggles that type on it directly — no sheet per day. Filling a month by
    // hand used to cost two taps + a sheet per day.
    var brushTypeId by mutableStateOf<String?>(null)
        private set

    fun selectBrush(typeId: String?) { brushTypeId = if (brushTypeId == typeId) null else typeId }

    fun onDayTapped(dateKey: String) {
        val brush = brushTypeId ?: return openDayModal(dateKey)
        val current = shiftsByDate[dateKey].orEmpty()
        val next = if (brush in current) current - brush else current + brush
        // Optimistic: the cell updates on the tap itself, Room confirms.
        shiftsByDate = shiftsByDate + (dateKey to next)
        launchMutation { repository.setShiftsForDay(uid, profileId, dateKey, next) }
    }

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
    fun setTemplateType(id: String) { templateTypeId = id; saveTemplateIfAuto() }
    fun onTemplatePatternChanged(pattern: String) { templatePattern = pattern; saveTemplateIfAuto() }
    fun onTemplateAnchorDateChanged(date: String) { templateAnchorDate = date; saveTemplateIfAuto() }

    private val templateAnchor: LocalDate
        get() = runCatching { LocalDate.parse(templateAnchorDate) }.getOrNull() ?: visibleMonth.atDay(1)

    /** Fills the month on screen from the template, replacing what was there. */
    fun applyTemplate() {
        val typeId = templateTypeId ?: return
        val month = visibleMonth
        val anchor = templateAnchor
        val pattern = templatePattern
        launchMutation { repository.applyTemplate(uid, profileId, month, typeId, pattern, anchor) }
    }

    fun clearCurrentMonth() {
        val monthPrefix = visibleMonth.toString()
        launchMutation { repository.clearMonth(uid, profileId, monthPrefix) }
    }

    /** Turning autofill on saves the template shown above it, so what the user sees is what runs. */
    fun setAutoFillEnabled(enabled: Boolean) {
        val next = if (enabled) {
            val typeId = templateTypeId ?: return
            autoFillSchedule.copy(enabled = true, typeId = typeId, pattern = templatePattern, anchorDate = templateAnchor.toString())
        } else {
            autoFillSchedule.copy(enabled = false)
        }
        launchMutation { repository.setAutoFillSchedule(uid, profileId, next, processDays = enabled) }
    }

    private fun saveTemplateIfAuto() {
        if (!autoFillSchedule.enabled) return
        val typeId = templateTypeId ?: return
        val next = autoFillSchedule.copy(typeId = typeId, pattern = templatePattern, anchorDate = templateAnchor.toString())
        if (next == autoFillSchedule) return
        launchMutation { repository.setAutoFillSchedule(uid, profileId, next, processDays = true) }
    }

    /** [earned] is every shift of the month; [earnedToDate] only those up to and including today. */
    data class MonthStats(val earned: Double, val hours: Double, val shiftsCount: Int, val offCount: Int, val earnedToDate: Double = earned)

    val monthStats: MonthStats
        get() {
            val prefix = visibleMonth.toString() // "yyyy-MM"
            var earned = 0.0; var hours = 0.0; var shiftsCount = 0; var offCount = 0; var toDate = 0.0
            val today = java.time.LocalDate.now().toString()
            shiftsByDate.forEach { (dateKey, ids) ->
                if (!dateKey.startsWith(prefix)) return@forEach
                ids.forEach { id ->
                    val t = shiftTypes.firstOrNull { it.id == id } ?: return@forEach
                    earned += t.amount; hours += t.hours
                    if (dateKey <= today) toDate += t.amount
                    if (t.isOff) offCount++ else shiftsCount++
                }
            }
            return MonthStats(earned, hours, shiftsCount, offCount, toDate)
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

    // Always the real current + next month (like the income chart), not the
    // navigated one — a forecast of a past month makes no sense.
    val currentForecast: MonthForecast
        get() = EarningsForecast.forMonth(YearMonth.now(), LocalDate.now(), shiftsByDate, shiftTypes, autoFillSchedule)
    val nextForecast: MonthForecast
        get() = EarningsForecast.forMonth(YearMonth.now().plusMonths(1), LocalDate.now(), shiftsByDate, shiftTypes, autoFillSchedule)
    val spendingInsight: ShiftSpendingInsight?
        get() = ShiftSpending.compute(LocalDate.now(), expensesUahByDate, shiftsByDate, shiftTypes)
    val typicalShiftPay: Double?
        get() = EarningsForecast.typicalShiftPay(shiftsByDate, shiftTypes, autoFillSchedule)

    val today: LocalDate get() = LocalDate.now()
}
