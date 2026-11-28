package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import ua.rytm.app.ui.components.RytmDetailScaffold
import ua.rytm.app.ui.components.RytmFilterChip
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
 * A screen of its own since 2026-11-27: the chart is scrubbed by dragging,
 * which fought a bottom sheet's own drag-to-dismiss.
 */
@Composable
fun CapitalScreen(vm: FinanceViewModel, onBack: () -> Unit) {
    var days by rememberSaveable { mutableStateOf<Long?>(30L) }
    val series = remember(days, vm.totalBalanceUah) { vm.capitalSeries(days) }
    RytmDetailScaffold(title = stringResource(R.string.capital_title), onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
            Text(stringResource(R.string.capital_subtitle), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                heroAmountText(maskedAmount("${formatMoney(vm.totalBalanceUah)}\u00A0₴")),
                style = MaterialTheme.typography.displaySmall.tabularNums(),
                fontWeight = FontWeight.Black,
            )
        }
        // Capital is not the money to spend: say how much of it is, so the
        // number here and the forecast's «Зараз на рахунках» never look at odds.
        if (kotlin.math.abs(vm.totalBalanceUah - vm.spendableBalanceUah) >= 0.5) {
            Text(
                stringResource(R.string.capital_spendable, maskedAmount(formatMoney(vm.spendableBalanceUah))),
                style = MaterialTheme.typography.bodyMedium.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            val deltaText = maskedAmount("$sign${formatMoney(kotlin.math.abs(delta))}\u00A0₴") +
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
        // Footnotes only when they apply: today's rate matters with foreign
        // currency, the missing share only with a wallet below zero.
        if (vm.isMultiCurrency) Text(stringResource(R.string.capital_note_rates), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.wallets.any { it.inCapital && vm.walletBalance(it.id) < 0 }) Text(stringResource(R.string.capital_note_negative), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    // Touch or drag across the chart to read one day; tap the same day again to let go.
    var selected by remember(series) { mutableStateOf<Int?>(null) }
    fun indexAt(px: Float, width: Int) = ((px / width.coerceAtLeast(1)) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
    val f = DateTimeFormatter.ofPattern("dd.MM.yy")
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val pick = selected
            if (pick != null) {
                Text(
                    series[pick].date.format(f) + "  " + maskedAmount(formatMoney(values[pick]) + "\u00A0₴"),
                    style = MaterialTheme.typography.labelLarge.tabularNums(),
                    fontWeight = FontWeight.Bold,
                )
            } else {
                Text(maskedAmount(formatMoney(max)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }
                .pointerInput(series) {
                    detectTapGestures { o -> indexAt(o.x, size.width).let { i -> selected = if (selected == i) null else i } }
                }
                .pointerInput(series) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> selected = indexAt(o.x, size.width) },
                    ) { change, _ ->
                        change.consume()
                        selected = indexAt(change.position.x, size.width)
                    }
                },
        ) {
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
            selected?.let { i ->
                val px = x(i)
                drawLine(grid, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Offset(px, size.height), strokeWidth = 1.dp.toPx())
                drawCircle(line, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(px, y(values[i])))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(maskedAmount(formatMoney(min)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                        // «<1 %», not «0 %», for a wallet with a little money in it.
                        Text(maskedAmount("${formatMoney(s.uah)}\u00A0₴") + "  " + formatPercent(sharePercent(s.fraction, 1.0)), style = MaterialTheme.typography.bodyMedium.tabularNums())
                    }
                    LinearProgressIndicator(progress = { s.fraction.toFloat() }, modifier = Modifier.fillMaxWidth(), strokeCap = StrokeCap.Round, drawStopIndicator = {})
                }
            }
        }
    }
}
