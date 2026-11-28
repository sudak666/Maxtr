package ua.rytm.app.ui.screens.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmDetailScaffold
import ua.rytm.app.ui.components.formatDayMonth
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.ExpandMore
import ua.rytm.app.ui.icons.Info
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.theme.tabularNums
import kotlin.math.abs

/**
 * The verdict first, then the path as a line, the sum the user can check line
 * by line and the days that matter with the balance after each. How usual
 * spending is averaged and why shift pay is or isn't added sit under «Як це
 * рахується».
 *
 * Outside review, 2026-11-27: with a gap on the way the month-end plus was the
 * biggest line and the gap a footnote under it — for someone whose money runs
 * out tomorrow the gap is the news (PocketGuard/Copilot lead with it). Now a
 * forecast with a gap opens with the gap; one without opens with the balance.
 */
@Composable
fun CashForecastScreen(forecast: CashForecast, shiftsLeft: Double, onBack: () -> Unit) {
    RytmDetailScaffold(title = stringResource(R.string.cash_forecast_title), onBack = onBack) {
        val gapDate = forecast.shortfallDate
        val lowestDate = forecast.lowestDate
        val short = gapDate != null && lowestDate != null
        Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
            if (short) {
                Text(stringResource(R.string.cash_forecast_short_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.cash_forecast_short_head, maskedAmount(formatMoney(forecast.shortfall))),
                    style = MaterialTheme.typography.headlineLarge.tabularNums(),
                    fontWeight = FontWeight.Bold,
                    color = RytmSemantic.warning,
                )
                Text(
                    if (gapDate == lowestDate) stringResource(R.string.cash_forecast_gap, formatDayMonth(gapDate!!))
                    else stringResource(R.string.cash_forecast_gap_deepest, formatDayMonth(gapDate!!), formatDayMonth(lowestDate!!)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.cash_forecast_main, formatDayMonth(forecast.end), maskedAmount(signedMoney(forecast.endBalance))),
                    style = MaterialTheme.typography.bodyMedium.tabularNums(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.cash_forecast_subtitle, formatDayMonth(forecast.end)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    maskedAmount(signedMoney(forecast.endBalance) + "\u00A0₴"),
                    style = MaterialTheme.typography.headlineLarge.tabularNums(),
                    fontWeight = FontWeight.Bold,
                    color = if (forecast.endBalance < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurface,
                )
                Text(stringResource(R.string.cash_forecast_enough, formatDayMonth(forecast.end)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (forecast.path.size >= 2) ForecastChart(forecast)

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
                        // Masked too: the daily rate times the days is the sum itself.
                        stringResource(R.string.cash_forecast_habit_hint, maskedAmount(formatMoney(habit.perDay)), forecast.unplannedDays),
                        color = RytmSemantic.signed(-1.0),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SumLine(stringResource(R.string.cash_forecast_end, formatDayMonth(forecast.end)), signedMoney(forecast.endBalance), bold = true)
            }
        }

        Text(stringResource(R.string.cash_forecast_events), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val keyDays = forecast.keyDays
        if (keyDays.isEmpty()) Explain(stringResource(R.string.cash_forecast_no_events, formatDayMonth(forecast.end)))
        keyDays.forEach { day -> KeyDayRow(day, forecast) }

        // The method on request, on a card like the sum above it (a bare row
        // hung in the air under the list).
        var howOpen by rememberSaveable { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(RytmRadii.Input),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Input)).clickable { howOpen = !howOpen }.padding(horizontal = RytmSpacing.lg, vertical = RytmSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RytmIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.cash_forecast_how), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = RytmSpacing.sm))
                Icon(if (howOpen) RytmIcons.ExpandLess else RytmIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(howOpen) {
                Column(Modifier.padding(start = RytmSpacing.lg, end = RytmSpacing.lg, bottom = RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    val habit = forecast.habit
                    Text(stringResource(R.string.cash_forecast_habit_title), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (habit == null) {
                        Explain(stringResource(R.string.cash_forecast_no_history))
                    } else {
                        Explain(stringResource(R.string.cash_forecast_habit_explain, habit.windowDays, habit.droppedDays))
                        if (habit.topCategories.isNotEmpty()) {
                            val parts = mutableListOf<String>()
                            for ((category, sum) in habit.topCategories) parts += localizedDomainText(category) + " " + maskedAmount(formatMoney(sum) + "\u00A0₴")
                            val top = parts.joinToString(" · ")
                            Explain(stringResource(R.string.cash_forecast_habit_top, top))
                        }
                        if (keyDays.isNotEmpty()) Explain(stringResource(R.string.cash_forecast_after_hint))
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
}

/**
 * One day that matters: its planned events (or the usual spending, on a day
 * with none) and the balance after it, with what makes the day special —
 * the money runs out, the deepest point, back above zero.
 */
@Composable
private fun KeyDayRow(day: ForecastDay, forecast: CashForecast) {
    val crossing = forecast.crossings[day.date]
    val marker = when {
        crossing == true || day.date == forecast.shortfallDate -> stringResource(R.string.cash_forecast_day_gap)
        day.date == forecast.lowestDate -> stringResource(R.string.cash_forecast_day_lowest)
        crossing == false -> stringResource(R.string.cash_forecast_day_back)
        else -> null
    }
    val labels = mutableListOf<String>()
    for (event in day.events) labels += localizedDomainText(event.label)
    val title = if (labels.isNotEmpty()) labels.joinToString(" · ") else stringResource(R.string.cash_forecast_habit)
    val amount = if (day.events.isNotEmpty()) day.events.sumOf { it.amountUah } else -(forecast.habit?.perDay ?: 0.0)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = RytmSpacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            val date = formatDayMonth(day.date)
            Text(
                if (marker != null) "$date · $marker" else date,
                style = MaterialTheme.typography.labelSmall,
                color = if (marker != null && day.balance < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val sign = if (amount < 0) "−" else "+"
            Text(maskedAmount("$sign${formatMoney(abs(amount))}\u00A0₴"), style = MaterialTheme.typography.bodyMedium.tabularNums(), fontWeight = FontWeight.Bold, color = RytmSemantic.signed(amount))
            Text(
                stringResource(R.string.cash_forecast_after, maskedAmount(formatMoney(day.balance))),
                style = MaterialTheme.typography.labelSmall.tabularNums(),
                color = if (day.balance < 0) RytmSemantic.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The balance day by day as a line over a dashed zero: below zero it turns
 * the warning colour, so a gap reads before any number is.
 */
@Composable
private fun ForecastChart(forecast: CashForecast) {
    val path = forecast.path
    val accent = MaterialTheme.colorScheme.primary
    val warning = RytmSemantic.warning
    val zeroColor = MaterialTheme.colorScheme.outline
    val description = stringResource(R.string.cash_forecast_chart_desc, formatDayMonth(path.first().date), formatDayMonth(path.last().date))
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
        // The highest and (below zero) the lowest balance on the path, so the
        // line has a scale without a full axis (outside review 2026-11-28).
        val peak = path.maxOf { it.balance }
        val low = path.minOf { it.balance }
        Text(maskedAmount(formatMoney(peak) + "\u00A0₴"), style = MaterialTheme.typography.labelSmall.tabularNums(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = description },
        ) {
            val values = path.map { it.balance }
            val max = maxOf(values.max(), 0.0)
            val min = minOf(values.min(), 0.0)
            val span = (max - min).takeIf { it > 0 } ?: 1.0
            val pad = 6.dp.toPx()
            val h = size.height - 2 * pad
            fun x(i: Int) = if (values.size == 1) 0f else i * size.width / (values.size - 1)
            fun y(v: Double) = pad + ((max - v) / span * h).toFloat()
            val zeroY = y(0.0)
            drawLine(zeroColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            val line = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // One path drawn twice, clipped at zero: accent above, warning below.
            clipRect(bottom = zeroY) { drawPath(line, accent, style = stroke) }
            clipRect(top = zeroY) { drawPath(line, warning, style = stroke) }
            forecast.lowestDate?.let { d ->
                val i = path.indexOfFirst { it.date == d }
                if (i >= 0) drawCircle(warning, radius = 4.dp.toPx(), center = Offset(x(i), y(values[i])))
            }
        }
        if (low < 0) Text(maskedAmount("−" + formatMoney(-low) + "\u00A0₴"), style = MaterialTheme.typography.labelSmall.tabularNums(), color = RytmSemantic.warning)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDayMonth(path.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDayMonth(path.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(maskedAmount("$amount\u00A0₴"), style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
