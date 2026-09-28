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
) {
    data class NextShift(val date: LocalDate, val name: String, val colorHex: Long)

    companion object {
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
                    ids.mapNotNull { byId[it] }.firstOrNull { !it.isOff }?.let { WidgetSnapshot.NextShift(date, it.name, it.colorHex) }
                }
            val forecast = EarningsForecast.forMonth(YearMonth.from(today), today, shiftsByDate, types, schedule).total
            return WidgetSnapshot(true, masked, balance, next, forecast)
        }

        suspend fun load(
            finance: FinanceRepository,
            shifts: ShiftsRepository,
            signedIn: Boolean,
            masked: Boolean,
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
            )
        }
    }
}
