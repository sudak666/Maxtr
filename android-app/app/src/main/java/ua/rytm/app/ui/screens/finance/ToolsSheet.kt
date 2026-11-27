package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import ua.rytm.app.ui.components.fadingEdges
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.components.RytmModalBottomSheet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import ua.rytm.app.ui.icons.PieChart
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
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.ui.theme.tabularNums
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import java.time.format.TextStyle
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.SEED_RATES
import ua.rytm.app.ui.components.CurrencyBadge
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.motionProgress
import kotlin.math.max
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
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
/** «Валюти» (was «Інструменти»): exchange rates and the converter. Analytics has its own tab now. */
@Composable
fun ToolsSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: ToolsViewModel = viewModel(factory = ToolsViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.lg),
        ) {
            RytmSheetTitle(stringResource(R.string.tools_title))
            FxRatesSection(viewModel)
            ConverterSection(viewModel)
        }
    }
}

/**
 * The Analytics tab (design audit 2026-11-26: it used to be the top of the
 * «Інструменти» sheet, reachable only through a wrench tile). Month summary,
 * the period's totals and categories, and the six-month chart.
 */
@Composable
fun AnalyticsScreen(financeViewModel: FinanceViewModel, onOpenFinance: () -> Unit) {
    val app = ua.rytm.app.ui.rytmApp()
    val canEdit = ua.rytm.app.ui.LocalCanEditProfile.current
    val viewModel: ToolsViewModel = viewModel(factory = ToolsViewModel.factory(app.financeRepository))
    ua.rytm.app.ui.components.ProvidePrimaryAction(stringResource(R.string.transaction_new_title), enabled = canEdit) {
        app.pendingLaunchAction.value = ua.rytm.app.MainActivity.ACTION_NEW_TRANSACTION
    }
    MonthSummaryHost(financeViewModel)
    var detail by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null) }
    detail?.let { (typeName, category) ->
        CategoryStatsSheet(
            vm = viewModel,
            type = TxType.valueOf(typeName),
            category = category,
            onDismiss = { detail = null },
            onShowTransactions = {
                val (from, to) = viewModel.periodRange()
                detail = null
                app.pendingHistoryJump.value = HistoryJump(TxType.valueOf(typeName), category, from, to)
                onOpenFinance()
            },
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = RytmSpacing.lg, end = RytmSpacing.lg, top = RytmSpacing.lg, bottom = ua.rytm.app.ui.theme.RytmDimens.BottomContentClearance),
        verticalArrangement = Arrangement.spacedBy(RytmSpacing.lg),
    ) {
        // The month summary is a compact tonal pill beside the title (outside
        // review 2026-11-27: a full-width outlined button above the period
        // chips competed with them for the first look). Opens right here.
        Row(verticalAlignment = Alignment.CenterVertically) {
            ua.rytm.app.ui.components.RytmScreenTitle(stringResource(R.string.nav_analytics), Modifier.weight(1f))
            androidx.compose.material3.FilledTonalButton(
                onClick = { financeViewModel.openMonthSummary() },
                modifier = Modifier.heightIn(min = ua.rytm.app.ui.theme.RytmDimens.TouchTarget).padding(top = RytmSpacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = RytmSpacing.md),
            ) {
                androidx.compose.material3.Icon(ua.rytm.app.ui.icons.RytmIcons.PieChart, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.month_summary_tool_short))
            }
        }
        AnalyticsSection(viewModel) { type, category -> detail = type.name to category }
        SixMonthChartSection(viewModel)
    }
}

