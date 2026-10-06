package ua.rytm.app.ui.screens.shifts

import java.time.LocalDate

/**
 * How spending differs between work days and days off — the one insight
 * only an app holding both a shift calendar and a ledger can give.
 */
data class ShiftSpendingInsight(
    val avgOnShiftDays: Double,
    val avgOnOffDays: Double,
    val shiftDays: Int,
    val offDays: Int,
) {
    /** +62 means 62% more on shift days; negative means less. */
    val diffPercent: Int get() = Math.round((avgOnShiftDays / avgOnOffDays - 1) * 100).toInt()
}

object ShiftSpending {
    const val WINDOW_DAYS = 90L
    const val MIN_DAYS_EACH = 10
    const val MIN_DIFF_PERCENT = 15

    /**
     * Over the last [WINDOW_DAYS] days before today: average daily expense (UAH)
     * on days with a paid/working shift vs. every other day. Null when either
     * side has under [MIN_DAYS_EACH] days, there's no spending at all, or the
     * difference is under [MIN_DIFF_PERCENT]% — a weak signal is not shown.
     */
    fun compute(
        today: LocalDate,
        expensesUahByDate: Map<String, Double>,
        shiftsByDate: Map<String, List<String>>,
        types: List<ShiftType>,
    ): ShiftSpendingInsight? {
        val working = types.filter { !it.isOff }.map { it.id }.toSet()
        if (working.isEmpty()) return null
        // Only days inside the period the user actually tracks shifts in —
        // before the first logged shift every day would count as "off".
        val firstShift = shiftsByDate.filterValues { ids -> ids.any { it in working } }.keys.minOrNull() ?: return null
        var shiftSum = 0.0; var shiftDays = 0
        var offSum = 0.0; var offDays = 0
        var day = today.minusDays(WINDOW_DAYS)
        while (day.isBefore(today)) {
            val key = day.toString()
            if (key >= firstShift) {
                val spent = expensesUahByDate[key] ?: 0.0
                if (shiftsByDate[key].orEmpty().any { it in working }) { shiftSum += spent; shiftDays++ }
                else { offSum += spent; offDays++ }
            }
            day = day.plusDays(1)
        }
        if (shiftDays < MIN_DAYS_EACH || offDays < MIN_DAYS_EACH) return null
        val avgShift = shiftSum / shiftDays
        val avgOff = offSum / offDays
        if (avgShift <= 0 || avgOff <= 0) return null
        val insight = ShiftSpendingInsight(avgShift, avgOff, shiftDays, offDays)
        return insight.takeIf { kotlin.math.abs(it.diffPercent) >= MIN_DIFF_PERCENT }
    }
}
