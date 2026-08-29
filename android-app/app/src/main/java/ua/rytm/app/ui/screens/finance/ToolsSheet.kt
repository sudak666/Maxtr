package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import java.time.format.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.motionProgress
import kotlin.math.max
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.TealDeep
import ua.rytm.app.ui.theme.BlueLight2
import ua.rytm.app.ui.theme.PurpleLight2
import ua.rytm.app.ui.theme.Amber600
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.CompareArrows
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.TrackChanges
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp

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
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RytmSheetTitle(stringResource(R.string.tools_title))

            AnalyticsSection(viewModel)
            FxRatesSection(viewModel)
            ConverterSection(viewModel)
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

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsTotalCard(
                label = stringResource(R.string.analytics_income_label),
                amount = vm.totalIncome,
                color = ua.rytm.app.ui.theme.RytmSemantic.income,
                icon = RytmIcons.TrendingUp,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AnalyticsTotalCard(
                label = stringResource(R.string.analytics_expense_label),
                amount = vm.totalExpense,
                color = ua.rytm.app.ui.theme.RytmSemantic.expense,
                icon = RytmIcons.TrendingDown,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsTotalCard(
                label = stringResource(R.string.analytics_difference), amount = vm.difference,
                color = if (vm.difference >= 0) ua.rytm.app.ui.theme.RytmSemantic.income else ua.rytm.app.ui.theme.RytmSemantic.expense,
                icon = RytmIcons.CompareArrows, modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            AnalyticsRateCard(
                label = stringResource(R.string.analytics_savings_rate), value = vm.savingsRate,
                color = if (vm.savingsRate >= 0) ua.rytm.app.ui.theme.RytmSemantic.income else ua.rytm.app.ui.theme.RytmSemantic.expense,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        val expenseChange = vm.expenseChangePercent
        val categoryGrowth = vm.topExpenseGrowth
        if (expenseChange != null || categoryGrowth != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    expenseChange?.let {
                        Text(stringResource(if (it <= 0) R.string.analytics_spending_less else R.string.analytics_spending_more, kotlin.math.abs(it)), style = MaterialTheme.typography.bodySmall, color = if (it <= 0) ua.rytm.app.ui.theme.RytmSemantic.income else ua.rytm.app.ui.theme.RytmSemantic.expense, fontWeight = FontWeight.SemiBold)
                    }
                    categoryGrowth?.let { (category, percent) ->
                        Text(stringResource(R.string.analytics_top_growth, localizedDomainText(category), percent), style = MaterialTheme.typography.bodySmall, color = ua.rytm.app.ui.theme.RytmSemantic.expense, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val expenseByCategory = vm.expenseByCategory
        if (expenseByCategory.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                ExpenseDonut(expenseByCategory, vm.totalExpense)
            }
        }

        if (expenseByCategory.isNotEmpty()) {
            Text(stringResource(R.string.analytics_expense_categories), style = MaterialTheme.typography.labelLarge)
            Column { expenseByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalExpense) } }
        }
        val incomeByCategory = vm.incomeByCategory
        if (incomeByCategory.isNotEmpty()) {
            Text(stringResource(R.string.analytics_income_categories), style = MaterialTheme.typography.labelLarge)
            Column { incomeByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalIncome) } }
        }
        if (expenseByCategory.isEmpty() && incomeByCategory.isEmpty()) {
            Text(stringResource(R.string.analytics_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AnalyticsTotalCard(label: String, amount: Double, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Input),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(26.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(
                maskedAmount(stringResource(R.string.finance_amount_uah, formatMoney(amount))),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun AnalyticsRateCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Input), border = BorderStroke(1.dp, color.copy(alpha = 0.28f)), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(26.dp).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(RytmIcons.TrackChanges, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("$value%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = color)
        }
    }
}

// Mirrors the PWA's conic-gradient donut (analytics-donut) - a Compose
// Canvas arc-sweep per category, colored via categoryColor() (the same
// deterministic per-category color used for transaction-list icon badges).
@Composable
private fun ExpenseDonut(byCategory: List<Pair<String, Double>>, total: Double) {
    val progress = motionProgress(byCategory, 600)
    val otherLabel = stringResource(R.string.analytics_other)
    val chartEntries = remember(byCategory, otherLabel) {
        if (byCategory.size <= 6) byCategory
        else byCategory.take(5) + (otherLabel to byCategory.drop(5).sumOf { it.second })
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        Modifier.size(184.dp).graphicsLayer {
            alpha = progress
            scaleX = 0.85f + 0.15f * progress
            scaleY = 0.85f + 0.15f * progress
            rotationZ = -8f * (1f - progress)
        },
        contentAlignment = Alignment.Center,
    ) {
        // Canvas charts carry no semantics of their own — spoken summary added.
        val donutSummary = stringResource(R.string.analytics_title) + ": " +
            chartEntries.joinToString(", ") { (cat, amt) ->
                cat + " " + formatMoney(amt) + " (" + (amt / total * 100).toInt() + "%)"
            }
        Canvas(Modifier.size(184.dp).semantics { contentDescription = donutSummary }) {
            var startAngle = -90f
            val stroke = 22.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
            )
            chartEntries.forEach { (cat, amt) ->
                val sweep = (amt / total * 360).toFloat()
                val gap = minOf(3f, sweep * 0.18f)
                drawArc(
                    color = categoryColor(cat),
                    startAngle = startAngle + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.6f) * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.analytics_expense_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maskedAmount(formatMoney(total)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CategoryBar(category: String, amount: Double, total: Double) {
    val pct = if (total > 0) (amount / total * 100).toInt() else 0
    val progress = if (total > 0) (amount / total).coerceIn(0.0, 1.0).toFloat() else 0f
    val color = categoryColor(category)
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(category = category, size = 30.dp)
            Text(
                localizedDomainText(category),
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                maskedAmount(formatMoney(amount)) + " · $pct%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(Modifier.fillMaxWidth().padding(top = 5.dp).height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).background(color, CircleShape))
        }
    }
}

@Composable
private fun FxRatesSection(vm: ToolsViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.rates_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val rates = vm.currencyRates.ifEmpty { SEED_RATES }
        if (rates.isEmpty()) {
            Text(stringResource(R.string.rates_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rates.toList().sortedBy { (code, _) -> listOf("USD", "EUR", "GBP", "PLN").indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }.forEach { (code, rate) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val badge = when (code) {
                        "USD" -> "$" to TealDeep
                        "EUR" -> "€" to BlueLight2
                        "GBP" -> "£" to PurpleLight2
                        "PLN" -> "zł" to Amber600
                        else -> currencySymbol(code) to MaterialTheme.colorScheme.primary
                    }
                    Box(Modifier.size(34.dp).background(badge.second.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                        Text(badge.first, color = badge.second, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(runCatching { java.util.Currency.getInstance(code).getDisplayName(LocalConfiguration.current.locales[0]) }.getOrDefault(code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(maskedAmount(stringResource(R.string.money_uah, formatMoney(rate))), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConverterSection(vm: ToolsViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.converter_from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.converterAmount,
                onValueChange = vm::onConverterAmountChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.amount_label)) },
            )
            CurrencyDropdown(vm.availableCurrencies, vm.converterFrom, vm::onConverterFromChange, Modifier.weight(0.72f))
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            IconButton(onClick = vm::swapConverter, modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)) {
                Icon(RytmIcons.SwapHoriz, contentDescription = stringResource(R.string.converter_swap), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Text(stringResource(R.string.converter_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(
                modifier = Modifier.weight(1f).height(56.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Control),
                // onSurfaceVariant, not outline -- same near-invisible-in-
                // light-theme pair already fixed for the Switch thumb, the
                // budget field border, and the Shopping checkbox.
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Box(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text(maskedAmount(formatMoney(vm.converterResult)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            CurrencyDropdown(vm.availableCurrencies, vm.converterTo, vm::onConverterToChange, Modifier.weight(0.72f))
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
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
    var metric by remember { mutableStateOf("balance") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.analytics_six_months), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val months = vm.sixMonthTotals
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("balance" to R.string.analytics_balance_label, "income" to R.string.analytics_income_label, "expense" to R.string.analytics_expense_label).forEach { (key, labelRes) ->
                FilterChip(selected = metric == key, onClick = { metric = key }, label = { Text(stringResource(labelRes)) }, modifier = Modifier.weight(1f))
            }
        }
        val values = months.map { when (metric) { "income" -> it.income; "expense" -> it.expense; else -> it.income - it.expense } }
        val minVal = values.minOrNull() ?: 0.0
        val maxVal = values.maxOrNull() ?: 1.0
        val span = (maxVal - minVal).takeIf { it > 0 } ?: 1.0
        val lineColor = MaterialTheme.colorScheme.primary
        val pointFill = MaterialTheme.colorScheme.surface
        val progress = motionProgress(months, 500)
        val chartLocale = LocalConfiguration.current.locales[0]
        val lineSummary = stringResource(R.string.analytics_six_months) + ": " + months.mapIndexed { i, m ->
            m.yearMonth.month.getDisplayName(TextStyle.FULL, chartLocale) + " " + formatMoney(values[i])
        }.joinToString(", ")
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = lineSummary },
        ) {
            val points = values.mapIndexed { i, value ->
                val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
                val targetY = size.height - ((value - minVal) / span).toFloat() * (size.height - 12.dp.toPx()) - 6.dp.toPx()
                val y = size.height - (size.height - targetY) * progress
                Offset(x, y)
            }
            if (points.isNotEmpty()) {
                val line = Path().apply { moveTo(points.first().x, points.first().y) }
                val area = Path().apply { moveTo(points.first().x, points.first().y) }
                points.drop(1).forEachIndexed { index, point ->
                    val previous = points[index]
                    val controlX = (previous.x + point.x) / 2f
                    line.cubicTo(controlX, previous.y, controlX, point.y, point.x, point.y)
                    area.cubicTo(controlX, previous.y, controlX, point.y, point.x, point.y)
                }
                area.lineTo(points.last().x, size.height)
                area.lineTo(points.first().x, size.height)
                area.close()
                drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0.02f))))
                drawPath(line, lineColor.copy(alpha = 0.14f), style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, lineColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.forEach { point ->
                    drawCircle(pointFill, radius = 6.dp.toPx(), center = point)
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = point)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val locale = LocalConfiguration.current.locales[0]
            months.forEach { m -> Text(m.yearMonth.month.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelSmall) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(maskedAmount(stringResource(R.string.analytics_average_uah, formatMoney(values.average()))), style = MaterialTheme.typography.labelSmall)
            Text(maskedAmount(stringResource(R.string.analytics_maximum_uah, formatMoney(maxVal))), style = MaterialTheme.typography.labelSmall)
        }
    }
    }
}
