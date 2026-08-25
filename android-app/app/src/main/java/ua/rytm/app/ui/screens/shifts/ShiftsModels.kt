package ua.rytm.app.ui.screens.shifts
import androidx.compose.runtime.Immutable

@Immutable
data class ShiftType(
    val id: String,
    val name: String,
    val short: String,
    val code: String,
    val colorHex: Long,
    val amount: Double,
    val hours: Double,
    val isOff: Boolean,
)

// bootstrap, same convention as SampleFinanceData/SampleShoppingData.
const val SALARY_GOAL = 20000.0

// Mirrors js/state.js's AppState.autoFillSchedule.
@Immutable
data class AutoFillSchedule(
    val enabled: Boolean = false,
    val typeId: String = "",
    val pattern: String = "every",
    val anchorDate: String = "",
)

// Mirrors js/calendar.js's SHIFT_PATTERN_CYCLES — [onDays, offDays] per cycle,
// shared by both the quick-fill template and autofill's day-by-day check.
val SHIFT_PATTERN_CYCLES: Map<String, Pair<Int, Int>> = mapOf(
    "every" to (1 to 0),
    "alt" to (1 to 1),
    "2_2" to (2 to 2),
    "3_3" to (3 to 3),
)

