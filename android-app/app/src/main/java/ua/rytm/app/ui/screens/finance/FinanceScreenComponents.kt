package ua.rytm.app.ui.screens.finance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.data.TransactionSyncState
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.motionAwareSpec
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmInteraction
import ua.rytm.app.ui.theme.RytmRadii

@Composable
internal fun HeroBalanceCard(vm: FinanceViewModel) {
    // Matches the PWA's .hero-balance: a subtle bg1→bg2 diagonal gradient
    // plus a soft brand-purple glow shadow (--surface-hero/--shadow-raised),
    // Use the shared gradient hero surface rather than a flat card.
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant))),
    ) {
        Column(Modifier.padding(horizontal = RytmDimens.HeroHorizontal, vertical = RytmDimens.HeroVertical)) {
            Text(
                text = stringResource(if (vm.isMultiCurrency) R.string.finance_estimated_balance else R.string.finance_total_balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = maskedAmount((if (vm.isMultiCurrency) "≈ " else "") + formatMoneyWithCurrency(vm.totalBalanceUah, "UAH")),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
            )

            val net = vm.monthIncomeUah - vm.monthExpenseUah
            val trendColor = when {
                net > 0 -> GreenDarkLike
                net < 0 -> RedLike
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(
                    imageVector = if (net < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.padding(2.dp))
                val sign = if (net > 0) "+" else if (net < 0) "−" else ""
                Text(
                    text = maskedAmount(stringResource(R.string.finance_month_net, sign + formatMoneyWithCurrency(kotlin.math.abs(net), "UAH"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (vm.isMultiCurrency) {
                Text(
                    text = stringResource(R.string.finance_conversion_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCard(label = stringResource(R.string.finance_month_income), value = vm.monthIncomeUah, positive = true, modifier = Modifier.weight(1f))
                MiniStatCard(label = stringResource(R.string.finance_month_expense), value = vm.monthExpenseUah, positive = false, modifier = Modifier.weight(1f))
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.wallets, key = { it.id }) { wallet ->
                    WalletChip(wallet = wallet, balance = vm.walletBalance(wallet.id))
                }
            }
        }
    }
}
// MaterialTheme's own tertiary is the brand purple, not a semantic
// green/red — the PWA's trend chip needs real semantic income/expense
// colors (--green2/--red2), so these pull directly from ui/theme/Color.kt
// rather than borrowing a mismatched theme role.
internal val GreenDarkLike @Composable get() = ua.rytm.app.ui.theme.GreenDark2
private val RedLike @Composable get() = ua.rytm.app.ui.theme.RedDark2

@Composable
internal fun MiniStatCard(label: String, value: Double, positive: Boolean, modifier: Modifier = Modifier) {
    // Matches the PWA's .fin-mini-stat.income/.expense: a tinted
    // green/red gradient wash + matching border, not a neutral surface —
    // Preserve the compact PWA summary-card treatment.
    val tint = if (positive) GreenDarkLike else RedLike
    val shape = RoundedCornerShape(RytmRadii.Input)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.03f))))
            .border(1.dp, tint.copy(alpha = 0.28f), shape)
            .padding(12.dp),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = maskedAmount(formatSignedMoneyWithCurrency(if (positive) value else -value, "UAH", showPlus = true)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

@Composable
internal fun WalletChip(wallet: Wallet, balance: Double) {
    Card(shape = RoundedCornerShape(RytmRadii.Pill), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Solid color dot for the wallet, matching .wallet-chip-dot.
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(wallet.colorHex)))
            Spacer(Modifier.padding(4.dp))
            Text(localizedDomainText(wallet.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.padding(4.dp))
            Text(
                maskedAmount(formatMoneyWithCurrency(balance, wallet.currency)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun QuickActionsRow(canEdit: Boolean, onNewTransaction: () -> Unit, onTools: () -> Unit, onBudgets: () -> Unit, onGoals: () -> Unit) {
    data class QuickAction(val label: String, val icon: ImageVector, val primary: Boolean, val onClick: () -> Unit)

    val actions = listOf(
        QuickAction(stringResource(R.string.finance_action_transaction), Icons.Filled.Add, primary = true, onClick = onNewTransaction),
        QuickAction(stringResource(R.string.tools_title), Icons.Filled.Build, primary = false, onClick = onTools),
        QuickAction(stringResource(R.string.budgets_title), Icons.Filled.PieChart, primary = false, onClick = onBudgets),
        QuickAction(stringResource(R.string.goals_title), Icons.Filled.Flag, primary = false, onClick = onGoals),
    ).filterIndexed { index, _ -> canEdit || index == 1 }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) RytmInteraction.ButtonPressedScale else 1f,
                animationSpec = motionAwareSpec(tween(100)),
                label = "quick-action-press",
            )
            // Matches the PWA's .quick-action: a plain neutral card with a
            // circular tinted icon badge inside (.quick-action-icon), not a
            // Tint the full card surface consistently with its metric.
            Card(
                onClick = action.onClick,
                modifier = Modifier.weight(1f).heightIn(min = RytmDimens.QuickActionMinHeight).graphicsLayer { scaleX = scale; scaleY = scale },
                shape = RoundedCornerShape(RytmRadii.Row),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                interactionSource = interactionSource,
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(RytmDimens.QuickActionIcon)
                            .clip(CircleShape)
                            .background(
                                if (action.primary) {
                                    Brush.linearGradient(listOf(ua.rytm.app.ui.theme.GreenDark, ua.rytm.app.ui.theme.GreenLight2))
                                } else {
                                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)))
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = if (action.primary) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.padding(2.dp))
                    Text(action.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun HistoryHeader(vm: FinanceViewModel, resultCount: Int, onBulkEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (vm.selectedTransactionIds.isEmpty()) {
            Text(stringResource(R.string.finance_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Column(horizontalAlignment = Alignment.End) {
                Text(pluralStringResource(R.plurals.finance_records, resultCount, resultCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val errors = vm.transactionSyncStates.values.count { it == TransactionSyncState.ERROR }
                val operationErrors = errors + if (vm.financeSnapshotSyncState == TransactionSyncState.ERROR) 1 else 0
                val pending = vm.transactionSyncStates.size + if (vm.financeSnapshotSyncState == TransactionSyncState.PENDING) 1 else 0
                val status = when {
                    operationErrors > 0 -> stringResource(R.string.sync_operations_error, operationErrors)
                    pending > 0 -> stringResource(R.string.sync_operations_pending, pending)
                    else -> stringResource(R.string.sync_operations_synced)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (operationErrors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(pluralStringResource(R.plurals.transaction_selected_count, vm.selectedTransactionIds.size, vm.selectedTransactionIds.size), fontWeight = FontWeight.Bold)
            Row {
                IconButton(onClick = onBulkEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.transaction_bulk_category)) }
                IconButton(onClick = vm::deleteSelectedTransactions) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                IconButton(onClick = vm::clearTransactionSelection) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.action_cancel)) }
            }
        }
    }
}

@Composable
internal fun SearchField(vm: FinanceViewModel) {
    OutlinedTextField(
        value = vm.search,
        onValueChange = vm::onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.finance_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (vm.search.isNotEmpty()) {
                IconButton(onClick = vm::clearSearch) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.finance_clear_search)) }
            }
        },
        singleLine = true,
    )
}

@Composable
internal fun TypeFilterRow(vm: FinanceViewModel) {
    val options = listOf(
        TxTypeFilter.ALL to stringResource(R.string.filter_all),
        TxTypeFilter.INCOME to "+ " + stringResource(R.string.tx_income),
        TxTypeFilter.EXPENSE to "− " + stringResource(R.string.tx_expense),
        TxTypeFilter.TRANSFER to "⇄ " + stringResource(R.string.tx_transfer),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) ->
            FilterChip(
                selected = vm.typeFilter == value,
                onClick = { vm.onTypeFilterChange(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun PeriodFilterRow(vm: FinanceViewModel) {
    val options = listOf(
        PeriodFilter.DAY to stringResource(R.string.action_today),
        PeriodFilter.MONTH to stringResource(R.string.period_month),
        PeriodFilter.ALL to stringResource(R.string.period_all),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (value, label) ->
            FilterChip(
                selected = vm.periodFilter == value,
                onClick = { vm.onPeriodFilterChange(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun CategoryFilterChip(category: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(stringResource(R.string.finance_category_clear, localizedDomainText(category))) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
internal fun EmptyState(isSearching: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (isSearching) Icons.Filled.Search else Icons.Filled.AccountBalanceWallet,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(6.dp))
        Text(
            text = stringResource(if (isSearching) R.string.finance_empty_search_title else R.string.finance_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isSearching) {
                stringResource(R.string.finance_empty_search_body)
            } else {
                stringResource(R.string.finance_empty_body)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
    }
}
