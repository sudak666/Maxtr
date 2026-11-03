package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmFilterChip
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.theme.tabularNums
import java.time.format.DateTimeFormatter

/**
 * «Капітал», opened from the balance: how capital moved over a period, where
 * it sits now and in which currencies. The change is a sum in hryvnias; a
 * percent is added only when it means something (CapitalHistory.percentChange).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapitalSheet(vm: FinanceViewModel, onDismiss: () -> Unit) {
    var days by rememberSaveable { mutableStateOf<Long?>(30L) }
    val series = remember(days, vm.totalBalanceUah) { vm.capitalSeries(days) }
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            RytmSheetTitle(stringResource(R.string.capital_title), subtitle = stringResource(R.string.capital_subtitle))
            Text(
                maskedAmount("${formatMoney(vm.totalBalanceUah)} ₴"),
                style = MaterialTheme.typography.displaySmall.tabularNums(),
                fontWeight = FontWeight.Black,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                listOf(30L to R.string.capital_30d, 90L to R.string.capital_90d, 365L to R.string.capital_year, null to R.string.period_all).forEach { (value, label) ->
                    RytmFilterChip(selected = days == value, onClick = { days = value }, label = { Text(stringResource(label)) })
                }
            }
            if (series.size >= 2) {
                val first = series.first().uah
                val last = series.last().uah
                val delta = last - first
                val pct = CapitalHistory.percentChange(first, last)
                val sign = if (delta >= 0) "+" else "−"
                val deltaText = maskedAmount("$sign${formatMoney(kotlin.math.abs(delta))} ₴") +
                    (pct?.let { " (" + (if (it >= 0) "+" else "−") + java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(kotlin.math.abs(it)) + " %)" } ?: "")
                Text(
                    stringResource(R.string.capital_change, deltaText, series.first().date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RytmSemantic.signed(delta),
                )
                CapitalChart(series)
            } else {
                Text(stringResource(R.string.capital_no_history), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ShareCard(stringResource(R.string.capital_by_wallet), vm.capitalByWallet) { localizedDomainText(it) }
            if (vm.capitalByCurrency.size > 1) ShareCard(stringResource(R.string.capital_by_currency), vm.capitalByCurrency) { it }
            Text(stringResource(R.string.capital_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CapitalChart(series: List<CapitalHistory.Point>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = series.map { it.uah }
    val min = values.min()
    val max = values.max()
    val description = stringResource(R.string.capital_chart_description, formatMoney(min), formatMoney(max))
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(maskedAmount(formatMoney(max)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val span = (max - min).takeIf { it > 0.005 } ?: 1.0
            fun y(v: Double) = (size.height * (1 - (v - min) / span)).toFloat()
            fun x(i: Int) = size.width * i / (values.size - 1)
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f))
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height))
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(maskedAmount(formatMoney(min)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val f = DateTimeFormatter.ofPattern("dd.MM.yy")
            Text(series.first().date.format(f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(series.last().date.format(f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShareCard(title: String, shares: List<CapitalHistory.Share>, label: @Composable (String) -> String) {
    if (shares.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Input),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            shares.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label(s.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(maskedAmount("${formatMoney(s.uah)} ₴") + "  " + Math.round(s.fraction * 100) + " %", style = MaterialTheme.typography.bodyMedium.tabularNums())
                    }
                    LinearProgressIndicator(progress = { s.fraction.toFloat() }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round, drawStopIndicator = {})
                }
            }
        }
    }
}
