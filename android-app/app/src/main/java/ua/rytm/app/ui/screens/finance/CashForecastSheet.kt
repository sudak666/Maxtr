package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.components.formatDayMonth
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing
import kotlin.math.abs

/**
 * The forecast as a sum the user can check line by line: balance now, plus
 * planned income, minus planned payments, minus usual spending, equals the
 * month end. Then where usual spending comes from, why shift pay is left out,
 * and the planned days with the balance after each.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CashForecastSheet(forecast: CashForecast, shiftsLeft: Double, onDismiss: () -> Unit) {
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            RytmSheetTitle(stringResource(R.string.cash_forecast_title), subtitle = stringResource(R.string.cash_forecast_subtitle, formatDayMonth(forecast.end)))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(RytmRadii.Input),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(Modifier.padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    SumLine(stringResource(R.string.cash_forecast_now), formatMoney(forecast.startBalance), stringResource(R.string.cash_forecast_now_hint))
                    if (forecast.plannedIn > 0) SumLine(stringResource(R.string.cash_forecast_in), "+" + formatMoney(forecast.plannedIn), color = RytmSemantic.signed(1.0))
                    if (forecast.plannedOut > 0) SumLine(stringResource(R.string.cash_forecast_out), "−" + formatMoney(forecast.plannedOut), color = RytmSemantic.signed(-1.0))
                    forecast.habit?.let { habit ->
                        SumLine(
                            stringResource(R.string.cash_forecast_habit),
                            "−" + formatMoney(forecast.unplannedSpend),
                            stringResource(R.string.cash_forecast_habit_hint, formatMoney(habit.perDay), forecast.unplannedDays),
                            color = RytmSemantic.signed(-1.0),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val endColor = if (forecast.endBalance < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurface
                    SumLine(stringResource(R.string.cash_forecast_end, formatDayMonth(forecast.end)), signedMoney(forecast.endBalance), color = endColor, bold = true)
                    val gapDate = forecast.shortfallDate
                    val lowestDate = forecast.lowestDate
                    if (gapDate != null && lowestDate != null) {
                        Text(stringResource(R.string.cash_forecast_gap, formatDayMonth(gapDate)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RytmSemantic.warning)
                        Text(stringResource(R.string.cash_forecast_lowest, maskedAmount(formatMoney(forecast.shortfall)), formatDayMonth(lowestDate)), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(stringResource(R.string.cash_forecast_enough), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text(stringResource(R.string.cash_forecast_habit_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val habit = forecast.habit
            if (habit == null) {
                Explain(stringResource(R.string.cash_forecast_no_history))
            } else {
                Explain(stringResource(R.string.cash_forecast_habit_explain, habit.windowDays, habit.droppedDays))
                if (habit.topCategories.isNotEmpty()) {
                    val parts = mutableListOf<String>()
                    habit.topCategories.forEach { (category, sum) -> parts += localizedDomainText(category) + " " + maskedAmount(formatMoney(sum) + " ₴") }
                    val top = parts.joinToString(" · ")
                    Explain(stringResource(R.string.cash_forecast_habit_top, top))
                }
            }

            if (shiftsLeft > 0) {
                Text(stringResource(R.string.cash_forecast_shifts_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                // With a recurring income in the sum the salary is already counted;
                // asking to add it again (as before) read as a bug to the owner.
                val note = if (forecast.plannedIn > 0) R.string.cash_forecast_shifts_paid else R.string.cash_forecast_shifts
                Explain(stringResource(note, maskedAmount(formatMoney(shiftsLeft))))
            }

            Text(stringResource(R.string.cash_forecast_events), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (forecast.events.isEmpty()) Explain(stringResource(R.string.cash_forecast_no_events))
            forecast.events.forEach { event ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = RytmSpacing.md)) {
                        Text(localizedDomainText(event.label), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(formatDayMonth(event.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val sign = if (event.amountUah < 0) "−" else "+"
                        Text(maskedAmount("$sign${formatMoney(abs(event.amountUah))} ₴"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RytmSemantic.signed(event.amountUah))
                        Text(stringResource(R.string.cash_forecast_after, maskedAmount(formatMoney(event.balanceAfter))), style = MaterialTheme.typography.labelSmall, color = if (event.balanceAfter < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (forecast.events.isNotEmpty() && forecast.habit != null) Explain(stringResource(R.string.cash_forecast_after_hint))
            Explain(stringResource(R.string.cash_forecast_note))
        }
    }
}

@Composable
private fun SumLine(label: String, amount: String, hint: String? = null, color: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = RytmSpacing.md)) {
            Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else null)
            hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(maskedAmount("$amount ₴"), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
