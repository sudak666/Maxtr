package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import ua.rytm.app.RytmApplication
import ua.rytm.app.data.DEFAULT_PROFILE_ID

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
    viewModel: FinanceViewModel = viewModel(
        factory = FinanceViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel.pendingMessage) {
        viewModel.pendingMessage?.let { message ->
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
            // brand purple — see ANDROID_MIGRATION.md visual-parity note.
            ExtendedFloatingActionButton(
                onClick = viewModel::openNewTransactionSheet,
                icon = { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Нова операція", color = Color.White) },
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
            item { HeroBalanceCard(viewModel) }
            item {
                QuickActionsRow(
                    onNewTransaction = viewModel::openNewTransactionSheet,
                    onTools = { toolsSheetOpen = true },
                    onBudgets = { budgetsSheetOpen = true },
                    onGoals = { goalsSheetOpen = true },
                )
            }
            widgetConfig.order.filter { it in widgetConfig.enabled }.forEach { key ->
                item(key = "dashboard-widget-$key") { FinanceDashboardWidget(key, app) }
            }
            item { HistoryHeader(viewModel, resultCount = filtered.size) }
            item { SearchField(viewModel) }
            item { TypeFilterRow(viewModel) }
            item { PeriodFilterRow(viewModel) }
            viewModel.categoryFilter?.let { cat ->
                item { CategoryFilterChip(cat, onClear = viewModel::clearCategoryFilter) }
            }

            if (filtered.isEmpty()) {
                item { EmptyState(isSearching = viewModel.isSearchOrFilterActive) }
            } else {
                items(visible, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        walletName = { id -> viewModel.wallets.firstOrNull { it.id == id }?.name },
                        tagLookup = { id -> viewModel.tags.firstOrNull { it.id == id } },
                        iconOverride = viewModel.categoryIcons[tx.category],
                        onDelete = { viewModel.deleteTransaction(tx.id) },
                        onClick = { viewModel.openEditTransactionSheet(tx) },
                    )
                }
                if (filtered.size > TX_LIST_COLLAPSED_COUNT) {
                    item {
                        TextButton(
                            onClick = viewModel::toggleListExpanded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (viewModel.listExpanded) "Згорнути" else "Переглянути всі")
                        }
                    }
                }
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
        Column(Modifier.padding(20.dp)) {
            Text(
                text = if (vm.isMultiCurrency) "Орієнтовний баланс (у грн)" else "Загальний баланс (у грн)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${if (vm.isMultiCurrency) "≈ " else ""}${formatMoney(vm.totalBalanceUah)} грн",
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
                    text = "$sign${formatMoney(kotlin.math.abs(net))} грн цього місяця",
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (vm.isMultiCurrency) {
                Text(
                    text = "Сума перерахована в гривню за поточними курсами гаманців.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatCard(label = "Дохід цього місяця", value = vm.monthIncomeUah, positive = true, modifier = Modifier.weight(1f))
                MiniStatCard(label = "Витрата цього місяця", value = vm.monthExpenseUah, positive = false, modifier = Modifier.weight(1f))
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
private val GreenDarkLike @Composable get() = ua.rytm.app.ui.theme.GreenDark2
private val RedLike @Composable get() = ua.rytm.app.ui.theme.RedDark2

@Composable
private fun MiniStatCard(label: String, value: Double, positive: Boolean, modifier: Modifier = Modifier) {
    // Matches the PWA's .fin-mini-stat.income/.expense: a tinted
    // green/red gradient wash + matching border, not a neutral surface —
    // see ANDROID_MIGRATION.md visual-parity note.
    val tint = if (positive) GreenDarkLike else RedLike
    val shape = RoundedCornerShape(18.dp)
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
                text = "${if (positive) "+" else "−"}${formatMoney(value)} грн",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
        }
    }
}

@Composable
private fun WalletChip(wallet: Wallet, balance: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Solid color dot for the wallet, matching .wallet-chip-dot.
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(wallet.colorHex)))
            Spacer(Modifier.padding(4.dp))
            Text(wallet.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.padding(4.dp))
            Text(
                "${formatMoney(balance)} ${currencySymbol(wallet.currency)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(onNewTransaction: () -> Unit, onTools: () -> Unit, onBudgets: () -> Unit, onGoals: () -> Unit) {
    data class QuickAction(val label: String, val icon: ImageVector, val primary: Boolean, val onClick: () -> Unit)

    val actions = listOf(
        QuickAction("Операція", Icons.Filled.Add, primary = true, onClick = onNewTransaction),
        QuickAction("Інструменти", Icons.Filled.Build, primary = false, onClick = onTools),
        QuickAction("Бюджети", Icons.Filled.PieChart, primary = false, onClick = onBudgets),
        QuickAction("Цілі", Icons.Filled.Flag, primary = false, onClick = onGoals),
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEach { action ->
            // Matches the PWA's .quick-action: a plain neutral card with a
            // circular tinted icon badge inside (.quick-action-icon), not a
            // whole-card color fill — see ANDROID_MIGRATION.md visual-parity note.
            Card(
                onClick = action.onClick,
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
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
private fun HistoryHeader(vm: FinanceViewModel, resultCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Історія операцій", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("$resultCount записів", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchField(vm: FinanceViewModel) {
    OutlinedTextField(
        value = vm.search,
        onValueChange = vm::onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Пошук за коментарем, категорією, гаманцем…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (vm.search.isNotEmpty()) {
                IconButton(onClick = vm::clearSearch) { Icon(Icons.Filled.Clear, contentDescription = "Очистити пошук") }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun TypeFilterRow(vm: FinanceViewModel) {
    val options = listOf(
        TxTypeFilter.ALL to "Всі",
        TxTypeFilter.INCOME to "+ Дохід",
        TxTypeFilter.EXPENSE to "− Витрата",
        TxTypeFilter.TRANSFER to "⇄ Переказ",
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
        PeriodFilter.DAY to "Сьогодні",
        PeriodFilter.MONTH to "Цей місяць",
        PeriodFilter.ALL to "Весь час",
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
        label = { Text("$category · Скинути ✕") },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun EmptyState(isSearching: Boolean) {
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
            text = if (isSearching) "Нічого не знайдено" else "Операцій ще немає",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isSearching) {
                "Зміни фільтр або пошуковий запит, щоб побачити операції."
            } else {
                "Додай перший дохід або витрату, щоб побачити баланс, історію та аналітику."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionRow(
    tx: Transaction,
    walletName: (String?) -> String?,
    tagLookup: (String) -> Tag?,
    iconOverride: String?,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    // confirmValueChange is deprecated (in favor of dynamic anchors) as of
    // this Compose BOM but still functional — not worth the bigger
    // AnchoredDraggable rewrite for this step; revisit if it's ever removed.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Видалити",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(end = 20.dp),
                )
            }
        },
    ) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBadge(tx.category, iconOverride = iconOverride)
                Spacer(Modifier.padding(6.dp))
                Column(Modifier.weight(1f)) {
                    val catLine = buildString {
                        append(tx.category)
                        tx.subcategory?.let { append(" · $it") }
                        walletName(tx.walletId)?.let { append(" · $it") }
                        if (tx.type == TxType.TRANSFER) {
                            walletName(tx.targetWalletId)?.let { append(" → $it") }
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
                Spacer(Modifier.padding(4.dp))
                val (amountText, amountColor) = when (tx.type) {
                    TxType.INCOME -> "+${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}" to GreenDarkLike
                    TxType.EXPENSE -> "−${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}" to RedLike
                    TxType.TRANSFER -> "${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor)
            }
        }
    }
}
