package ua.rytm.app.ui.screens.shifts

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/**
 * Earnings forecast for one calendar month from the shift calendar.
 *
 * - [earned]: shifts on days up to and including today.
 * - [planned]: shifts already placed on days after today.
 * - [fromSchedule]: days after today with nothing placed yet that the
 *   autofill schedule will fill (same on/off cycle as processAutoFillShifts).
 */
data class MonthForecast(
    val month: YearMonth,
    val earned: Double,
    val planned: Double,
    val fromSchedule: Double,
    val plannedShifts: Int,
    val scheduleShifts: Int,
) {
    val total: Double get() = earned + planned + fromSchedule
}

/** How far a forecast is from the salary goal. */
sealed interface GoalOutlook {
    data object Reached : GoalOutlook
    /** [shiftsNeeded] is null when there's no paid shift type to estimate with. */
    data class Short(val missing: Double, val shiftsNeeded: Int?) : GoalOutlook
}

object EarningsForecast {
    fun forMonth(
        month: YearMonth,
        today: LocalDate,
        shiftsByDate: Map<String, List<String>>,
        types: List<ShiftType>,
        schedule: AutoFillSchedule,
    ): MonthForecast {
        val byId = types.associateBy { it.id }
        var earned = 0.0; var planned = 0.0; var fromSchedule = 0.0
        var plannedShifts = 0; var scheduleShifts = 0
        val scheduleType = byId[schedule.typeId]?.takeIf { schedule.enabled && !it.isOff }
        val cycle = SHIFT_PATTERN_CYCLES[schedule.pattern] ?: SHIFT_PATTERN_CYCLES.getValue("every")
        val period = cycle.first + cycle.second
        val anchor = runCatching { LocalDate.parse(schedule.anchorDate) }.getOrNull()

        var day = month.atDay(1)
        val last = month.atEndOfMonth()
        while (!day.isAfter(last)) {
            val ids = shiftsByDate[day.toString()].orEmpty()
            if (ids.isNotEmpty()) {
                val sum = ids.sumOf { byId[it]?.amount ?: 0.0 }
                if (day.isAfter(today)) {
                    planned += sum
                    plannedShifts += ids.count { byId[it]?.isOff == false }
                } else earned += sum
            } else if (day.isAfter(today) && scheduleType != null && anchor != null && period > 0) {
                val diff = ChronoUnit.DAYS.between(anchor, day)
                if (diff >= 0 && diff % period < cycle.first) {
                    fromSchedule += scheduleType.amount
                    scheduleShifts++
                }
            }
            day = day.plusDays(1)
        }
        return MonthForecast(month, earned, planned, fromSchedule, plannedShifts, scheduleShifts)
    }

    /**
     * Pay of a typical shift for the "≈ N more shifts" hint: the autofill
     * type when set, otherwise the most used paid type in the calendar.
     */
    fun typicalShiftPay(shiftsByDate: Map<String, List<String>>, types: List<ShiftType>, schedule: AutoFillSchedule): Double? {
        val paid = types.filter { !it.isOff && it.amount > 0 }
        paid.firstOrNull { it.id == schedule.typeId }?.let { return it.amount }
        val counts = shiftsByDate.values.flatten().groupingBy { it }.eachCount()
        return paid.maxByOrNull { counts[it.id] ?: 0 }?.amount
    }

    fun outlook(total: Double, goal: Double, typicalPay: Double?): GoalOutlook {
        if (goal <= 0 || total >= goal) return GoalOutlook.Reached
        val missing = goal - total
        val shifts = typicalPay?.takeIf { it > 0 }?.let { ceil(missing / it).toInt() }
        return GoalOutlook.Short(missing, shifts)
    }
}
