package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import ua.rytm.app.ui.components.SwipeReleaseAction
import ua.rytm.app.ui.components.swipeReleaseAction
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmInteraction
import ua.rytm.app.ui.motionAwareSpec
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.Event
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Build
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.theme.tabularNums
import ua.rytm.app.ui.components.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import ua.rytm.app.ui.LocalReducedMotion
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.ExpandLess

// Implements FINANCE_SCREEN_SPEC.md end to end for this step: hero balance,
// quick actions, search+filters, transaction list with swipe-to-delete, two
// distinct empty states, collapsed/expand-all. Backed by FinanceViewModel,
// which is Room-persisted as of this step (still bootstrapped from
// SampleFinanceData — see the spec's §7/§8/FinanceRepository's comment for
// what's deliberately still a no-op or a seed, not real synced data).

private const val TX_LIST_COLLAPSED_COUNT = 5 // mirrors js/analytics-csv.js's TX_LIST_COLLAPSED_COUNT
/** A collapsed list may grow to this many rows to finish the last day it shows. */
private const val TX_LIST_COLLAPSED_MAX = 10

@Composable
fun FinanceScreen(
    viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.factory(ua.rytm.app.ui.rytmApp()),
    ),
    onOpenCapital: () -> Unit = {},
    onOpenForecast: () -> Unit = {},
) {
    val canEdit = LocalCanEditProfile.current
    val app = ua.rytm.app.ui.rytmApp()
    val launchAction by app.pendingLaunchAction.collectAsStateWithLifecycle()
    LaunchedEffect(launchAction, canEdit, viewModel.loading) {
        // canEdit starts false until the role loads; on a cold start (tap on a
        // bank suggestion / widget "+") consuming the action now dropped it.
        // Also wait for wallets+history: opened earlier the prefill had no wallet
        // and the alphabetically first category ("% …") instead of the frequent one.
        val action = launchAction
        if (action in ua.rytm.app.MainActivity.FINANCE_ACTIONS && canEdit && !viewModel.loading) {
            app.pendingLaunchAction.value = null
            when (action) {
                ua.rytm.app.MainActivity.ACTION_QUICK_ENTRY -> viewModel.quickEntryOpen = true
                ua.rytm.app.MainActivity.ACTION_MONTH_SUMMARY -> viewModel.openMonthSummary()
                ua.rytm.app.MainActivity.ACTION_NEW_INCOME -> viewModel.openNewTransactionSheet(type = TxType.INCOME)
                ua.rytm.app.MainActivity.ACTION_NEW_EXPENSE -> if (viewModel.needsStartingBalance) {
                    // A fresh profile: the money already there first, or the
                    // first expense shows as a minus.
                    viewModel.expenseAfterStartingBalance = true
                    viewModel.startingBalanceOpen = true
                } else viewModel.openNewTransactionSheet(type = TxType.EXPENSE)
                else -> {
                    val prefill = app.pendingPrefill.value
                    app.pendingPrefill.value = null
                    viewModel.openNewTransactionSheet(prefill)
                }
            }
        }
    }
    // «До операцій» from the Analytics tab: no edit right needed to look.
    val historyJump by app.pendingHistoryJump.collectAsStateWithLifecycle()
    LaunchedEffect(historyJump, viewModel.loading) {
        val jump = historyJump ?: return@LaunchedEffect
        if (viewModel.loading) return@LaunchedEffect
        app.pendingHistoryJump.value = null
        viewModel.applyFilters(if (jump.type == TxType.INCOME) TxTypeFilter.INCOME else TxTypeFilter.EXPENSE, jump.category, HistoryFilter(from = jump.from, to = jump.to))
        if (jump.from == null) viewModel.onPeriodFilterChange(PeriodFilter.ALL)
    }
    viewModel.salaryAllocation?.let { plan ->
        SalaryAllocationSheet(plan, viewModel.wallets, onApply = viewModel::applySalaryAllocation, onDismiss = { viewModel.salaryAllocation = null })
    }
    if (viewModel.startingBalanceOpen) {
        StartingBalanceSheet(viewModel.wallets, onSave = viewModel::saveStartingBalances, onDismiss = {
            viewModel.startingBalanceOpen = false
            if (viewModel.expenseAfterStartingBalance) {
                viewModel.expenseAfterStartingBalance = false
                viewModel.openNewTransactionSheet(type = TxType.EXPENSE)
            }
        })
    }
    MonthSummaryHost(viewModel)
    if (viewModel.quickEntryOpen) {
        QuickEntryDialog(
            wallets = viewModel.wallets,
            onDismiss = { viewModel.quickEntryOpen = false },
            onParsed = { result ->
                viewModel.quickEntryOpen = false
                viewModel.openNewTransactionSheet(
                    ua.rytm.app.bank.TxPrefill(
                        isIncome = result.isIncome,
                        amount = result.amount,
                        currency = viewModel.wallets.firstOrNull { it.id == result.walletId }?.currency ?: "UAH",
                        comment = result.comment.ifBlank { null },
                        walletId = result.walletId,
                        category = viewModel.guessQuickEntryCategory(result.comment, result.isIncome),
                    ),
                )
            },
        )
    }
    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbarHostState = LocalSnackbarHost.current ?: ownHost
    val scope = rememberCoroutineScope()
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingTx by remember { mutableStateOf<Transaction?>(null) }
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

    val session = ua.rytm.app.ui.LocalSession.current
    val accountUid = session.accountUid
    val activeProfileId = session.profileId
    val activeProfileOwnerUid = session.ownerUid
    val widgetConfig by app.settingsStore.financeWidgets.collectAsStateWithLifecycle(
        initialValue = ua.rytm.app.data.local.FinanceWidgetsConfig(emptySet(), emptyList()),
    )
    val showBalanceAfter by app.settingsStore.showBalanceAfter.collectAsStateWithLifecycle(initialValue = false)
    var toolsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var budgetsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var goalsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var walletOrderFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    // Search lives behind the magnifier in the history header; a search
    // restored with the screen keeps its field open.
    var searchOpen by rememberSaveable { mutableStateOf(viewModel.search.isNotBlank()) }
    // A search started elsewhere (a tapped #tag) opens the field, so the list
    // never filters by a search the user can't see.
    LaunchedEffect(viewModel.search.isNotBlank()) { if (viewModel.search.isNotBlank()) searchOpen = true }
    val listState = rememberLazyListState()
    // The floating "Згорнути список" FAB exists so a user who scrolled deep
    // into a long expanded list can collapse it without scrolling all the
    // way back to the inline OutlinedButton (key "collapse-list-button")
    // that also does this. Showing both at once when that inline button is
    // still on screen just duplicates the same action twice in one
    // viewport (flagged live, screenshot: two "Згорнути список" controls
    // stacked). Matched by the item's stable key.
    val collapseButtonVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "collapse-list-button" }
        }
    }
    val swipedRows = remember { androidx.compose.runtime.mutableStateSetOf<String>() }
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

    // The "+" lives in the bottom bar now (PrimaryAction), not as a FAB over the rows.
    ua.rytm.app.ui.components.ProvidePrimaryAction(stringResource(R.string.transaction_new_title), enabled = canEdit) {
        viewModel.openNewTransactionSheet()
    }
    Scaffold(
        containerColor = Color.Transparent, // background comes from MainActivity's Surface (overdraw)
        // The host itself lives in RytmNavHost now — one per app.
        snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost, Modifier.padding(bottom = RytmDimens.BottomContentClearance)) },
        floatingActionButton = {
            if (viewModel.listExpanded && !collapseButtonVisible) {
                val shape = RoundedCornerShape(RytmRadii.Pill)
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(8.dp, shape)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClick = ::collapseTransactions)
                        .padding(horizontal = RytmSpacing.xl, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(RytmIcons.ExpandLess, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Text(stringResource(R.string.action_collapse_list), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        val filtered = viewModel.filteredTransactions
        val walletsById = remember(viewModel.wallets) { viewModel.wallets.associateBy { it.id } }
        // Read only when the setting is on, so the running sum is never computed otherwise.
        val balancesAfter = if (showBalanceAfter) viewModel.balancesAfter else emptyMap()
        val tagsById = remember(viewModel.tags) { viewModel.tags.associateBy { it.id } }
        // Collapsed, the list ends on a day boundary where it can: a day cut in
        // the middle showed a header total that the visible rows did not add up to.
        val collapsed = remember(filtered) { collapsedHistory(filtered, TX_LIST_COLLAPSED_COUNT, TX_LIST_COLLAPSED_MAX) }
        val visible = if (viewModel.listExpanded) filtered else collapsed
        // Each day header shows the whole day's net, not just the rows shown while collapsed.
        val dayNets = remember(filtered, pendingDeleteId) { viewModel.dayNetsUah(filtered.filter { it.id != pendingDeleteId }) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + RytmDimens.FabScreenBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.lg),
        ) {
            item { RealtimeStateBanner() }
            if (viewModel.loading) item { ScreenLoadingState() }
            if (viewModel.loadFailed) item { ScreenLoadErrorState() }
            item {
                HeroBalanceCard(viewModel, onOpenCapital = onOpenCapital, onOpenForecast = onOpenForecast, onWalletClick = { id ->
                    viewModel.toggleWalletFilter(id)
                    // Selecting brings the filtered history into view; clearing stays put.
                    if (viewModel.historyFilter.walletId == id) scope.launch { listState.animateScrollToItem(historyHeaderIndex) }
                }, onWalletLongClick = if (canEdit && viewModel.wallets.size > 1) { id -> walletOrderFocus = id } else null)
            }
            item {
                QuickActionsRow(
                    canEdit = canEdit,
                    onTools = { toolsSheetOpen = true },
                    onBudgets = { budgetsSheetOpen = true },
                    onGoals = { goalsSheetOpen = true },
                )
            }
            if (viewModel.showMonthSummaryCard) {
                item(key = "month-summary") {
                    MonthSummaryCard(viewModel.lastMonthSummary, onOpen = { viewModel.openMonthSummary(lastMonth = true) }, onClose = viewModel::markMonthSummarySeen)
                }
            }
            if (canEdit && viewModel.subscriptionCandidates.isNotEmpty()) {
                item(key = "subscription-suggestions") {
                    SubscriptionSuggestionsCard(viewModel.subscriptionCandidates, viewModel::acceptSubscription, viewModel::dismissSubscription)
                }
            }
            // Nothing to search yet: on an empty account these pushed the first action below the fold.
            val hasHistory = filtered.isNotEmpty() || viewModel.isSearchOrFilterActive
            item {
                HistoryHeader(
                    searchOpen = searchOpen,
                    showTools = hasHistory,
                    activeFilterCount = viewModel.activeFilterCount,
                    onToggleSearch = {
                        // Closing the field clears it: a hidden search still
                        // filtering the list would read as lost operations.
                        if (searchOpen) viewModel.onSearchChange("")
                        searchOpen = !searchOpen
                    },
                    onOpenFilters = { filtersOpen = true },
                )
            }
            if (hasHistory && searchOpen) item(key = "search") {
                val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                // Opened by a tap: the keyboard comes up at once, as in a bank app.
                LaunchedEffect(Unit) { if (viewModel.search.isEmpty()) runCatching { focus.requestFocus() } }
                SearchField(viewModel, Modifier.focusRequester(focus))
            }
            if (hasHistory) item { FilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }
            val hf = viewModel.historyFilter
            if (hf.walletId != null || hf.minAmount != null || hf.maxAmount != null) item {
                // Wallet and amount filters had no visible trace except the badge.
                val parts = listOfNotNull(
                    hf.walletId?.let { id -> viewModel.wallets.firstOrNull { it.id == id }?.name?.let { ua.rytm.app.ui.localizedDomainText(it) } },
                    hf.minAmount?.let { androidx.compose.ui.res.stringResource(R.string.filters_from) + " " + formatMoney(it) },
                    hf.maxAmount?.let { androidx.compose.ui.res.stringResource(R.string.filters_to) + " " + formatMoney(it) },
                )
                CategoryFilterChip(parts.joinToString(", "), onClear = viewModel::clearWalletAmountFilters)
            }
            // "#відпустка" or any filter: how much the found operations add up to.
            if (viewModel.isSearchOrFilterActive && filtered.isNotEmpty()) item(key = "filtered-totals") {
                val shownTxs = filtered.filter { it.id != pendingDeleteId }
                val (spent, earned) = remember(shownTxs) { viewModel.totalsUah(shownTxs) }
                FilteredTotals(shownTxs.size, spent, earned)
            }

            if (!viewModel.loading && !viewModel.loadFailed && filtered.isEmpty()) {
                item {
                    // Opening balances, not an income row named «Початковий
                    // баланс»: that row counted as the month's income.
                    EmptyState(isSearching = viewModel.isSearchOrFilterActive, canEdit = canEdit) {
                        viewModel.startingBalanceOpen = true
                    }
                }
            } else {
                // Was a lambda per row doing firstOrNull over every wallet and
                // every tag — O(rows x wallets) on each recomposition, and four
                // freshly-allocated lambdas per row capturing the ViewModel.
                // Grouped by day (newest first, as sorted by the ViewModel), each
                // header carrying that day's net — the Monobank/Revolut pattern;
                // the per-row date it replaces is gone from the rows.
                val days = visible.groupBy { it.date }
                days.forEach { (date, dayTxs) ->
                    val shown = dayTxs.filter { it.id != pendingDeleteId }
                    // Design audit 2026-11-26: a day is one segmented group (outer
                    // corners round, 2dp seams between rows, Android 16's grouped
                    // list) instead of a separate card per row — so the whole day
                    // is one LazyColumn item; rows keep their state via key(tx.id).
                    item(key = "day-$date", contentType = "day") {
                        AnimatedVisibility(visible = shown.isNotEmpty(), exit = fadeOut(tween(180)) + shrinkVertically(tween(220))) {
                            Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                                DayHeader(date, dayNets[date] ?: 0.0)
                                Column(verticalArrangement = Arrangement.spacedBy(GroupedRowGap)) {
                                    dayTxs.forEach { tx ->
                                        androidx.compose.runtime.key(tx.id) {
                                            // Shape follows the rows still shown, so the new first/last
                                            // row rounds as soon as a neighbour starts leaving.
                                            val index = shown.indexOfFirst { it.id == tx.id }
                                            AnimatedVisibility(
                                                visible = pendingDeleteId != tx.id,
                                                exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
                                            ) {
                                                TransactionRow(
                                                    tx = tx,
                                                    // One wallet: naming it on every row says nothing.
                                                    walletName = { id -> if (walletsById.size < 2) null else id?.let(walletsById::get)?.name },
                                                    tagLookup = tagsById::get,
                                                    iconOverride = viewModel.categoryIcons[tx.category],
                                                    canEdit = canEdit && pendingDeleteId == null,
                                                    resetGeneration = swipeResetGeneration,
                                                    onDelete = { requestDelete(tx) },
                                                    canOpen = pendingDeleteId == null,
                                                    onClick = { if (canEdit) viewModel.openEditTransactionSheet(tx) else viewingTx = tx },
                                                    onSwipeOpenChange = { open -> if (open) swipedRows.add(tx.id) else swipedRows.remove(tx.id) },
                                                    balanceAfter = if (showBalanceAfter) balancesAfter[tx.id] else null,
                                                    showWallet = viewModel.historyFilter.walletId == null,
                                                    shape = groupedRowShape(index.coerceAtLeast(0), shown.size.coerceAtLeast(1)),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (collapsed.size < filtered.size) {
                    item(key = "collapse-list-button") {
                        ua.rytm.app.ui.components.RytmOutlinedButton(
                            onClick = { if (viewModel.listExpanded) collapseTransactions() else viewModel.toggleListExpanded() },
                            modifier = Modifier.fillMaxWidth(),
                            // Was Pill -- DebtScreen's functionally identical
                            // "Переглянути всі"/"Згорнути" button uses Row;
                            // found mismatched during the button-shape audit.
                            shape = RoundedCornerShape(RytmRadii.Row),
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

    // A viewer can't edit, but can open an operation to read all of it.
    viewingTx?.let { tx ->
        TransactionDetailsDialog(
            tx = tx,
            walletName = viewModel.wallets.firstOrNull { it.id == tx.walletId }?.name,
            tagNames = tx.tags.mapNotNull { id -> viewModel.tags.firstOrNull { it.id == id }?.name },
            onDismiss = { viewingTx = null },
        )
    }
    if (filtersOpen) HistoryFiltersSheet(viewModel) { filtersOpen = false }
    if (viewModel.form.sheetVisible) {
        // Delete from the edit sheet too: swipe alone was hard to find and
        // impossible without a path gesture.
        TransactionFormSheet(viewModel, onDelete = {
            viewModel.editingTransaction()?.let { tx -> viewModel.form.close(); requestDelete(tx) }
        })
    }
    if (toolsSheetOpen) {
        ToolsSheet(repository = app.financeRepository, onDismiss = { toolsSheetOpen = false })
    }
    if (budgetsSheetOpen && accountUid != null) {
        BudgetsManagerSheet(repository = app.financeRepository, syncRepository = app.budgetsSyncRepository, uid = activeProfileOwnerUid ?: accountUid, profileId = activeProfileId, onDismiss = { budgetsSheetOpen = false })
    }
    walletOrderFocus?.let { focus ->
        if (accountUid != null) {
            WalletOrderSheet(
                repository = app.financeRepository,
                syncRepository = app.financeSyncRepository,
                recurringSyncRepository = app.recurringSyncRepository,
                uid = activeProfileOwnerUid ?: accountUid,
                profileId = activeProfileId,
                focusId = focus,
                onDismiss = { walletOrderFocus = null },
            )
        }
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

/**
 * The month summary sheet with its PDF paywall, for whichever tab opens it:
 * Finance (widget, recap notification) and Analytics' own button, which used
 * to switch the user over to Finance first (2026-11-27).
 */
@Composable
internal fun MonthSummaryHost(viewModel: FinanceViewModel) {
    val app = ua.rytm.app.ui.rytmApp()
    var pdfPaywallOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (pdfPaywallOpen) ua.rytm.app.ui.screens.PremiumDialog(onDismiss = { pdfPaywallOpen = false })
    if (viewModel.monthSummaryOpen) {
        val reportContext = androidx.compose.ui.platform.LocalContext.current
        val shareTitle = androidx.compose.ui.res.stringResource(ua.rytm.app.R.string.month_report_share)
        MonthSummarySheet(
            viewModel.shownMonthSummary,
            onDismiss = { viewModel.monthSummaryOpen = false },
            onPrevMonth = { viewModel.shiftSummaryMonth(-1) },
            onNextMonth = if (viewModel.canShowNextSummaryMonth) ({ viewModel.shiftSummaryMonth(1) }) else null,
            ai = viewModel.aiInsight,
            aiKey = { lang -> viewModel.aiKey(viewModel.shownMonthSummary, lang) },
            onAskAi = if (viewModel.aiInsightsAvailable) ({ lang, label -> viewModel.askAiInsight(viewModel.shownMonthSummary, lang, label) }) else null,
            onSharePdf = onSharePdf@{
                if (!app.premiumRepository.isPremium.value) { pdfPaywallOpen = true; return@onSharePdf }
                runCatching {
                    shareMonthReportPdf(reportContext, viewModel.monthReport(viewModel.shownMonthSummary.month), viewModel::walletDisplayName, shareTitle)
                }.onFailure { viewModel.reportExportFailed() }
            },
        )
    }
}