@Composable
private fun AnalyticsSection(vm: ToolsViewModel, onCategory: (TxType, String) -> Unit) {
    val periodLabels = mapOf(AnalyticsPeriod.MONTH to stringResource(R.string.period_month), AnalyticsPeriod.PREV to stringResource(R.string.period_previous), AnalyticsPeriod.M3 to stringResource(R.string.period_three_months), AnalyticsPeriod.ALL to stringResource(R.string.period_all))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.analytics_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        val periodRowState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyRow(state = periodRowState, modifier = Modifier.fadingEdges(periodRowState), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
            items(AnalyticsPeriod.entries.toList()) { p ->
                ua.rytm.app.ui.components.RytmFilterChip(selected = vm.period == p, onClick = { vm.onPeriodChange(p) }, label = { Text(periodLabels.getValue(p)) })
            }
        }

        AnalyticsSummary(vm.totalIncome, vm.totalExpense, vm.difference, vm.savingsRate)

        val expenseChange = vm.expenseChangePercent
        val categoryGrowth = vm.topExpenseGrowth
        if (expenseChange != null || categoryGrowth != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row)) {
                // One insight per line, each led by its own trend glyph: two
                // whole sentences in green and red read as one block (outside
                // review, 2026-11-27). The colour sits on the glyph, the text stays plain.
                Column(Modifier.fillMaxWidth().padding(RytmSpacing.md), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    expenseChange?.let {
                        // Same ±5% band as the PWA: a 3% swing is noise, not a trend.
                        val text = spendingChangeText(it, vm.period)
                        when {
                            it <= -SAME_PACE_BAND -> InsightLine(ua.rytm.app.ui.icons.RytmIcons.TrendingDown, ua.rytm.app.ui.theme.RytmSemantic.income, text)
                            it >= SAME_PACE_BAND -> InsightLine(ua.rytm.app.ui.icons.RytmIcons.TrendingUp, ua.rytm.app.ui.theme.RytmSemantic.expense, text)
                            else -> InsightLine(null, MaterialTheme.colorScheme.onSurfaceVariant, text)
                        }
                    }
                    categoryGrowth?.let { growth ->
                        InsightLine(
                            ua.rytm.app.ui.icons.RytmIcons.TrendingUp,
                            ua.rytm.app.ui.theme.RytmSemantic.expense,
                            stringResource(R.string.analytics_top_growth, localizedDomainText(growth.category), maskedAmount(formatMoney(growth.increase)), formatPercent(growth.percent)),
                        )
                    }
                }
            }
        }

        val expenseByCategory = vm.expenseByCategory
        // The donut's slices pick colours no other slice has; the rows below
        // use the same ones, so a slice and its row match at a glance.
        val sliceColors = remember(expenseByCategory) { distinctCategoryColors(donutTop(expenseByCategory).map { it.first }) }
        if (expenseByCategory.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                ExpenseDonut(expenseByCategory, vm.totalExpense, sliceColors)
            }
        }

        if (expenseByCategory.isNotEmpty()) {
            Text(stringResource(R.string.analytics_expense_categories), style = MaterialTheme.typography.labelLarge)
            Column { expenseByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalExpense, color = sliceColors[cat] ?: categoryColor(cat)) { onCategory(TxType.EXPENSE, cat) } } }
        }
        val incomeByCategory = vm.incomeByCategory
        if (incomeByCategory.isNotEmpty()) {
            val incomeColors = remember(incomeByCategory) { distinctCategoryColors(incomeByCategory.map { it.first }) }
            Text(stringResource(R.string.analytics_income_categories), style = MaterialTheme.typography.labelLarge)
            Column { incomeByCategory.forEach { (cat, amt) -> CategoryBar(cat, amt, vm.totalIncome, color = incomeColors.getValue(cat)) { onCategory(TxType.INCOME, cat) } } }
        }
        if (expenseByCategory.isEmpty() && incomeByCategory.isEmpty()) {
            Text(stringResource(R.string.analytics_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The period in three lines, one surface (design audit 2026-11-26): four
 * tinted cards said the same thing twice («Різниця» and «Заощаджено») and
 * coded each value with a colour, an icon badge and a tile; the donut below
 * already repeats the expense total.
 */
@Composable
private fun AnalyticsSummary(income: Double, expense: Double, difference: Double, savingsRate: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Input))
            .padding(horizontal = RytmSpacing.lg, vertical = RytmSpacing.md)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
    ) {
        @Composable
        fun Line(label: String, value: String, color: Color) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.titleSmall.tabularNums(), fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
            }
        }
        @Composable fun uah(v: Double) = maskedAmount(stringResource(R.string.finance_amount_uah, formatMoney(kotlin.math.abs(v))))
        Line(stringResource(R.string.analytics_income_label), "+" + uah(income), ua.rytm.app.ui.theme.RytmSemantic.income)
        Line(stringResource(R.string.analytics_expense_label), "−" + uah(expense), MaterialTheme.colorScheme.onSurface)
        // «Заощаджено −993 ₴» is a contradiction: a month that spent more
        // than it earned is named for what it is, without a negative rate.
        if (difference < 0) {
            Line(stringResource(R.string.analytics_overspent), "−" + uah(difference), ua.rytm.app.ui.theme.RytmSemantic.expense)
        } else {
            Line(stringResource(R.string.analytics_savings_rate), uah(difference) + " · " + formatPercent(savingsRate), ua.rytm.app.ui.theme.RytmSemantic.income)
        }
    }
}

