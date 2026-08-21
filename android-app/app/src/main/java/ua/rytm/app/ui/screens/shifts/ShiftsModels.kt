package ua.rytm.app.ui.screens.shifts

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

// 1:1 with LEGACY_SHIFT_TYPES in js/core.js — SEED_SHIFT_TYPES.md-style
// bootstrap, same convention as SampleFinanceData/SampleShoppingData.
object SeedShiftTypes {
    val types = listOf(
        ShiftType(id = "day", name = "Денна зміна", short = "День", code = "Д", colorHex = 0xFF3B82F6, amount = 1000.0, hours = 12.0, isOff = false),
        ShiftType(id = "day1900", name = "Денна (підвищена)", short = "День+", code = "Д+", colorHex = 0xFF10B981, amount = 1900.0, hours = 12.0, isOff = false),
        ShiftType(id = "night", name = "Нічна повна", short = "Ніч", code = "Н", colorHex = 0xFF8B5CF6, amount = 900.0, hours = 12.0, isOff = false),
        ShiftType(id = "night_half1", name = "Ніч рання", short = "НічР", code = "Нр", colorHex = 0xFFEC4899, amount = 450.0, hours = 6.0, isOff = false),
        ShiftType(id = "night_half2", name = "Ніч пізня", short = "НічП", code = "Нп", colorHex = 0xFF06B6D4, amount = 450.0, hours = 6.0, isOff = false),
        ShiftType(id = "vacation", name = "Вихідний", short = "Вих", code = "В", colorHex = 0xFFF59E0B, amount = 0.0, hours = 0.0, isOff = true),
    )
}

const val SALARY_GOAL = 20000.0

// Mirrors js/state.js's AppState.autoFillSchedule.
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

val SHIFT_PATTERN_LABELS: Map<String, String> = mapOf(
    "every" to "Щодня",
    "alt" to "День через день",
    "2_2" to "2 через 2",
    "3_3" to "3 через 3",
)
