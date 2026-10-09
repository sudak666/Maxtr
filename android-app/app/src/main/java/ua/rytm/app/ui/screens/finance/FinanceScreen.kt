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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import ua.rytm.app.ui.LocalReducedMotion
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.Event
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
    val launchApp = LocalContext.current.applicationContext as RytmApplication
    val launchAction by launchApp.pendingLaunchAction.collectAsState()
    LaunchedEffect(launchAction, canEdit, viewModel.loading) {
        // canEdit starts false until the role loads; on a cold start (tap on a
        // bank suggestion / widget "+") consuming the action now dropped it.
        // Also wait for wallets+history: opened earlier the prefill had no wallet
        // and the alphabetically first category ("% …") instead of the frequent one.
        if (launchAction == ua.rytm.app.MainActivity.ACTION_NEW_TRANSACTION && canEdit && !viewModel.loading) {
            launchApp.pendingLaunchAction.value = null
            val prefill = launchApp.pendingPrefill.value
            launchApp.pendingPrefill.value = null
            viewModel.openNewTransactionSheet(prefill)
        }
    }
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
    // The floating "Згорнути список" FAB exists so a user who scrolled deep
    // into a long expanded list can collapse it without scrolling all the
    // way back to the inline OutlinedButton (key "collapse-list-button")
    // that also does this. Showing both at once when that inline button is
    // still on screen just duplicates the same action twice in one
    // viewport (flagged live, screenshot: two "Згорнути список" controls
    // stacked). Same visible-item-key technique as showFab above.
    val collapseButtonVisible by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.key == "collapse-list-button" }
        }
    }
    // M3 extended-FAB behaviour: full label at the top, shrinks to the round
    // icon FAB once the list is scrolled, so it stops covering rows (the
    // wide pill hid amounts/"Переглянути всі" mid-scroll, seen live on an A51).
    val fabAtTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val compactHeight = LocalConfiguration.current.screenHeightDp < 480
    val haptics = LocalHapticFeedback.current
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
                val collapsed = largeText || compactHeight || !fabAtTop
                val label = stringResource(R.string.transaction_new_title)
                val fabAnimMs = if (LocalReducedMotion.current) 0 else 450
                val fabPadding by animateDpAsState(if (collapsed) 16.dp else 22.dp, tween(fabAnimMs), label = "fabPadding")
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(10.dp, shape, spotColor = ua.rytm.app.ui.theme.GreenLight2.copy(alpha = 0.5f))
                        .clip(shape)
                        .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.GreenLight2, ua.rytm.app.ui.theme.GreenDeep)))
                        .clickable(role = Role.Button, onClick = {
                            // LongPress, not VirtualKey/Confirm -- verified live on a
                            // real device (Samsung A51) that the lighter constants
                            // produce a barely-perceptible tick on this hardware.
                            // LongPress is the strongest tap-style constant the
                            // public HapticFeedbackType API exposes.
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openNewTransactionSheet()
                        })
                        .padding(horizontal = fabPadding, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        RytmIcons.Add,
                        contentDescription = if (collapsed) label else null,
                        tint = Color.White,
                    )
                    // Label slides/fades out and the pill narrows together, instead
                    // of snapping straight to the round "+" (owner: "оп і плюс").
                    AnimatedVisibility(
                        visible = !collapsed,
                        enter = expandHorizontally(tween(fabAnimMs), expandFrom = Alignment.Start) + fadeIn(tween(fabAnimMs)),
                        exit = shrinkHorizontally(tween(fabAnimMs), shrinkTowards = Alignment.Start) + fadeOut(tween(fabAnimMs / 2)),
                    ) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
                    }
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
                bottom = innerPadding.calculateBottomPadding() + RytmDimens.FabScreenBottomClearance,
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
                    onTools = { toolsSheetOpen = true },
                    onBudgets = { budgetsSheetOpen = true },
                    onGoals = { goalsSheetOpen = true },
                )
            }
            item { HistoryHeader(viewModel, resultCount = displayedCount) }
            item { SearchField(viewModel) }
            item { FilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }

            if (!viewModel.loading && !viewModel.loadFailed && filtered.isEmpty()) {
                item { EmptyState(isSearching = viewModel.isSearchOrFilterActive, canEdit = canEdit, onAddFirst = viewModel::openNewTransactionSheet) }
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
                    item(key = "day-$date") {
                        AnimatedVisibility(visible = shown.isNotEmpty(), exit = fadeOut(tween(180)) + shrinkVertically(tween(220))) {
                            // Whole day's net, not just the rows shown while collapsed.
                            DayHeader(date, viewModel.netUah(filtered.filter { it.date == date && it.id != pendingDeleteId }))
                        }
                    }
                items(dayTxs, key = { it.id }) { tx ->
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
                }
                if (filtered.size > TX_LIST_COLLAPSED_COUNT) {
                    item(key = "collapse-list-button") {
                        androidx.compose.material3.OutlinedButton(
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
