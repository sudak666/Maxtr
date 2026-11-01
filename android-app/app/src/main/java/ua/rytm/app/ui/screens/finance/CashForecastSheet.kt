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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import kotlin.math.floor

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CashForecastSheet(forecast: CashForecast, onDismiss: () -> Unit) {
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
                Column(Modifier.padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
                    val gapDate = forecast.shortfallDate
                    if (gapDate != null) {
                        Text(stringResource(R.string.cash_forecast_gap, formatDayMonth(gapDate)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RytmSemantic.warning)
                        Text(stringResource(R.string.cash_forecast_gap_amount, maskedAmount(formatMoney(forecast.shortfall))), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.cash_forecast_enough, maskedAmount(formatMoney(floor(forecast.endBalance)))), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        forecast.dailySpend?.let { stringResource(R.string.cash_forecast_daily, maskedAmount(formatMoney(floor(it)))) } ?: stringResource(R.string.cash_forecast_no_history),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(stringResource(R.string.cash_forecast_events), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (forecast.events.isEmpty()) {
                Text(stringResource(R.string.cash_forecast_no_events), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
            Text(stringResource(R.string.cash_forecast_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
