package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import ua.rytm.app.ui.components.SwipeReleaseAction
import ua.rytm.app.ui.components.swipeReleaseAction
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
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Build
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.theme.tabularNums

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
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var swipeResetGeneration by rememberSaveable { mutableIntStateOf(0) }
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
    var toolsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var budgetsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var goalsSheetOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // The FAB should hide once the dashboard widgets (Goals/Top
    // cryptocurrencies/Tip of the day, appended after the transaction list)
    // scroll into view, so it doesn't sit on top of their content — this
    // used to be a hardcoded `firstVisibleItemIndex <= 8`, which assumed a
    // fixed item count before the widgets. That count actually varies with
    // the loading/error banners, the category filter chip, the transaction
    // row count, AND how many widgets are enabled — with fewer widgets
    // enabled the list is shorter than index 8 even fully scrolled, so the
    // FAB never hid at all (reported live). Checking each widget item's own
    // stable key against what's actually visible is correct regardless of
    // how many precede it.
    val showFab by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.none { (it.key as? String)?.startsWith("dashboard-widget-") == true }
        }
    }
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val historyHeaderIndex = 3 + (if (viewModel.loading) 1 else 0) + (if (viewModel.loadFailed) 1 else 0)

    fun collapseTransactions() {
        if (!viewModel.listExpanded) return
        viewModel.toggleListExpanded()
        scope.launch { listState.animateScrollToItem(historyHeaderIndex) }
    }

    fun requestDelete(transaction: Transaction): Boolean {
        if (pendingDeleteId != null) return false
        pendingDeleteId = transaction.id
        viewModel.deleteTransaction(transaction.id, animationDelayMs = 220L) { deleted ->
            pendingDeleteId = null
            if (!deleted) {
                swipeResetGeneration++
                return@deleteTransaction
            }
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = transactionDeleted,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.restoreTransaction(transaction)
                }
            }
        }
        return true
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
                    Icon(RytmIcons.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
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
                        RytmIcons.Add,
                        contentDescription = if (collapsed) label else null,
                        tint = Color.White,
                    )
                    if (!collapsed) Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        val filtered = viewModel.filteredTransactions
        val displayedCount = filtered.count { it.id != pendingDeleteId }
        val walletsById = remember(viewModel.wallets) { viewModel.wallets.associateBy { it.id } }
        val tagsById = remember(viewModel.tags) { viewModel.tags.associateBy { it.id } }
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
            item { HistoryHeader(viewModel, resultCount = displayedCount) }
            item { SearchField(viewModel) }
            item { TypeFilterRow(viewModel) }
            item { PeriodFilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }

            if (!viewModel.loading && !viewModel.loadFailed && filtered.isEmpty()) {
                item { EmptyState(isSearching = viewModel.isSearchOrFilterActive, canEdit = canEdit, onAddFirst = viewModel::openNewTransactionSheet) }
            } else {
                // Was a lambda per row doing firstOrNull over every wallet and
                // every tag — O(rows x wallets) on each recomposition, and four
                // freshly-allocated lambdas per row capturing the ViewModel.
                items(visible, key = { it.id }) { tx ->
                    AnimatedVisibility(
                        visible = pendingDeleteId != tx.id,
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
                    ) {
                        TransactionRow(
                            tx = tx,
                            walletName = { id -> id?.let(walletsById::get)?.name },
                            tagLookup = tagsById::get,
                            iconOverride = viewModel.categoryIcons[tx.category],
                            canEdit = canEdit && pendingDeleteId == null,
                            resetGeneration = swipeResetGeneration,
                            onDelete = { requestDelete(tx) },
                            onClick = { viewModel.openEditTransactionSheet(tx) },
                        )
                    }
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
                // Tabular figures: proportional digits make the balance jitter
                // horizontally as it changes.
                style = MaterialTheme.typography.displayMedium.tabularNums(),
                fontWeight = FontWeight.Black,
            )

            val net = vm.monthIncomeUah - vm.monthExpenseUah
            val trendColor = RytmSemantic.signed(net)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(
                    imageVector = if (net < 0) RytmIcons.TrendingDown else RytmIcons.TrendingUp,
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
            // Same icon-circle + label treatment as ToolsSheet's
            // AnalyticsTotalCard (income/expense-this-month, the same
            // underlying figures) — this card used to render label+value
            // only, reading as a different component instead of a compact
            // variant of the same one; PWA's equivalent (.fin-mini-stat-icon)
            // already got this fix, this one hadn't yet.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).background(valueColor.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (positive) RytmIcons.TrendingUp else RytmIcons.TrendingDown, contentDescription = null, tint = valueColor, modifier = Modifier.size(13.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = maskedAmount(stringResource(R.string.finance_signed_uah, if (positive) "+" else "−", formatMoney(value))),
                style = MaterialTheme.typography.titleMedium.tabularNums(),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp),
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
        QuickAction(stringResource(R.string.finance_action_transaction), RytmIcons.Add, primary = true, onClick = onNewTransaction),
        QuickAction(stringResource(R.string.tools_title), RytmIcons.Build, primary = false, onClick = onTools),
        QuickAction(stringResource(R.string.budgets_title), RytmIcons.PieChart, primary = false, onClick = onBudgets),
        QuickAction(stringResource(R.string.goals_title), RytmIcons.Flag, primary = false, onClick = onGoals),
    ).filterIndexed { index, _ -> canEdit || index == 1 }
    val configuration = LocalConfiguration.current
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val columnCount = when {
        largeText -> 1
        configuration.screenWidthDp < 600 -> 2
        else -> 4
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(columnCount).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) RytmInteraction.ButtonPressedScale else 1f,
                        animationSpec = motionAwareSpec(tween(100)),
                        label = "quick-action-press",
                    )
                    Card(
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f).height(RytmDimens.QuickActionMinHeight).graphicsLayer { scaleX = scale; scaleY = scale },
                        shape = RoundedCornerShape(RytmRadii.Row),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        interactionSource = interactionSource,
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = if (action.primary) RytmSemantic.income else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(RytmDimens.QuickActionIcon),
                            )
                            Text(
                                action.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                repeat(columnCount - rowActions.size) { Spacer(Modifier.weight(1f)) }
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
        leadingIcon = { Icon(RytmIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (vm.search.isNotEmpty()) {
                IconButton(onClick = vm::clearSearch) { Icon(RytmIcons.Clear, contentDescription = stringResource(R.string.finance_clear_search)) }
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
        icon = if (isSearching) RytmIcons.Search else RytmIcons.AccountBalanceWallet,
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
    onDelete: () -> Boolean,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { SwipeOpenThreshold.toPx() }
    val revealWidthPx = with(density) { SwipeRevealWidth.toPx() }
    var rowWidthPx by remember(tx.id) { mutableIntStateOf(0) }
    var offsetPx by remember(tx.id) { mutableFloatStateOf(0f) }

    suspend fun settleAt(target: Float) {
        animate(offsetPx, target, animationSpec = tween(180)) { value, _ -> offsetPx = value }
    }

    LaunchedEffect(resetGeneration) {
        settleAt(0f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .onSizeChanged { rowWidthPx = it.width },
    ) {
        // The covering Card below is sized from rowWidthPx, which starts at
        // 0 until the Box above reports its real width via onSizeChanged —
        // for that one frame the Card is 0-wide and this full-red
        // delete-reveal layer underneath is fully exposed. Invisible for a
        // single freshly-composed row, but "View all" composes dozens at
        // once, turning that one frame into a visible red flash across the
        // whole list (reported live). Not drawing this layer until the row
        // has a real measured width closes the gap.
        if (rowWidthPx > 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(SwipeRevealWidth)
                        .fillMaxHeight()
                        .clickable(enabled = canEdit && offsetPx <= -revealWidthPx / 2, role = Role.Button) {
                            if (onDelete()) scope.launch { settleAt(-rowWidthPx.toFloat()) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        RytmIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        // Was `.fillMaxWidth().offset { IntOffset(offsetPx, 0) }` — a
        // horizontal translate, the exact anti-pattern the PWA's own swipe
        // rows explicitly avoid (see js/analytics-csv.js's setupTxSwipe()
        // doc comment: "shrinks its own width... never transform:translateX
        // — clips left-edge content"). Translating a rounded-corner Card
        // left exposes ITS OWN rounded corner mid-row once it's no longer
        // flush with the row's right edge — reported live via screenshot as
        // a stray diagonal/rounded cut where the trash icon reveals. Shrink
        // the card's actual width instead (anchored at the row's start), so
        // the reveal edge is a straight vertical line matching the shrunk
        // box's own corner, not a rounded corner floating mid-row.
        val cardWidthDp = with(density) { (rowWidthPx + offsetPx).coerceAtLeast(0f).toDp() }
        Card(
            onClick = {
                if (offsetPx < -1f) scope.launch { settleAt(0f) } else onClick()
            },
            enabled = canEdit,
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
            modifier = Modifier
                .width(cardWidthDp)
                .draggable(
                    enabled = canEdit,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(-rowWidthPx.toFloat(), 0f)
                    },
                    onDragStopped = { velocity ->
                        when (swipeReleaseAction(offsetPx, rowWidthPx.toFloat(), swipeThresholdPx, velocity)) {
                            SwipeReleaseAction.Delete -> if (onDelete()) settleAt(-rowWidthPx.toFloat()) else settleAt(0f)
                            SwipeReleaseAction.Reveal -> settleAt(-revealWidthPx)
                            SwipeReleaseAction.Settle -> settleAt(0f)
                        }
                    },
                ),
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
