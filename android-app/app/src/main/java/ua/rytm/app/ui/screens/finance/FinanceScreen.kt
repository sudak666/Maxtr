package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.ui.components.SwipeOpenThreshold
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmInteraction
import ua.rytm.app.ui.motionAwareSpec
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.LocalSnackbarHost

// Implements FINANCE_SCREEN_SPEC.md end to end for this step: hero balance,
// quick actions, search+filters, transaction list with swipe-to-delete, two
// distinct empty states, collapsed/expand-all. Backed by FinanceViewModel,
// which is Room-persisted as of this step (still bootstrapped from
// SampleFinanceData — see the spec's §7/§8/FinanceRepository's comment for
// what's deliberately still a no-op or a seed, not real synced data).

private const val TX_LIST_COLLAPSED_COUNT = 5 // mirrors js/analytics-csv.js's TX_LIST_COLLAPSED_COUNT

@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val canEdit = LocalCanEditProfile.current
    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbarHostState = LocalSnackbarHost.current ?: ownHost
    val scope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var swipeResetGeneration by remember { mutableIntStateOf(0) }
    val transactionDeleted = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    val pendingMessage = viewModel.pendingMessage?.let { message ->
        val arguments = if (message.resource == R.string.transaction_auto_category || message.resource == R.string.transaction_budget_exceeded) {
            message.arguments.mapIndexed { index, value -> if (index == 0) localizedDomainText(value.toString()) else value }
        } else message.arguments
        stringResource(message.resource, *arguments.toTypedArray())
    }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val app = LocalContext.current.applicationContext as RytmApplication
    val accountUid = FirebaseAuth.getInstance().currentUser?.uid
    val activeProfileId by (accountUid?.let(app.activeProfileStore::activeProfileId) ?: flowOf(DEFAULT_PROFILE_ID))
        .collectAsState(initial = DEFAULT_PROFILE_ID)
    val activeProfileOwnerUid by (accountUid?.let(app.activeProfileStore::activeProfileOwnerUid) ?: flowOf(null))
        .collectAsState(initial = null)
    val widgetConfig by app.settingsStore.financeWidgets.collectAsState(
        initial = ua.rytm.app.data.local.FinanceWidgetsConfig(emptySet(), emptyList()),
    )
    var toolsSheetOpen by remember { mutableStateOf(false) }
    var budgetsSheetOpen by remember { mutableStateOf(false) }
    var goalsSheetOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex <= 8 } }
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val historyHeaderIndex = 3 + (if (viewModel.loading) 1 else 0) + (if (viewModel.loadFailed) 1 else 0)

    fun collapseTransactions() {
        if (!viewModel.listExpanded) return
        viewModel.toggleListExpanded()
        scope.launch { listState.animateScrollToItem(historyHeaderIndex) }
    }

    fun requestDelete(id: String) {
        if (pendingDeleteId != null) return
        pendingDeleteId = id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = transactionDeleted,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result != SnackbarResult.ActionPerformed) {
                viewModel.deleteTransaction(id) { pendingDeleteId = null }
            } else {
                swipeResetGeneration++
                pendingDeleteId = null
            }
        }
    }

    Scaffold(
        // The host itself lives in RytmNavHost now — one per app.
        snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost, Modifier.padding(bottom = RytmDimens.BottomContentClearance)) },
        floatingActionButton = {
            if (viewModel.listExpanded) {
                val shape = RoundedCornerShape(RytmRadii.Pill)
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(8.dp, shape)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClick = ::collapseTransactions)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.action_collapse_list), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            } else if (canEdit && showFab) {
                val shape = RoundedCornerShape(RytmRadii.Pill)
                // At a large font scale or a short screen the extended FAB used
                // to be hidden outright — removing the screen's primary action
                // from exactly the users who need it most. M3's answer is to
                // collapse it to a round icon FAB instead.
                val collapsed = largeText || compactHeight
                val label = stringResource(R.string.transaction_new_title)
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(10.dp, shape, spotColor = ua.rytm.app.ui.theme.GreenLight2.copy(alpha = 0.5f))
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.GreenLight2, ua.rytm.app.ui.theme.GreenDeep)))
                        .clickable(role = Role.Button, onClick = viewModel::openNewTransactionSheet)
                        .padding(horizontal = if (collapsed) 16.dp else 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = if (collapsed) label else null,
                        tint = Color.White,
                    )
                    if (!collapsed) Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        val filtered = viewModel.filteredTransactions.filterNot { it.id == pendingDeleteId }
        val visible = if (viewModel.listExpanded || filtered.size <= TX_LIST_COLLAPSED_COUNT) {
            filtered
        } else {
            filtered.take(TX_LIST_COLLAPSED_COUNT)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + RytmDimens.BottomContentClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RealtimeStateBanner() }
            if (viewModel.loading) item { ScreenLoadingState() }
            if (viewModel.loadFailed) item { ScreenLoadErrorState() }
            item { HeroBalanceCard(viewModel) }
            item {
                QuickActionsRow(
                    canEdit = canEdit,
                    onNewTransaction = viewModel::openNewTransactionSheet,
                    onTools = { toolsSheetOpen = true },
                    onBudgets = { budgetsSheetOpen = true },
                    onGoals = { goalsSheetOpen = true },
                )
            }
            item { HistoryHeader(viewModel, resultCount = filtered.size) }
            item { SearchField(viewModel) }
            item { TypeFilterRow(viewModel) }
            item { PeriodFilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }

            if (!viewModel.loading && !viewModel.loadFailed && filtered.isEmpty()) {
                item { EmptyState(isSearching = viewModel.isSearchOrFilterActive, canEdit = canEdit, onAddFirst = viewModel::openNewTransactionSheet) }
            } else {
                items(visible, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        walletName = { id -> viewModel.wallets.firstOrNull { it.id == id }?.name },
                        tagLookup = { id -> viewModel.tags.firstOrNull { it.id == id } },
                        iconOverride = viewModel.categoryIcons[tx.category],
                        canEdit = canEdit,
                        resetGeneration = swipeResetGeneration,
                        onDelete = { requestDelete(tx.id) },
                        onClick = { viewModel.openEditTransactionSheet(tx) },
                    )
                }
                if (filtered.size > TX_LIST_COLLAPSED_COUNT) {
                    item {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { if (viewModel.listExpanded) collapseTransactions() else viewModel.toggleListExpanded() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(RytmRadii.Pill),
                        ) {
                            Text(stringResource(if (viewModel.listExpanded) R.string.action_collapse_list else R.string.action_view_all))
                        }
                    }
                }
            }
            widgetConfig.order.filter { it in widgetConfig.enabled }.forEach { key ->
                item(key = "dashboard-widget-$key") { FinanceDashboardWidget(key, app) }
            }
        }
    }

    if (viewModel.sheetVisible) {
        TransactionFormSheet(viewModel)
    }
    if (toolsSheetOpen) {
        ToolsSheet(repository = app.financeRepository, onDismiss = { toolsSheetOpen = false })
    }
    if (budgetsSheetOpen && accountUid != null) {
        BudgetsManagerSheet(repository = app.financeRepository, syncRepository = app.budgetsSyncRepository, uid = activeProfileOwnerUid ?: accountUid, profileId = activeProfileId, onDismiss = { budgetsSheetOpen = false })
    }
    if (goalsSheetOpen && accountUid != null) {
        GoalsManagerSheet(
            repository = app.financeRepository,
            syncRepository = app.goalsSyncRepository,
            uid = activeProfileOwnerUid ?: accountUid,
            profileId = activeProfileId,
            onDismiss = { goalsSheetOpen = false },
        )
    }
}