/** The donut's own slices: up to six categories, or the top five and the rest as «Інше». */
internal fun donutTop(byCategory: List<Pair<String, Double>>): List<Pair<String, Double>> =
    if (byCategory.size <= 6) byCategory else byCategory.take(5)

// Mirrors the PWA's conic-gradient donut (analytics-donut) - a Compose
// Canvas arc-sweep per category. Slices take [colors] (distinct within the
// chart, shared with the rows below); «Інше» is a neutral grey so it never
// passes for a category. A tap on a slice puts its name and sum in the
// middle and dims the rest (the Revolut/Wallet donut); a second tap, or a
// tap in the middle, goes back to the total.
@Composable
private fun ExpenseDonut(byCategory: List<Pair<String, Double>>, total: Double, colors: Map<String, Color>) {
    val progress = motionProgress(byCategory, 600)
    val otherLabel = stringResource(R.string.analytics_other)
    val otherColor = MaterialTheme.colorScheme.outline
    data class Slice(val label: String, val amount: Double, val color: Color)
    val top = donutTop(byCategory)
    val slices = top.map { (cat, amt) -> Slice(localizedDomainText(cat), amt, colors[cat] ?: categoryColor(cat)) } +
        (if (byCategory.size > top.size) listOf(Slice(otherLabel, byCategory.drop(top.size).sumOf { it.second }, otherColor)) else emptyList())
    var selected by androidx.compose.runtime.saveable.rememberSaveable(byCategory) { androidx.compose.runtime.mutableIntStateOf(-1) }
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
            slices.joinToString(", ") { sl -> sl.label + " " + formatMoney(sl.amount) + " (" + formatPercent(sharePercent(sl.amount, total)) + ")" }
        Canvas(
            Modifier
                .size(184.dp)
                .semantics { contentDescription = donutSummary }
                .pointerInput(slices.size, total) {
                    detectTapGestures { tap ->
                        val dx = tap.x - size.width / 2f
                        val dy = tap.y - size.height / 2f
                        val r = kotlin.math.sqrt(dx * dx + dy * dy)
                        val outerR = size.width / 2f
                        val innerR = outerR - 20.dp.toPx()
                        if (r < innerR - 16.dp.toPx() || total <= 0) { selected = -1; return@detectTapGestures }
                        // Angle from 12 o'clock, clockwise, like the slices are drawn.
                        val deg = ((Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()) + 90 + 360) % 360)
                        var acc = 0.0
                        val hit = slices.indexOfFirst { sl -> acc += sl.amount / total * 360; deg < acc }
                        selected = if (hit == selected) -1 else hit
                    }
                },
        ) {
            // Segments are ring pieces with softly rounded corners and an even gap
            // (owner, 2026-11-06): full round caps read as pills, and short slices
            // turned into dots.
            val ring = 20.dp.toPx()
            val outerR = size.minDimension / 2
            val innerR = outerR - ring
            val gapDeg = Math.toDegrees((3.dp.toPx() / (outerR - ring / 2)).toDouble()).toFloat()
            if (slices.isEmpty()) {
                drawCircle(trackColor, radius = outerR - ring / 2, style = Stroke(width = ring))
                return@Canvas
            }
            var startAngle = -90f
            val single = slices.size == 1
            slices.forEachIndexed { i, sl ->
                val color = if (selected >= 0 && i != selected) sl.color.copy(alpha = 0.3f) else sl.color
                val sweep = (sl.amount / total * 360).toFloat()
                val drawn = (if (single) 360f else (sweep - gapDeg).coerceAtLeast(0.8f)) * progress
                if (single && drawn >= 359.9f) {
                    // A 360° arcTo starts and ends on one point, and Android draws
                    // nothing: one category at 100 % left the chart blank.
                    drawCircle(color, radius = outerR - ring / 2, style = Stroke(width = ring))
                } else if (drawn > 0f) {
                    drawPath(
                        donutSegment(center, outerR, innerR, if (single) startAngle else startAngle + gapDeg / 2, drawn, 5.dp.toPx(), rounded = !single),
                        color,
                    )
                }
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            val sl = slices.getOrNull(selected)
            Text(
                sl?.label ?: stringResource(R.string.analytics_expense_label),
                style = MaterialTheme.typography.labelMedium,
                color = sl?.color ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                heroAmountText(maskedAmount(formatMoney(sl?.amount ?: total) + " ₴")),
                style = MaterialTheme.typography.titleMedium.tabularNums(),
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            if (sl != null) Text(formatPercent(sharePercent(sl.amount, total)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One ring piece from [startDeg] over [sweepDeg] with corners rounded by up to
 * [corner] px (less when the piece is too short or the ring too thin).
 */
private fun donutSegment(center: Offset, outerR: Float, innerR: Float, startDeg: Float, sweepDeg: Float, corner: Float, rounded: Boolean): Path {
    val path = Path()
    val outer = Rect(center.x - outerR, center.y - outerR, center.x + outerR, center.y + outerR)
    val inner = Rect(center.x - innerR, center.y - innerR, center.x + innerR, center.y + innerR)
    val end = startDeg + sweepDeg
    if (!rounded || sweepDeg >= 359.9f) {
        path.arcTo(outer, startDeg, sweepDeg, true)
        path.arcTo(inner, end, -sweepDeg, false)
        path.close()
        return path
    }
    // The corner can be no bigger than half the ring or half the inner arc.
    val innerArc = Math.toRadians(sweepDeg.toDouble()).toFloat() * innerR
    val c = minOf(corner, (outerR - innerR) / 2, innerArc / 2)
    fun at(r: Float, deg: Float): Offset {
        val a = Math.toRadians(deg.toDouble())
        return Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat())
    }
    val dOut = Math.toDegrees((c / outerR).toDouble()).toFloat()
    val dIn = Math.toDegrees((c / innerR).toDouble()).toFloat()
    path.arcTo(outer, startDeg + dOut, sweepDeg - 2 * dOut, true)
    at(outerR, end).let { k -> at(outerR - c, end).let { p -> path.quadraticTo(k.x, k.y, p.x, p.y) } }
    at(innerR + c, end).let { path.lineTo(it.x, it.y) }
    at(innerR, end).let { k -> at(innerR, end - dIn).let { p -> path.quadraticTo(k.x, k.y, p.x, p.y) } }
    path.arcTo(inner, end - dIn, -(sweepDeg - 2 * dIn), false)
    at(innerR, startDeg).let { k -> at(innerR + c, startDeg).let { p -> path.quadraticTo(k.x, k.y, p.x, p.y) } }
    at(outerR - c, startDeg).let { path.lineTo(it.x, it.y) }
    at(outerR, startDeg).let { k -> at(outerR, startDeg + dOut).let { p -> path.quadraticTo(k.x, k.y, p.x, p.y) } }
    path.close()
    return path
}

@Composable
private fun CategoryBar(category: String, amount: Double, total: Double, label: String = localizedDomainText(category), color: Color = categoryColor(category), onClick: (() -> Unit)? = null) {
    val pct = sharePercent(amount, total)
    val progress = if (total > 0) (amount / total).coerceIn(0.0, 1.0).toFloat() else 0f
    Column(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row)).clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(category = category, size = 30.dp, color = color)
            Text(
                label,
                modifier = Modifier.padding(start = RytmSpacing.sm).weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                maskedAmount(formatMoney(amount)) + " · " + formatPercent(pct),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Outside review 2026-11-27: a 3dp grey bar across the whole row read
        // as a divider between rows. Now 4dp in the slice's colour, starting
        // under the name, on a faint track.
        Box(
            Modifier.fillMaxWidth().padding(start = 30.dp + RytmSpacing.sm, top = 6.dp).height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).background(color.copy(alpha = 0.85f), CircleShape))
        }
    }
}

/** A category in the chosen analytics period: total, change and the split by subcategory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryStatsSheet(vm: ToolsViewModel, type: TxType, category: String, onDismiss: () -> Unit, onShowTransactions: (() -> Unit)?) {
    val d = vm.categoryDetail(type, category)
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
                CategoryIconBadge(category = category, size = 40.dp)
                RytmSheetTitle(localizedDomainText(category), modifier = Modifier.weight(1f))
            }
            Text(maskedAmount(formatMoney(d.total) + " ₴"), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            val prev = d.previous
            val changeText = when {
                prev == null || prev <= 0 -> stringResource(R.string.category_stats_no_compare)
                else -> {
                    val pct = Math.round((d.total - prev) / prev * 100).toInt()
                    stringResource(R.string.category_stats_vs_previous, formatPercent((if (pct > 0) "+" else if (pct < 0) "−" else "") + kotlin.math.abs(pct)), maskedAmount(formatMoney(prev) + " ₴"))
                }
            }
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.finance_records, d.count, d.count) + " · " + changeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (d.bySubcategory.size > 1 || d.bySubcategory.firstOrNull()?.first != null) {
                Text(stringResource(R.string.category_stats_by_sub), style = MaterialTheme.typography.labelLarge)
                Column {
                    val none = stringResource(R.string.subcategory_none)
                    d.bySubcategory.forEach { (sub, amt) ->
                        CategoryBar(category, amt, d.total, label = sub?.let { localizedDomainText(it) } ?: none)
                    }
                }
            }
            onShowTransactions?.let { show ->
                ua.rytm.app.ui.components.RytmOutlinedButton(
                    onClick = show,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ua.rytm.app.ui.theme.RytmDimens.TouchTarget),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.category_stats_show_tx)) }
            }
        }
    }
}

@Composable
private fun FxRatesSection(vm: ToolsViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
    Column(Modifier.fillMaxWidth().padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
        Text(stringResource(R.string.rates_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val rates = vm.currencyRates.ifEmpty { SEED_RATES }
        if (rates.isEmpty()) {
            Text(stringResource(R.string.rates_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rates.toList().filter { it.first in ua.rytm.app.data.MAIN_CURRENCIES }.sortedBy { (code, _) -> listOf("USD", "EUR", "GBP", "PLN").indexOf(code).let { if (it < 0) Int.MAX_VALUE else it } }.forEach { (code, rate) ->
                Row(Modifier.fillMaxWidth().padding(vertical = RytmSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    CurrencyBadge(code, size = 34.dp)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        // Locale read outside runCatching: a composable read inside it is illegal (Kotlin 2.4 check).
                        val locale = LocalConfiguration.current.locales[0]
                        Text(runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrDefault(code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(Modifier.fillMaxWidth().padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
        Text(stringResource(R.string.converter_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            // The result is an answer, not a field: a label and a large number,
            // no filled box (a grey box read as a disabled input).
            Column(Modifier.weight(1f).heightIn(min = 56.dp).padding(start = RytmSpacing.xs), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.converter_result), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(maskedAmount(formatMoney(vm.converterResult)), style = MaterialTheme.typography.headlineSmall.tabularNums(), fontWeight = FontWeight.Bold, maxLines = 1)
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
    Column(Modifier.fillMaxWidth().padding(RytmSpacing.lg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.analytics_six_months), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val months = vm.sixMonthTotals
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("balance" to R.string.analytics_difference, "income" to R.string.analytics_income_label, "expense" to R.string.analytics_expense_label).forEach { (key, labelRes) ->
                ua.rytm.app.ui.components.RytmFilterChip(
                    selected = metric == key,
                    onClick = { metric = key },
                    // FilterChip's label isn't centered within extra width
                    // on its own -- barely visible at the chip's natural
                    // size, but this row stretches all 3 chips equally via
                    // weight(1f), which turned that into a real, visible
                    // left-shift (flagged live, screenshot: "Дохід" sitting
                    // noticeably off-center in its own pill).
                    label = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(labelRes)) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val values = months.map { when (metric) { "income" -> it.income; "expense" -> it.expense; else -> it.income - it.expense } }
        val minVal = values.minOrNull() ?: 0.0
        val maxVal = values.maxOrNull() ?: 1.0
        val span = (maxVal - minVal).takeIf { it > 0 } ?: 1.0
        val lineColor = MaterialTheme.colorScheme.primary
        val pointFill = MaterialTheme.colorScheme.surface
        val zeroColor = MaterialTheme.colorScheme.outline
        val progress = motionProgress(months, 500)
        val chartLocale = LocalConfiguration.current.locales[0]
        val lineSummary = stringResource(R.string.analytics_six_months) + ": " + months.mapIndexed { i, m ->
            m.yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, chartLocale) + " " + formatMoney(values[i])
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
            // Zero line when the months cross it: the fill alone made a
            // month with a deficit look like a small positive one.
            if (minVal < 0 && maxVal > 0) {
                val zeroY = size.height - ((0 - minVal) / span).toFloat() * (size.height - 12.dp.toPx()) - 6.dp.toPx()
                drawLine(
                    zeroColor,
                    Offset(0f, zeroY),
                    Offset(size.width, zeroY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
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

/**
 * «Витрати на 27 % менші, ніж за ці дні жовтня»: one wording for the change
 * against the previous period, shared by Analytics and the month summary (the
 * home screen says the same in a shorter form). Outside review, 2026-11-27:
 * the same fact read «ніж у попередньому періоді» on one screen and «ніж
 * місяцем раніше» on another, and neither said it compared the same days.
 */
@Composable
internal fun spendingVsMonthText(pct: Int, previous: java.time.YearMonth, partial: Boolean): String {
    val amount = formatPercent(kotlin.math.abs(pct))
    return if (partial) {
        val month = monthGenitive(previous)
        when {
            pct <= -SAME_PACE_BAND -> stringResource(R.string.spending_less_days, amount, month)
            pct >= SAME_PACE_BAND -> stringResource(R.string.spending_more_days, amount, month)
            else -> stringResource(R.string.spending_same_days, month)
        }
    } else {
        when {
            pct <= -SAME_PACE_BAND -> stringResource(R.string.spending_less_month, amount, monthForLabel(previous))
            pct >= SAME_PACE_BAND -> stringResource(R.string.spending_more_month, amount, monthForLabel(previous))
            else -> stringResource(R.string.spending_same_month, monthGenitive(previous))
        }
    }
}

/** [spendingVsMonthText] for the chosen Analytics period. */
@Composable
private fun spendingChangeText(pct: Int, period: AnalyticsPeriod): String {
    val now = ua.rytm.app.data.FinancialMonth.of(java.time.LocalDate.now())
    return when (period) {
        AnalyticsPeriod.MONTH -> spendingVsMonthText(pct, now.minusMonths(1), partial = true)
        AnalyticsPeriod.PREV -> spendingVsMonthText(pct, now.minusMonths(2), partial = false)
        AnalyticsPeriod.M3, AnalyticsPeriod.ALL -> {
            val amount = formatPercent(kotlin.math.abs(pct))
            when {
                pct <= -SAME_PACE_BAND -> stringResource(R.string.spending_less_3m, amount)
                pct >= SAME_PACE_BAND -> stringResource(R.string.spending_more_3m, amount)
                else -> stringResource(R.string.spending_same_3m)
            }
        }
    }
}

@Composable
private fun InsightLine(icon: androidx.compose.ui.graphics.vector.ImageVector?, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(RytmSpacing.sm))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A category's share of the total as shown: rounded, not cut (1 517 of
 * 50 800 is 2,99 %, it showed «2%»), and «<1» for a real but small share
 * instead of a «0%» that reads as nothing spent.
 */
internal fun sharePercent(amount: Double, total: Double): String {
    if (total <= 0 || amount <= 0) return "0"
    val pct = amount / total * 100
    return if (pct < 0.5) "<1" else Math.round(pct).toString()
}
