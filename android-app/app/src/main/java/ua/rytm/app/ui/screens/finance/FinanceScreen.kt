package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.localizedDomainText

// Implements FINANCE_SCREEN_SPEC.md end to end for this step: hero balance,
// quick actions, search+filters, transaction list with swipe-to-delete, two
// distinct empty states, collapsed/expand-all. Backed by FinanceViewModel,
// which is Room-persisted as of this step (still bootstrapped from
// SampleFinanceData — see the spec's §7/§8/FinanceRepository's comment for
// what's deliberately still a no-op or a seed, not real synced data).

private const val TX_LIST_COLLAPSED_COUNT = 5 // mirrors js/analytics-csv.js's TX_LIST_COLLAPSED_COUNT

private val FinanceFabShape = RoundedCornerShape(16.dp)

@Composable
fun FinanceScreen(
    sharedText: String? = null,
    openNewTransaction: Boolean = false,
    onLaunchRequestConsumed: () -> Unit = {},
    viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val canEdit = LocalCanEditProfile.current
    LaunchedEffect(openNewTransaction, sharedText, canEdit) {
        if (openNewTransaction) {
            if (canEdit) viewModel.openNewTransactionSheet(sharedText)
            onLaunchRequestConsumed()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var bulkCategoryOpen by remember { mutableStateOf(false) }
    val undoRows = viewModel.pendingUndoTransactions
    val deletedMessage = pluralStringResource(R.plurals.transaction_deleted_count, undoRows.size, undoRows.size)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(undoRows) {
        if (undoRows.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoTransactionDelete()
            else viewModel.consumeUndoTransactions()
        }
    }
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Matches the PWA's .fin-fab.finance-fab green gradient
            // (linear-gradient(135deg,--green,#059669)), not the theme's
            // Keep the selected state on the established brand purple.
            if (canEdit) ExtendedFloatingActionButton(
                onClick = viewModel::openNewTransactionSheet,
                icon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White) },
                text = { Text(stringResource(R.string.transaction_new_title), color = Color.White) },
                shape = FinanceFabShape,
                containerColor = Color.Transparent,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                modifier = Modifier
                    .shadow(10.dp, FinanceFabShape, spotColor = GreenDarkLike.copy(alpha = 0.5f))
                    .clip(FinanceFabShape)
                    .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.GreenDark, ua.rytm.app.ui.theme.GreenLight2))),
            )
        },
    ) { innerPadding ->
        val filtered = viewModel.filteredTransactions
        val visible = if (viewModel.listExpanded || filtered.size <= TX_LIST_COLLAPSED_COUNT) {
            filtered
        } else {
            filtered.take(TX_LIST_COLLAPSED_COUNT)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
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
            widgetConfig.order.filter { it in widgetConfig.enabled }.forEach { key ->
                item(key = "dashboard-widget-$key") { FinanceDashboardWidget(key, app) }
            }
            item { HistoryHeader(viewModel, resultCount = filtered.size, onBulkEdit = { bulkCategoryOpen = true }) }
            item { SearchField(viewModel) }
            item { TypeFilterRow(viewModel) }
            item { PeriodFilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }

            if (!viewModel.loading && !viewModel.loadFailed && filtered.isEmpty()) {
                item { EmptyState(isSearching = viewModel.isSearchOrFilterActive) }
            } else {
                items(visible, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        walletName = { id -> viewModel.wallets.firstOrNull { it.id == id }?.name },
                        tagLookup = { id -> viewModel.tags.firstOrNull { it.id == id } },
                        iconOverride = viewModel.categoryIcons[tx.category],
                        canEdit = canEdit,
                        selected = tx.id in viewModel.selectedTransactionIds,
                        selectionMode = viewModel.selectedTransactionIds.isNotEmpty(),
                        onDelete = { viewModel.deleteTransaction(tx.id) },
                        onClick = { viewModel.openEditTransactionSheet(tx) },
                        onToggleSelection = { viewModel.toggleTransactionSelection(tx.id) },
                        syncState = viewModel.transactionSyncStates[tx.id],
                    )
                }
                if (filtered.size > TX_LIST_COLLAPSED_COUNT) {
                    item {
                        TextButton(
                            onClick = viewModel::toggleListExpanded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(if (viewModel.listExpanded) R.string.action_collapse else R.string.action_view_all))
                        }
                    }
                }
            }
        }
    }

    if (viewModel.sheetVisible) {
        TransactionFormSheet(viewModel)
    }
    if (bulkCategoryOpen) {
        val categories = viewModel.categoriesByType.values.flatten().distinct().sorted()
        AlertDialog(
            onDismissRequest = { bulkCategoryOpen = false },
            title = { Text(stringResource(R.string.transaction_bulk_category)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.updateSelectedCategory(category); bulkCategoryOpen = false },
                            label = { Text(localizedDomainText(category)) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { bulkCategoryOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
