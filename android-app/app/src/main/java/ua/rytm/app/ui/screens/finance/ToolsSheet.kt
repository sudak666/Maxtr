package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import java.time.format.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.LocalHideAmounts
import ua.rytm.app.ui.motionProgress
import kotlin.math.max

// Mirrors js/index.html's #tools-modal — Analytics (donut + category
// breakdown + period filter), FX rates, currency converter, 6-month chart —
// see ToolsViewModel's own doc comment for exactly which data each reuses.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: ToolsViewModel = viewModel(factory = ToolsViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            AnalyticsSection(viewModel)
            HorizontalDivider()
            FxRatesSection(viewModel)
            HorizontalDivider()
            ConverterSection(viewModel)
            HorizontalDivider()
            SixMonthChartSection(viewModel)
        }
    }
}

@Composable
private fun AnalyticsSection(vm: ToolsViewModel) {
    val periodLabels = mapOf(AnalyticsPeriod.MONTH to stringResource(R.string.period_month), AnalyticsPeriod.PREV to stringResource(R.string.period_previous), AnalyticsPeriod.M3 to stringResource(R.string.period_three_months), AnalyticsPeriod.ALL to stringResource(R.string.period_all))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsPeriod.entries.forEach { p ->
                FilterChip(selected = vm.period == p, onClick = { vm.onPeriodChange(p) }, label = { Text(periodLabels.getValue(p)) })
            }
        }

        val net = vm.totalIncome - vm.totalExpense
        val savingsRate = if (vm.totalIncome > 0) (net / vm.totalIncome * 100).toInt() else 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(stringResource(R.string.analytics_income_label), formatMoneyWithCurrency(vm.totalIncome, "UAH"), true, Modifier.weight(1f))
            AnalyticsStatCard(stringResource(R.string.analytics_expense_label), formatMoneyWithCurrency(vm.totalExpense, "UAH"), false, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsStatCard(stringResource(R.string.analytics_net), formatMoneyWithCurrency(net, "UAH"), net >= 0, Modifier.weight(1f))
            AnalyticsStatCard(stringResource(R.string.analytics_savings_rate), "$savingsRate%", savingsRate >= 0, Modifier.weight(1f))
        }

        val expenseByCategory = vm.expenseByCategory
        if (expenseByCategory.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ExpenseDonut(expenseByCategory, vm.totalExpense)
            }
        }

        if (expenseByCategory.isNotEmpty()) {
            Text(stringResource(R.string.analytics_expense_categories), style = MaterialTheme.typography.labelLarge)
            expenseByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalExpense) }
        }
        val incomeByCategory = vm.incomeByCategory
        if (incomeByCategory.isNotEmpty()) {
            Text(stringResource(R.string.analytics_income_categories), style = MaterialTheme.typography.labelLarge)
            incomeByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalIncome) }
        }
        if (expenseByCategory.isEmpty() && incomeByCategory.isEmpty()) {
            Text(stringResource(R.string.analytics_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Mirrors the PWA's conic-gradient donut (analytics-donut) — a Compose
// Canvas arc-sweep per category, colored via categoryColor() (the same
// deterministic per-category color used for transaction-list icon badges).
@Composable
private fun ExpenseDonut(byCategory: List<Pair<String, Double>>, total: Double) {
    val progress = motionProgress(byCategory, 600)
    val topCategory = byCategory.firstOrNull()
    val chartDescription = stringResource(
        R.string.analytics_donut_accessibility,
        maskedAmount(formatMoneyWithCurrency(total, "UAH")),
        topCategory?.let { localizedDomainText(it.first) }.orEmpty(),
        topCategory?.let { maskedAmount(formatMoneyWithCurrency(it.second, "UAH")) }.orEmpty(),
    )
    Box(
        Modifier.size(170.dp).graphicsLayer {
            alpha = progress
            scaleX = 0.85f + 0.15f * progress
            scaleY = 0.85f + 0.15f * progress
            rotationZ = -8f * (1f - progress)
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(170.dp).semantics { contentDescription = chartDescription }) {
            var startAngle = -90f
            val stroke = size.minDimension * 0.22f
            byCategory.forEach { (cat, amt) ->
                val sweep = (amt / total * 360).toFloat()
                drawArc(
                    color = categoryColor(cat),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = stroke),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(maskedAmount(formatMoneyWithCurrency(total, "UAH")), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.analytics_sum), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsStatCard(label: String, value: String, positive: Boolean, modifier: Modifier = Modifier) {
    val tint = if (positive) ua.rytm.app.ui.theme.GreenDark2 else ua.rytm.app.ui.theme.RedDark2
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(42.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (positive) Icons.Filled.TrendingUp else Icons.Filled.ShoppingCart, contentDescription = null, tint = Color.White)
            }
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(maskedAmount(value), style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryBar(category: String, amount: Double, total: Double) {
    val pct = if (total > 0) (amount / total * 100).toInt() else 0
    val color = categoryColor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CategoryIconBadge(category, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Text(localizedDomainText(category), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(maskedAmount(formatMoneyWithCurrency(amount, "UAH")) + " · $pct%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

@Composable
private fun FxRatesSection(vm: ToolsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.rates_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val rates = vm.currencyRates.ifEmpty { SEED_RATES }
        if (rates.isEmpty()) {
            Text(stringResource(R.string.rates_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rates.forEach { (code, rate) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 $code", style = MaterialTheme.typography.bodyMedium)
                    Text(maskedAmount(formatMoneyWithCurrency(rate, "UAH")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConverterSection(vm: ToolsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = vm.converterAmount,
                onValueChange = vm::onConverterAmountChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.amount_label)) },
            )
            CurrencyDropdown(vm.availableCurrencies, vm.converterFrom, vm::onConverterFromChange, Modifier.weight(1f))
            IconButton(onClick = vm::swapConverter, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) { Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.converter_swap)) }
        }
        CurrencyDropdown(vm.availableCurrencies, vm.converterTo, vm::onConverterToChange, Modifier.fillMaxWidth())
        Text(
            maskedAmount("1 ${vm.converterFrom} = ${formatMoneyWithCurrency(vm.converterResult, vm.converterTo)}"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { onSelect(code); expanded = false })
            }
        }
    }
}

@Composable
private fun SixMonthChartSection(vm: ToolsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.finance_chart_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val months = vm.sixMonthTotals
        val labels = mapOf(FinanceChartSeries.NET to stringResource(R.string.finance_chart_net), FinanceChartSeries.INCOME to stringResource(R.string.finance_chart_income), FinanceChartSeries.EXPENSE to stringResource(R.string.finance_chart_expense))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FinanceChartSeries.entries.forEach { series ->
                FilterChip(vm.chartSeries == series, { vm.onChartSeriesChange(series) }, { Text(labels.getValue(series)) }, modifier = Modifier.weight(1f))
            }
        }
        val values = months.map { when (vm.chartSeries) { FinanceChartSeries.NET -> it.income - it.expense; FinanceChartSeries.INCOME -> it.income; FinanceChartSeries.EXPENSE -> it.expense } }
        val deltas = values.zipWithNext { a, b -> b - a }.takeLast(3)
        val forecast = values.lastOrNull()?.plus(deltas.average().takeUnless { it.isNaN() } ?: 0.0) ?: 0.0
        val chartValues = values + forecast
        val minVal = minOf(0.0, chartValues.minOrNull() ?: 0.0)
        val maxVal = maxOf(1.0, chartValues.maxOrNull() ?: 0.0)
        val range = maxVal - minVal
        val current = values.lastOrNull() ?: 0.0
        val previous = values.getOrNull(values.lastIndex - 1) ?: 0.0
        val trend = if (previous != 0.0) (((current - previous) / kotlin.math.abs(previous)) * 100).toInt() else 0
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(maskedAmount(formatMoneyWithCurrency(current, "UAH")), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (previous != 0.0) Text((if (trend >= 0) "↑ " else "↓ ") + kotlin.math.abs(trend) + "%", color = if (trend >= 0) ua.rytm.app.ui.theme.GreenDark2 else ua.rytm.app.ui.theme.RedDark2, fontWeight = FontWeight.Bold)
        }
        val progress = motionProgress(months, 500)
        val lineColor = MaterialTheme.colorScheme.primary
        val chartSurface = MaterialTheme.colorScheme.surface
        val locale = LocalConfiguration.current.locales[0]
        val hideAmounts = LocalHideAmounts.current
        val accessibleValues = months.zip(values).joinToString("; ") { (month, value) ->
            "${month.yearMonth.month.getDisplayName(TextStyle.SHORT, locale)} ${if (hideAmounts) "••••" else formatMoneyWithCurrency(value, "UAH", locale)}"
        }
        val chartDescription = stringResource(
            R.string.finance_chart_accessibility,
            labels.getValue(vm.chartSeries),
            maskedAmount(formatMoneyWithCurrency(current, "UAH", locale)),
            maskedAmount(formatMoneyWithCurrency(forecast, "UAH", locale)),
        ) + ". " + accessibleValues
        Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = chartDescription }) {
            val step = size.width / (chartValues.size - 1)
            val points = chartValues.mapIndexed { i, value -> Offset(i * step, size.height - (((value - minVal) / range).toFloat() * size.height * progress)) }
            val area = Path().apply { moveTo(points.first().x, size.height); points.take(values.size).forEach { lineTo(it.x, it.y) }; lineTo(points[values.lastIndex].x, size.height); close() }
            drawPath(area, lineColor.copy(alpha = .16f))
            points.take(values.size).zipWithNext().forEach { (a, b) -> drawLine(lineColor, a, b, 6f) }
            drawLine(lineColor.copy(alpha = .7f), points[values.lastIndex], points.last(), 6f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            points.forEachIndexed { i, point -> drawCircle(if (i == points.lastIndex) chartSurface else lineColor, 8f, point); if (i == points.lastIndex) drawCircle(lineColor, 8f, point, style = Stroke(4f)) }
            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            months.forEach { m -> Text(m.yearMonth.month.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall) }
            Text(stringResource(R.string.finance_chart_forecast), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        val average = values.average().takeUnless { it.isNaN() } ?: 0.0
        Text(stringResource(R.string.finance_chart_average, formatMoneyWithCurrency(average, "UAH", locale)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
}