@Composable
private fun HeroBalanceCard(vm: FinanceViewModel) {
    // Matches the PWA's .hero-balance: a subtle bg1→bg2 diagonal gradient
    // plus a soft brand-purple glow shadow (--surface-hero/--shadow-raised),
    // not a flat Card — see ANDROID_MIGRATION.md visual-parity note.
    val shape = MaterialTheme.shapes.large
    val largeText = LocalDensity.current.fontScale >= 1.2f
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
                text = maskedAmount(stringResource(if (vm.isMultiCurrency) R.string.finance_amount_uah_estimated else R.string.finance_amount_uah, formatMoney(vm.totalBalanceUah))),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
                fontWeight = FontWeight.Black,
            )

            val net = vm.monthIncomeUah - vm.monthExpenseUah
            val trendColor = RytmSemantic.signed(net)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(
                    imageVector = if (net < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                val sign = if (net > 0) "+" else if (net < 0) "−" else ""
                Text(
                    text = maskedAmount(stringResource(R.string.finance_month_net, sign, formatMoney(kotlin.math.abs(net)))),
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

            if (largeText) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatCard(label = stringResource(R.string.finance_month_income), value = vm.monthIncomeUah, positive = true, modifier = Modifier.fillMaxWidth())
                    MiniStatCard(label = stringResource(R.string.finance_month_expense), value = vm.monthExpenseUah, positive = false, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatCard(label = stringResource(R.string.finance_month_income), value = vm.monthIncomeUah, positive = true, modifier = Modifier.weight(1f))
                    MiniStatCard(label = stringResource(R.string.finance_month_expense), value = vm.monthExpenseUah, positive = false, modifier = Modifier.weight(1f))
                }
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

@Composable
private fun MiniStatCard(label: String, value: Double, positive: Boolean, modifier: Modifier = Modifier) {
    // Matches the PWA's .fin-mini-stat.income/.expense: a tinted
    // green/red gradient wash + matching border, not a neutral surface —
    // see ANDROID_MIGRATION.md visual-parity note.
    // Wash tint and text tone are separate: the light-theme wash needs the
    // brighter green/red to read as a tint at all, while the value on top of
    // it needs the deeper tone to clear 4.5:1 against that wash.
    val tint = if (positive) RytmSemantic.incomeWash else RytmSemantic.expenseWash
    val valueColor = if (positive) RytmSemantic.income else RytmSemantic.expense
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
                text = maskedAmount(stringResource(R.string.finance_signed_uah, if (positive) "+" else "−", formatMoney(value))),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun WalletChip(wallet: Wallet, balance: Double) {
    Card(shape = RoundedCornerShape(RytmRadii.Pill), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Solid color dot for the wallet, matching .wallet-chip-dot.
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(wallet.colorHex)))
            Spacer(Modifier.width(8.dp))
            Text(localizedDomainText(wallet.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                maskedAmount("${formatMoney(balance)} ${currencySymbol(wallet.currency)}"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(canEdit: Boolean, onNewTransaction: () -> Unit, onTools: () -> Unit, onBudgets: () -> Unit, onGoals: () -> Unit) {
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
            // whole-card color fill — see ANDROID_MIGRATION.md visual-parity note.
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
                                    Brush.linearGradient(listOf(ua.rytm.app.ui.theme.GreenLight2, ua.rytm.app.ui.theme.GreenDeep))
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
                    Spacer(Modifier.width(4.dp))
                    Text(action.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(vm: FinanceViewModel, resultCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.finance_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(pluralStringResource(R.plurals.finance_records, resultCount, resultCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchField(vm: FinanceViewModel) {
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
private fun TypeFilterRow(vm: FinanceViewModel) {
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
private fun PeriodFilterRow(vm: FinanceViewModel) {
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
private fun CategoryFilterChip(category: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(stringResource(R.string.finance_category_clear, localizedDomainText(category))) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun EmptyState(isSearching: Boolean, canEdit: Boolean, onAddFirst: () -> Unit) {
    // The no-transactions state had no call to action at all, while the FAB
    // that would have been the obvious next tap can itself be hidden (large
    // font / compact height / scrolled far down the list).
    RytmEmptyState(
        icon = if (isSearching) Icons.Filled.Search else Icons.Filled.AccountBalanceWallet,
        title = stringResource(if (isSearching) R.string.finance_empty_search_title else R.string.finance_empty_title),
        body = stringResource(if (isSearching) R.string.finance_empty_search_body else R.string.finance_empty_body),
        actionLabel = if (!isSearching && canEdit) stringResource(R.string.finance_empty_cta) else null,
        onAction = if (!isSearching && canEdit) onAddFirst else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    walletName: (String?) -> String?,
    tagLookup: (String) -> Tag?,
    iconOverride: String?,
    canEdit: Boolean,
    resetGeneration: Int,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    var deleteRequested by remember(tx.id) { mutableStateOf(false) }
    // confirmValueChange is deprecated (in favor of dynamic anchors) as of
    // this Compose BOM but still functional — not worth the bigger
    // AnchoredDraggable rewrite for this step; revisit if it's ever removed.
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { swipeThresholdPx },
        confirmValueChange = { value ->
            if (canEdit && value == SwipeToDismissBoxValue.EndToStart) {
                if (!deleteRequested) {
                    deleteRequested = true
                    onDelete()
                }
                true
            } else {
                false
            }
        },
    )
    LaunchedEffect(resetGeneration) {
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        deleteRequested = false
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        },
    ) {
        Card(
            onClick = onClick,
            enabled = canEdit,
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBadge(tx.category, iconOverride = iconOverride)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val categoryLabel = localizedDomainText(tx.category)
                    val walletLabel = walletName(tx.walletId)?.let { localizedDomainText(it) }
                    val targetWalletLabel = walletName(tx.targetWalletId)?.let { localizedDomainText(it) }
                    val catLine = buildString {
                        append(categoryLabel)
                        tx.subcategory?.let { append(" · $it") }
                        walletLabel?.let { append(" · $it") }
                        if (tx.type == TxType.TRANSFER) {
                            targetWalletLabel?.let { append(" → $it") }
                        }
                    }
                    Text(catLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    val dateParts = tx.date.split("-") // "yyyy-MM-dd" -> "dd.MM.yyyy", matches txItemInnerHtml()
                    val metaLine = buildString {
                        append("${dateParts.getOrElse(2) { "" }}.${dateParts.getOrElse(1) { "" }}.${dateParts.getOrElse(0) { "" }}")
                        tx.comment?.let { append(" · $it") }
                    }
                    Text(metaLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val rowTags = tx.tags.mapNotNull(tagLookup)
                    if (rowTags.isNotEmpty()) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowTags.forEach { tag ->
                                val color = Color(tag.colorHex)
                                Box(
                                    Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(color.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(tag.name, style = MaterialTheme.typography.labelSmall, color = color)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val (amountText, amountColor) = when (tx.type) {
                    TxType.INCOME -> maskedAmount("+${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to RytmSemantic.income
                    TxType.EXPENSE -> maskedAmount("−${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to RytmSemantic.expense
                    TxType.TRANSFER -> maskedAmount("${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor)
            }
        }
    }
}
