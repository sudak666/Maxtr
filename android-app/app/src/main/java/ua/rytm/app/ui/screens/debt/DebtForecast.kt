package ua.rytm.app.ui.screens.debt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.ui.motionProgress
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.R
import ua.rytm.app.ui.screens.finance.formatMoney
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.LocalHideAmounts
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Payoff forecast: mirrors js/debt.js's renderDebtForecast()/DebtBurndownChart —
// a balance-burndown mini chart + "≈ N payments left at this pace" estimate,
// purely derived from existing entries (no new inputs/schema change). Shown
// only when there's enough signal: startAmount set, ≥2 payments (same guard
// as the PWA). The PWA draws this as inline SVG via Preact (a proof-of-concept
// the PWA itself calls out, see CLAUDE.md's "Preact adoption" note) — here it's
// a native Compose Canvas, matching this port's convention of using the
// platform's own primitive rather than porting a browser-specific workaround
// (see ANDROID_MIGRATION.md §3's improvement list).
@Composable
fun DebtForecastCard(debt: Debt) {
    val start = debt.startAmount
    val entries = debt.entries
    if (start <= 0 || entries.size < 2) return

    val series = listOf(start) + entries.map { it.balance }
    val currentBalance = debt.currentBalance()

    // Average paydown per payment, counting only payments that actually reduced
    // the balance — a correction that raised it isn't "progress" (1:1 with the PWA).
    var prev = start
    var totalDown = 0.0
    var downCount = 0
    entries.forEach { e ->
        val d = prev - e.balance
        if (d > 0) { totalDown += d; downCount++ }
        prev = e.balance
    }
    val avgDown = if (downCount > 0) totalDown / downCount else 0.0

    // Matches the PWA's .debt-forecast: an uppercase icon+label header, same
    // shape/label treatment as Shifts' IncomeChartSection (step 39).
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(15.dp))
                Text(
                    stringResource(R.string.debt_forecast_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
            val hideAmounts = LocalHideAmounts.current
            val chartDescription = stringResource(
                R.string.debt_chart_accessibility,
                maskedAmount("${formatMoney(start)} ${debt.currency}"),
                maskedAmount("${formatMoney(currentBalance)} ${debt.currency}"),
            ) + ". " + series.joinToString("; ") { if (hideAmounts) "••••" else "${formatMoney(it)} ${debt.currency}" }
            DebtBurndownCanvas(series, modifier = Modifier.fillMaxWidth().height(76.dp).padding(top = 12.dp, bottom = 8.dp).semantics { contentDescription = chartDescription })
            when {
                currentBalance <= 0 -> Text(stringResource(R.string.debt_paid_off), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                avgDown <= 0 -> Text(stringResource(R.string.debt_forecast_insufficient), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val paymentsLeft = max(1, ceil(currentBalance / avgDown).roundToInt())
                    val avgStr = maskedAmount("${formatMoney(avgDown.roundToInt().toDouble())} ${debt.currency}")
                    Text(pluralStringResource(R.plurals.debt_payments_left, paymentsLeft, paymentsLeft), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.debt_average_payment, avgStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DebtBurndownCanvas(series: List<Double>, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.34f)
    val transparent = lineColor.copy(alpha = 0f)
    val progress = motionProgress(series, 500)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val maxV = max(series.max(), 1.0)
        val minV = min(series.min(), 0.0)
        val span = (maxV - minV).let { if (it == 0.0) 1.0 else it }

        fun xAt(i: Int) = (w * i / (series.size - 1))
        fun yAt(v: Double): Float {
            val target = (h * (1 - (v - minV) / span)).toFloat()
            return h + (target - h) * progress
        }

        val points = series.mapIndexed { i, v -> Offset(xAt(i), yAt(v)) }

        val areaPath = Path().apply {
            moveTo(points.first().x, h)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, h)
            close()
        }
        drawPath(areaPath, brush = Brush.verticalGradient(listOf(fillColor, transparent)))

        val linePath = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        drawCircle(color = lineColor, radius = 8f, center = points.last())
    }
}
