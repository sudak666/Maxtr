package ua.rytm.app.widget

import kotlinx.coroutines.flow.first
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.ShiftsRepository
import ua.rytm.app.data.convertCurrencyAmount
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.Wallet
import ua.rytm.app.ui.screens.shifts.AutoFillSchedule
import ua.rytm.app.ui.screens.shifts.EarningsForecast
import ua.rytm.app.ui.screens.shifts.ShiftType
import java.time.LocalDate
import java.time.YearMonth

/** Everything the home-screen widget shows, computed from the local Room cache. */
data class WidgetSnapshot(
    val signedIn: Boolean,
    /** True when a PIN or "hide amounts" is on — the widget sits outside the PIN gate. */
    val masked: Boolean,
    val balanceUah: Double,
    val nextShift: NextShift?,
    val monthForecastUah: Double,
    val salaryGoal: Double = 0.0,
    /** Today + the next 6 days; [WeekDay.code] is null for a day with no paid shift. */
    val week: List<WeekDay> = emptyList(),
    /** Newest first, capped — the tall layout shows as many as fit. */
    val recent: List<RecentTx> = emptyList(),
) {
    data class NextShift(val date: LocalDate, val name: String, val code: String, val colorHex: Long)
    data class WeekDay(val date: LocalDate, val code: String?, val colorHex: Long)
    data class RecentTx(val title: String, val date: LocalDate?, val signedAmount: String)

    companion object {
        const val RECENT_CAP = 8
        private fun shiftCode(t: ShiftType) = t.code.ifBlank { t.name.take(1).uppercase() }.take(2)

        val SignedOut = WidgetSnapshot(signedIn = false, masked = true, balanceUah = 0.0, nextShift = null, monthForecastUah = 0.0)

        fun compute(
            today: LocalDate,
            wallets: List<Wallet>,
            transactions: List<Transaction>,
            rates: Map<String, Double>,
            types: List<ShiftType>,
            shiftsByDate: Map<String, List<String>>,
            schedule: AutoFillSchedule,
            masked: Boolean,
            salaryGoal: Double = 0.0,
        ): WidgetSnapshot {
            val balance = wallets.sumOf { w ->
                convertCurrencyAmount(FinanceRepository.walletBalance(transactions, w.id), w.currency, "UAH", rates)
            }
            val byId = types.associateBy { it.id }
            val next = shiftsByDate.entries
                .asSequence()
                .mapNotNull { (key, ids) -> runCatching { LocalDate.parse(key) }.getOrNull()?.let { it to ids } }
                .filter { (date, _) -> !date.isBefore(today) }
                .sortedBy { it.first }
                .firstNotNullOfOrNull { (date, ids) ->
                    ids.mapNotNull { byId[it] }.firstOrNull { !it.isOff }?.let { WidgetSnapshot.NextShift(date, it.name, shiftCode(it), it.colorHex) }
                }
            val forecast = EarningsForecast.forMonth(YearMonth.from(today), today, shiftsByDate, types, schedule).total
            val week = (0L until 7L).map { i ->
                val d = today.plusDays(i)
                val t = shiftsByDate[d.toString()].orEmpty().mapNotNull { byId[it] }.firstOrNull { !it.isOff }
                WeekDay(d, t?.let { shiftCode(it) }, t?.colorHex ?: 0)
            }
            val recent = transactions
                .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
                .take(RECENT_CAP)
                .map { t ->
                    val sign = when (t.type) {
                        ua.rytm.app.ui.screens.finance.TxType.INCOME -> "+"
                        ua.rytm.app.ui.screens.finance.TxType.EXPENSE -> "−"
                        else -> ""
                    }
                    RecentTx(
                        title = t.comment?.takeIf { it.isNotBlank() }?.let { "${t.category} · $it" } ?: t.category,
                        date = runCatching { LocalDate.parse(t.date) }.getOrNull(),
                        signedAmount = sign + ua.rytm.app.ui.screens.finance.formatMoney(t.amount) + " " +
                            ua.rytm.app.ui.screens.finance.currencySymbol(t.currency),
                    )
                }
            return WidgetSnapshot(true, masked, balance, next, forecast, salaryGoal, week, recent)
        }

        suspend fun load(
            finance: FinanceRepository,
            shifts: ShiftsRepository,
            signedIn: Boolean,
            masked: Boolean,
            salaryGoal: Double,
        ): WidgetSnapshot {
            if (!signedIn) return SignedOut
            return compute(
                LocalDate.now(),
                finance.wallets.first(),
                finance.transactions.first(),
                finance.currencyRates.first(),
                shifts.shiftTypes.first(),
                shifts.shiftsByDate.first(),
                shifts.autoFillSchedule.first(),
                masked,
                salaryGoal,
            )
        }
    }
}
