package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.DatePickerField
import ua.rytm.app.ui.components.RytmFilterChip
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.Tune
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSpacing

/** Next to the history search: opens [HistoryFiltersSheet], badged with how many filters are on. */
@Composable
fun FiltersButton(activeCount: Int, onClick: () -> Unit) {
    val label = stringResource(R.string.filters_title)
    BadgedBox(badge = { if (activeCount > 0) Badge { Text(activeCount.toString()) } }) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.heightIn(min = RytmDimens.TouchTarget)) {
            Icon(RytmIcons.Tune, contentDescription = label)
        }
    }
}

/**
 * Every history filter in one place: type, wallet, category, amount range and
 * period. Edits stay local until «Застосувати», so half-set values never flash
 * the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryFiltersSheet(vm: FinanceViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val start = vm.historyFilter
    var type by rememberSaveable { mutableStateOf(vm.typeFilter) }
    var walletId by rememberSaveable { mutableStateOf(start.walletId) }
    var category by rememberSaveable { mutableStateOf(vm.categoryFilter) }
    var minText by rememberSaveable { mutableStateOf(start.minAmount?.let(::plain).orEmpty()) }
    var maxText by rememberSaveable { mutableStateOf(start.maxAmount?.let(::plain).orEmpty()) }
    var period by rememberSaveable { mutableStateOf(if (start.hasRange) null else vm.periodFilter) }
    var from by rememberSaveable { mutableStateOf(start.from.orEmpty()) }
    var to by rememberSaveable { mutableStateOf(start.to.orEmpty()) }

    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm)) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
            ) {
                RytmSheetTitle(stringResource(R.string.filters_title))

                Section(stringResource(R.string.filters_type)) {
                    listOf(
                        TxTypeFilter.ALL to R.string.filter_all,
                        TxTypeFilter.EXPENSE to R.string.tx_expense,
                        TxTypeFilter.INCOME to R.string.tx_income,
                        TxTypeFilter.TRANSFER to R.string.tx_transfer,
                    ).forEach { (value, label) ->
                        RytmFilterChip(selected = type == value, onClick = {
                            type = value
                            // A category of the other type would silently match nothing.
                            if (category != null && category !in categoriesFor(vm, value)) category = null
                        }, label = { Text(stringResource(label)) })
                    }
                }

                if (vm.wallets.size > 1) Section(stringResource(R.string.filters_wallet)) {
                    RytmFilterChip(selected = walletId == null, onClick = { walletId = null }, label = { Text(stringResource(R.string.filter_all)) })
                    vm.wallets.forEach { w ->
                        RytmFilterChip(selected = walletId == w.id, onClick = { walletId = w.id }, label = { Text(localizedDomainText(w.name)) })
                    }
                }

                val cats = categoriesFor(vm, type)
                if (type != TxTypeFilter.TRANSFER && cats.isNotEmpty()) {
                    // Owner, 2026-11-03: every category as a chip made a wall
                    // of 40 (and would be 100+). The most used come first, the
                    // rest open under «Ще N», with a search once the list is long.
                    val usage = vm.categoryUsage
                    val ranked = cats.sortedByDescending { usage[it] ?: 0 }
                    var expanded by rememberSaveable { mutableStateOf(false) }
                    var query by rememberSaveable { mutableStateOf("") }
                    val hidden = (ranked.size - CATEGORY_CHIPS_COLLAPSED).coerceAtLeast(0)
                    if (expanded && ranked.size > CATEGORY_SEARCH_FROM) {
                        Text(stringResource(R.string.filters_category), style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.filters_category_search)) },
                            leadingIcon = { Icon(RytmIcons.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Section(if (expanded && ranked.size > CATEGORY_SEARCH_FROM) null else stringResource(R.string.filters_category)) {
                        val shown = when {
                            !expanded -> ranked.take(CATEGORY_CHIPS_COLLAPSED).let { top -> if (category != null && category !in top && category in ranked) top + category!! else top }
                            query.isBlank() -> ranked
                            else -> ranked.filter { localizedDomainText(it).contains(query.trim(), ignoreCase = true) }
                        }
                        if (query.isBlank()) RytmFilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.filter_all)) })
                        shown.forEach { c ->
                            RytmFilterChip(selected = category == c, onClick = { category = c }, label = { Text(localizedDomainText(c)) })
                        }
                        if (hidden > 0) RytmFilterChip(
                            selected = false,
                            onClick = { expanded = !expanded; query = "" },
                            label = { Text(if (expanded) stringResource(R.string.filters_category_less) else stringResource(R.string.filters_category_more, hidden)) },
                        )
                    }
                }

                Text(stringResource(R.string.filters_amount), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it.filter(AmountExpression::isAllowed) },
                        label = { Text(stringResource(R.string.filters_from)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = minText.isNotBlank() && parseMoneyInput(minText) == null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it.filter(AmountExpression::isAllowed) },
                        label = { Text(stringResource(R.string.filters_to)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = maxText.isNotBlank() && parseMoneyInput(maxText) == null,
                        modifier = Modifier.weight(1f),
                    )
                }

                Section(stringResource(R.string.filters_period)) {
                    listOf(
                        PeriodFilter.ALL to R.string.period_all,
                        PeriodFilter.MONTH to R.string.period_month,
                        PeriodFilter.DAY to R.string.action_today,
                    ).forEach { (value, label) ->
                        RytmFilterChip(selected = period == value, onClick = { period = value; from = ""; to = "" }, label = { Text(stringResource(label)) })
                    }
                    RytmFilterChip(selected = period == null, onClick = { period = null }, label = { Text(stringResource(R.string.filters_custom_period)) })
                }
                if (period == null) Row(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    DatePickerField(value = from, onValueChange = { from = it }, label = stringResource(R.string.filters_from), modifier = Modifier.weight(1f))
                    DatePickerField(value = to, onValueChange = { to = it }, label = stringResource(R.string.filters_to), modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = RytmSpacing.md), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                OutlinedButton(
                    onClick = { vm.resetFilters(); vm.onPeriodFilterChange(PeriodFilter.ALL); onDismiss() },
                    modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.filters_reset)) }
                Button(
                    onClick = {
                        val lo = parseMoneyInput(minText)
                        val hi = parseMoneyInput(maxText)
                        val (a, b) = if (lo != null && hi != null && lo > hi) hi to lo else lo to hi
                        val (f, t) = from.ifBlank { null } to to.ifBlank { null }
                        val range = if (period == null && f != null && t != null && f > t) t to f else f to t
                        vm.applyFilters(type, category, HistoryFilter(walletId, a, b, range.first.takeIf { period == null }, range.second.takeIf { period == null }))
                        period?.let(vm::onPeriodFilterChange)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.filters_apply)) }
            }
        }
    }
}

/** Chips shown before «Ще N»: about two rows on a phone. */
private const val CATEGORY_CHIPS_COLLAPSED = 8
/** Above this many categories the expanded list gets a search field. */
private const val CATEGORY_SEARCH_FROM = 15

private fun plain(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun categoriesFor(vm: FinanceViewModel, type: TxTypeFilter): List<String> = when (type) {
    TxTypeFilter.INCOME -> vm.categoriesByType[TxType.INCOME].orEmpty()
    TxTypeFilter.EXPENSE -> vm.categoriesByType[TxType.EXPENSE].orEmpty()
    TxTypeFilter.TRANSFER -> emptyList()
    TxTypeFilter.ALL -> (vm.categoriesByType[TxType.EXPENSE].orEmpty() + vm.categoriesByType[TxType.INCOME].orEmpty()).distinct()
        .sortedBy { it == ua.rytm.app.data.BuiltinCategories.OTHER }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(title: String?, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm), verticalArrangement = Arrangement.spacedBy(0.dp)) { chips() }
    }
}
