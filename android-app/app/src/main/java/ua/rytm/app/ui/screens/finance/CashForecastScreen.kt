package ua.rytm.app.ui.screens.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.ExpandMore
import ua.rytm.app.ui.icons.Info
import ua.rytm.app.ui.icons.RytmIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmDetailScaffold
import ua.rytm.app.ui.components.formatDayMonth
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.theme.tabularNums
import kotlin.math.abs

/**
 * The verdict first (month-end balance, whether it lasts), then the sum the
 * user can check line by line and the planned days with the balance after
 * each. How usual spending is averaged and why shift pay is or isn't added
 * sit under «Як це рахується»: the method as body text read as a developer's
 * note (outside review, 2026-11-26). A screen of its own since 2026-11-27:
 * with the day-by-day list it is read, not glanced at.
 */
@Composable
fun CashForecastScreen(forecast: CashForecast, shiftsLeft: Double, onBack: () -> Unit) {
    RytmDetailScaffold(title = stringResource(R.string.cash_forecast_title), onBack = onBack) {
        // The verdict first: the number at the month end and whether it lasts.
        val gapDate = forecast.shortfallDate
        val lowestDate = forecast.lowestDate
        val short = gapDate != null && lowestDate != null
        Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
            Text(stringResource(R.string.cash_forecast_subtitle, formatDayMonth(forecast.end)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                maskedAmount(signedMoney(forecast.endBalance) + " ₴"),
                style = MaterialTheme.typography.headlineLarge.tabularNums(),
                fontWeight = FontWeight.Bold,
                color = if (forecast.endBalance < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurface,
            )
            if (short) {
                Text(stringResource(R.string.cash_forecast_gap, formatDayMonth(gapDate!!)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RytmSemantic.warning)
                Text(stringResource(R.string.cash_forecast_lowest, maskedAmount(formatMoney(forecast.shortfall)), formatDayMonth(lowestDate!!)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(R.string.cash_forecast_enough), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

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
                SumLine(stringResource(R.string.cash_forecast_end, formatDayMonth(forecast.end)), signedMoney(forecast.endBalance), bold = true)
            }
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

        // The method on request: how usual spending is averaged, why shift pay
        // is or isn't added, where the month end comes from.
        var howOpen by rememberSaveable { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Input)).clickable { howOpen = !howOpen }.padding(vertical = RytmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RytmIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.cash_forecast_how), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = RytmSpacing.sm))
            Icon(if (howOpen) RytmIcons.ExpandLess else RytmIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(howOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                val habit = forecast.habit
                Text(stringResource(R.string.cash_forecast_habit_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                    if (forecast.events.isNotEmpty()) Explain(stringResource(R.string.cash_forecast_after_hint))
                }
                if (shiftsLeft > 0) {
                    Text(stringResource(R.string.cash_forecast_shifts_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    // With a recurring income in the sum the salary is already counted;
                    // asking to add it again (as before) read as a bug to the owner.
                    val note = if (forecast.plannedIn > 0) R.string.cash_forecast_shifts_paid else R.string.cash_forecast_shifts
                    Explain(stringResource(note, maskedAmount(formatMoney(shiftsLeft))))
                }
                Explain(stringResource(R.string.cash_forecast_note))
            }
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
