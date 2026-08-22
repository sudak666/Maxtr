package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.R
import java.time.format.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.ui.maskedAmount
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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
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

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AnalyticsPeriod.entries.toList()) { p ->
                FilterChip(selected = vm.period == p, onClick = { vm.onPeriodChange(p) }, label = { Text(periodLabels.getValue(p)) })
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(maskedAmount(stringResource(R.string.analytics_income, formatMoney(vm.totalIncome))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Text(maskedAmount(stringResource(R.string.analytics_expense, formatMoney(vm.totalExpense))), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
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
    Box(Modifier.size(100.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(100.dp)) {
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
        Text(maskedAmount(formatMoney(total)), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryBar(category: String, amount: Double, total: Double) {
    val pct = if (total > 0) (amount / total * 100).toInt() else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(category, style = MaterialTheme.typography.bodySmall)
        Text(maskedAmount(formatMoney(amount)) + " · $pct%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(maskedAmount("${formatMoney(rate)} UAH"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.converterAmount,
                onValueChange = vm::onConverterAmountChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.amount_label)) },
            )
            CurrencyDropdown(vm.availableCurrencies, vm.converterFrom, vm::onConverterFromChange, Modifier.weight(1f))
            IconButton(onClick = vm::swapConverter) { Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.converter_swap)) }
            CurrencyDropdown(vm.availableCurrencies, vm.converterTo, vm::onConverterToChange, Modifier.weight(1f))
        }
        Text(
            maskedAmount("${formatMoney(vm.converterResult)} ${vm.converterTo}"),
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
        Text(stringResource(R.string.analytics_six_months), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val months = vm.sixMonthTotals
        val maxVal = max(1.0, months.maxOf { max(it.income, it.expense) })
        val incomeColor = MaterialTheme.colorScheme.primary
        val expenseColor = MaterialTheme.colorScheme.error
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val barGroupWidth = size.width / months.size
            val barWidth = barGroupWidth / 3.2f
            months.forEachIndexed { i, m ->
                val groupLeft = i * barGroupWidth
                val incomeHeight = (m.income / maxVal * size.height).toFloat()
                val expenseHeight = (m.expense / maxVal * size.height).toFloat()
                drawRect(incomeColor, topLeft = Offset(groupLeft + barWidth * 0.4f, size.height - incomeHeight), size = Size(barWidth, incomeHeight))
                drawRect(expenseColor, topLeft = Offset(groupLeft + barWidth * 1.8f, size.height - expenseHeight), size = Size(barWidth, expenseHeight))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val locale = LocalConfiguration.current.locales[0]
            months.forEach { m -> Text(m.yearMonth.month.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall) }
        }
    }
}
