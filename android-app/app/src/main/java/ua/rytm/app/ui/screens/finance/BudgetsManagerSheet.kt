package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.components.RytmModalBottomSheet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import ua.rytm.app.ui.icons.Add
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.BudgetsSyncRepository
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.ExpandMore
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.Edit

// Mirrors js/settings-managers.js's budgets-modal — see BudgetsManagerViewModel's
// doc comment for scope (expense categories only).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsManagerSheet(
    repository: FinanceRepository,
    syncRepository: BudgetsSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: BudgetsManagerViewModel = viewModel(key = "budgets-$uid-$profileId", factory = BudgetsManagerViewModel.factory(repository, syncRepository, uid, profileId)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.md),
        ) {
            RytmSheetTitle(stringResource(R.string.budgets_title), subtitle = stringResource(R.string.budgets_body))

            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            if (viewModel.rows.isEmpty()) {
                Text(stringResource(R.string.budgets_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Categories with a limit first; the rest wait behind «Додати ліміт»
            // (first-run audit 2026-11-07: a new profile opened on 17 rows of
            // «Без ліміту»). The row being edited stays visible.
            var showUnset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            fun hasLimit(category: String, limit: Double) = limit > 0 ||
                viewModel.subcategories[ua.rytm.app.data.subKey(TxType.EXPENSE.name, category)].orEmpty()
                    .any { (viewModel.budgets[ua.rytm.app.data.subBudgetKey(category, it)] ?: 0.0) > 0 }
            val unsetCount = viewModel.rows.count { (c, l) -> !hasLimit(c, l) }
            if (viewModel.rows.isNotEmpty() && unsetCount == viewModel.rows.size && !showUnset) {
                Text(stringResource(R.string.budgets_none_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            viewModel.rows.filter { (c, l) -> showUnset || hasLimit(c, l) || viewModel.expandedCategory == c }.forEach { (category, limit) ->
                BudgetRow(
                    category = category,
                    limit = limit,
                    iconOverride = viewModel.categoryIcons[category],
                    expanded = viewModel.expandedCategory == category,
                    onToggleEdit = { viewModel.toggleEdit(category) },
                    onLimitChange = { viewModel.updateBudget(category, it) },
                    subcategories = viewModel.subcategories[ua.rytm.app.data.subKey(TxType.EXPENSE.name, category)].orEmpty(),
                    subLimits = viewModel.budgets,
                    spent = viewModel.spent[category] ?: 0.0,
                    onSubLimitChange = { sub, amount -> viewModel.updateBudget(ua.rytm.app.data.subBudgetKey(category, sub), amount) },
                )
            }
            if (unsetCount > 0 && !showUnset) {
                ua.rytm.app.ui.components.RytmTextButton(onClick = { showUnset = true }) {
                    Icon(RytmIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(RytmSpacing.sm))
                    Text(stringResource(R.string.budgets_add_limit))
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(category: String, limit: Double, iconOverride: String?, expanded: Boolean, onToggleEdit: () -> Unit, onLimitChange: (Double) -> Unit, subcategories: List<String>, subLimits: Map<String, Double>, spent: Double, onSubLimitChange: (String, Double) -> Unit) {
    // What is left (or how far over) next to the spent sum, as Spendee and
    // Wallet show it (outside review 2026-11-28); the sums hide with «Приховувати суми».
    val summary = if (limit > 0) stringResource(R.string.budgets_spent_of, ua.rytm.app.ui.maskedAmount(formatMoney(spent)), ua.rytm.app.ui.maskedAmount(formatMoney(limit)))
        else stringResource(R.string.budgets_unlimited)
    // Its own line under the bar: after «4 504,54 з 5 000 ₴» on one line it
    // wrapped in the middle («залишилось / 495,46 ₴»), seen on the emulator.
    val rest = limit - spent
    val remainder = if (limit <= 0) null
        else if (rest >= 0) stringResource(R.string.budgets_left, ua.rytm.app.ui.maskedAmount(formatMoney(rest)))
        else stringResource(R.string.budgets_over, ua.rytm.app.ui.maskedAmount(formatMoney(-rest)))

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
        Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).clickable(onClickLabel = stringResource(R.string.action_edit), role = androidx.compose.ui.semantics.Role.Button, onClick = onToggleEdit), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(category, iconOverride = iconOverride, size = 32.dp)
            Spacer(Modifier.width(RytmSpacing.sm))
            Column(Modifier.weight(1f)) {
                Text(localizedDomainText(category), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (limit > 0) {
                    // Filled share of this month's limit, coloured by pace (see budgetPace).
                    val share = (spent / limit).toFloat()
                    val elapsed = remember { budgetMonthElapsed(java.time.LocalDate.now()) }
                    val barColor = when (budgetPace(spent, limit, elapsed)) {
                        BudgetPace.OVER -> MaterialTheme.colorScheme.error
                        BudgetPace.AHEAD -> ua.rytm.app.ui.theme.RytmSemantic.warning
                        BudgetPace.ON_TRACK -> MaterialTheme.colorScheme.primary
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { share.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp),
                        color = barColor,
                        // The card is surfaceContainerHighest too, so that track was invisible
                        // and an untouched limit showed no scale at all.
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        drawStopIndicator = {},
                    )
                    remainder?.let {
                        Text(
                            it.replaceFirstChar { c -> c.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rest < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = RytmSpacing.xs),
                        )
                    }
                }
            }
            Icon(if (expanded) RytmIcons.ExpandLess else RytmIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (expanded) {
            var limitText by remember(category) { mutableStateOf(if (limit == 0.0) "" else formatMoney(limit)) }
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    val parsed = ua.rytm.app.ui.screens.finance.parseMoneyInput(limitText) ?: 0.0
                    if (parsed != limit) onLimitChange(parsed)
                    onToggleEdit()
                }),
                label = { Text(stringResource(R.string.budgets_monthly_limit)) },
                // The «blank or 0» rule belongs where the limit is typed, not under the list title.
                supportingText = { Text(stringResource(R.string.budgets_limit_hint)) },
                // Used to override unfocusedBorderColor to onSurfaceVariant
                // here (default `outline` was invisible in light theme at
                // the time). Now that Theme.kt's `outline` itself is a real,
                // visible hairline tone app-wide, this override is both
                // redundant and actively inconsistent -- it made this one
                // field's border brighter than every other field's. Removed
                // to fall back to the shared default.
            )
            LaunchedEffect(limitText) {
                delay(400)
                val parsed = ua.rytm.app.ui.screens.finance.parseMoneyInput(limitText) ?: 0.0
                if (parsed != limit) onLimitChange(parsed)
            }
            subcategories.forEach { sub ->
                val current = subLimits[ua.rytm.app.data.subBudgetKey(category, sub)] ?: 0.0
                var text by remember(category, sub) { mutableStateOf(if (current == 0.0) "" else formatMoney(current)) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    label = { Text(localizedDomainText(sub)) },
                )
                LaunchedEffect(text) {
                    delay(400)
                    val parsed = ua.rytm.app.ui.screens.finance.parseMoneyInput(text) ?: 0.0
                    if (parsed != current) onSubLimitChange(sub, parsed)
                }
            }
        }
    }
    }
}
