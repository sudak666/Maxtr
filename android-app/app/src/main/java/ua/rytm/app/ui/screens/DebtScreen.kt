package ua.rytm.app.ui.screens
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.ReducedMotionVisibility
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.DatePickerField
import ua.rytm.app.ui.components.CurrencyPickerField
import ua.rytm.app.ui.screens.debt.DEBT_COLORS
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.DebtEntry
import ua.rytm.app.ui.screens.debt.DebtForecastCard
import ua.rytm.app.ui.screens.debt.DebtViewModel
import ua.rytm.app.ui.screens.debt.currentBalance
import ua.rytm.app.ui.screens.debt.paid
import ua.rytm.app.ui.screens.debt.todayLabel
import ua.rytm.app.ui.screens.finance.formatMoney
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding

// Implements the in-scope subset of CLAUDE.md §1.4: debt chips, hero balance,
// progress bar, chip stats, due chip, payoff-forecast burndown chart,
// collapsible info/history, payment CRUD with swipe-to-delete, FAB → new-
// payment sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    viewModel: DebtViewModel = viewModel(
        factory = DebtViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val cd = viewModel.currentDebt
    val canEdit = LocalCanEditProfile.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historyHeaderIndex = 7 + (if (viewModel.loading) 1 else 0) + (if (viewModel.loadFailed) 1 else 0)

    fun collapseHistory() {
        if (!viewModel.historyExpanded) return
        viewModel.toggleHistoryPanel()
        scope.launch { listState.animateScrollToItem(historyHeaderIndex) }
    }

    Scaffold(
        floatingActionButton = {
            if (cd != null && canEdit) {
                val shape = RoundedCornerShape(999.dp)
                val collapse = viewModel.historyExpanded
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(10.dp, shape)
                        .clip(shape)
                        .background(
                            if (collapse) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
                            else Brush.linearGradient(listOf(ua.rytm.app.ui.theme.OrangeDark, ua.rytm.app.ui.theme.OrangeLight2)),
                        )
                        .clickable(role = Role.Button, onClick = if (collapse) ::collapseHistory else viewModel::openNewEntrySheet)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (collapse) Icons.Filled.ExpandLess else Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Text(stringResource(if (collapse) R.string.action_collapse_list else R.string.debt_payment), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = innerPadding.calculateBottomPadding() + RytmDimens.BottomContentClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RealtimeStateBanner() }
            if (viewModel.loading) item { ScreenLoadingState() }
            if (viewModel.loadFailed) item { ScreenLoadErrorState() }
            item { DebtChipsRow(viewModel, canEdit) }

            if (!viewModel.loading && !viewModel.loadFailed && cd == null) {
                item { EmptyDebtState() }
            } else if (cd != null) {
                item { HeroBalance(cd) }
                item { ProgressBarSection(cd) }
                item { ChipStatsRow(cd) }
                item { DebtForecastCard(cd) }
                item { InfoPanel(viewModel, cd, canEdit) }
                val newestEntries = cd.entries.reversed()
                val visibleEntries = if (viewModel.historyExpanded) newestEntries else newestEntries.take(3)
                if (visibleEntries.isEmpty()) {
                    item { EmptyEntriesState() }
                } else {
                    items(visibleEntries, key = { it.id }) { entry ->
                        DebtEntryRow(viewModel, entry, cd.currency, canEdit)
                    }
                }
                if (newestEntries.size > 3) {
                    item {
                        OutlinedButton(
                            onClick = { if (viewModel.historyExpanded) collapseHistory() else viewModel.toggleHistoryPanel() },
                            modifier = Modifier.padding(end = 178.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(stringResource(if (viewModel.historyExpanded) R.string.action_collapse_list else R.string.action_view_all))
                        }
                    }
                }
            }
        }
    }

    if (canEdit && viewModel.newEntrySheetOpen && cd != null) {
        NewEntrySheet(viewModel, cd)
    }

    if (viewModel.pendingDeleteDebt) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteDebt,
            title = { Text(stringResource(R.string.debt_delete_title)) },
            text = { Text(stringResource(R.string.debt_delete_body)) },
            confirmButton = { Button(onClick = viewModel::confirmDeleteDebt, enabled = !viewModel.saving, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelDeleteDebt) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (viewModel.pendingDeleteEntryId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteEntry,
            title = { Text(stringResource(R.string.debt_payment_delete_title)) },
            text = { Text(stringResource(R.string.debt_payment_delete_body)) },
            confirmButton = { Button(onClick = viewModel::confirmDeleteEntry, enabled = !viewModel.saving, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text(stringResource(R.string.action_delete)) } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelDeleteEntry) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    viewModel.errorMessageRes?.let { messageRes ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text(stringResource(R.string.attention_title)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text(stringResource(R.string.action_ok)) } },
        )
    }
}

@Composable
private fun DebtChipsRow(viewModel: DebtViewModel, canEdit: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.debts, key = { it.id }) { debt ->
            val index = viewModel.debts.indexOf(debt)
            val active = debt.id == viewModel.currentDebt?.id
            Card(
                onClick = { viewModel.switchDebt(debt.id) },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (active) Color(DEBT_COLORS[index % DEBT_COLORS.size]) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(50),
            ) {
                Text(localizedDomainText(debt.name), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        if (canEdit) item {
            var addOpen by remember { mutableStateOf(false) }
            Card(onClick = { addOpen = true }, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier)
                    Text(stringResource(R.string.debt_new_default), fontWeight = FontWeight.SemiBold)
                }
            }
            if (addOpen) {
                var name by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { addOpen = false },
                    title = { Text(stringResource(R.string.debt_new_default)) },
                    text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }) },
                    confirmButton = {
                        val fallbackName = stringResource(R.string.debt_new_default)
                        Button(onClick = { viewModel.addDebt(name, fallbackName); addOpen = false }) { Text(stringResource(R.string.action_add)) }
                    },
                    dismissButton = { OutlinedButton(onClick = { addOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
    }
}

@Composable
private fun EmptyDebtState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.debt_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.debt_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Matches the PWA's .hero-metric: a subtle bg1->bg2 diagonal gradient plus a
// soft brand-purple glow shadow, not a flat Card — same treatment
// FinanceScreen's HeroBalanceCard (step 38) and Shifts' HeroMetric (step 39) got.
@Composable
private fun HeroBalance(cd: Debt) {
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
            Text(stringResource(R.string.debt_current_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maskedAmount("${formatMoney(cd.currentBalance())} ${cd.currency}"), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        }
    }
}

// Matches the PWA's .salary-bar-fill (reused as-is for #debt-progress-fill,
// same class) — a green gradient, not the theme's purple.
@Composable
private fun ProgressBarSection(cd: Debt) {
    val target = cd.startAmount
    if (target <= 0) return
    val paid = cd.paid()
    val pct = (paid / target).coerceIn(0.0, 1.0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.debt_paid), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct.toFloat())
                    .fillMaxSize()
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(ua.rytm.app.ui.theme.GreenDark, ua.rytm.app.ui.theme.GreenDark2))),
            )
        }
    }
}

// Matches the PWA's .chip-stat-row: due date folded in as a 4th chip
// (#debt-due-chip lives inside the same row, not a separate banner card).
@Composable
private fun ChipStatsRow(cd: Debt) {
    val due = dueChipInfo(cd)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { StatChip(Icons.Filled.AccountBalanceWallet, maskedAmount("${formatMoney(cd.startAmount)} ${cd.currency}"), stringResource(R.string.debt_start_amount)) }
        item { StatChip(Icons.Filled.CheckCircle, maskedAmount("${formatMoney(cd.paid())} ${cd.currency}"), stringResource(R.string.debt_paid)) }
        item { StatChip(Icons.Filled.Receipt, cd.entries.size.toString(), stringResource(R.string.debt_payments)) }
        if (due != null) item { StatChip(Icons.Filled.Event, due, stringResource(R.string.debt_due_date)) }
    }
}

@Composable
private fun dueChipInfo(cd: Debt): String? {
    if (cd.dueDate.isBlank()) return null
    val diffDays = try {
        val due = java.time.LocalDate.parse(cd.dueDate)
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due)
    } catch (e: Exception) { return null }
    return when {
        diffDays < 0 -> stringResource(R.string.debt_days_overdue, -diffDays)
        diffDays == 0L -> stringResource(R.string.action_today)
        else -> stringResource(R.string.debt_days_future, diffDays)
    }
}

// Matches the PWA's .chip-stat/.chip-stat-icon: a pill with a small circular
// purple-gradient icon badge, not a plain Card.
@Composable
private fun StatChip(icon: ImageVector, value: String, label: String) {
    Card(
        Modifier.widthIn(min = 164.dp),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.PurpleDark, ua.rytm.app.ui.theme.Purple3))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoPanel(viewModel: DebtViewModel, cd: Debt, canEdit: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Card(shape = RoundedCornerShape(18.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(enabled = canEdit, onClick = viewModel::toggleInfoPanel).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.debt_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (canEdit) Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (viewModel.infoExpanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        ReducedMotionVisibility(visible = viewModel.infoExpanded) {
            var name by remember(cd.id) { mutableStateOf(cd.name) }
            var note by remember(cd.id) { mutableStateOf(cd.note) }
            var currency by remember(cd.id) { mutableStateOf(cd.currency) }
            var startAmount by remember(cd.id) { mutableStateOf(if (cd.startAmount == 0.0) "" else cd.startAmount.toString()) }
            var dueDate by remember(cd.id) { mutableStateOf(cd.dueDate) }

            fun commit() { viewModel.updateInfo(name, note, currency, startAmount.toDoubleOrNull() ?: 0.0, dueDate) }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.field_name)) })
                OutlinedTextField(value = note, onValueChange = { note = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.note_label)) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startAmount, onValueChange = { startAmount = it; commit() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.debt_start_amount)) })
                    CurrencyPickerField(value = currency, onValueChange = { currency = it; commit() }, modifier = Modifier.weight(1f))
                }
                DatePickerField(value = dueDate, onValueChange = { dueDate = it; commit() }, label = stringResource(R.string.debt_due_date), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::requestDeleteCurrentDebt) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.debt_delete_title), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyEntriesState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.debt_payments_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtEntryRow(viewModel: DebtViewModel, entry: DebtEntry, currency: String, canEdit: Boolean) {
    DebtEntrySwipeContainer(canEdit = canEdit, onDelete = { viewModel.requestDeleteEntry(entry.id) }) {
        DebtEntryContent(viewModel, entry, currency)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtEntrySwipeContainer(canEdit: Boolean, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    var deleteCommitted by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { swipeThresholdPx },
        confirmValueChange = { value ->
            if (canEdit && value == SwipeToDismissBoxValue.EndToStart) {
                if (!deleteCommitted) { deleteCommitted = true; onDelete() }
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxHeight().width(SwipeRevealWidth).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onError)
                }
            }
        },
    ) {
        content()
    }
}

@Composable
private fun DebtEntryContent(viewModel: DebtViewModel, entry: DebtEntry, currency: String) {
    val editing = viewModel.entryEditId == entry.id
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (editing) {
                    var amount by remember(entry.id) { mutableStateOf(entry.amount) }
                    var balance by remember(entry.id) { mutableStateOf(entry.balance.toString()) }
                    var date by remember(entry.id) { mutableStateOf(entry.date) }
                    OutlinedTextField(value = amount, onValueChange = { amount = it; viewModel.updateEntryAmount(entry, it) }, singleLine = true, label = { Text(stringResource(R.string.amount_label)) }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = balance, onValueChange = { balance = it; viewModel.updateEntryBalance(entry, it) }, singleLine = true, label = { Text(stringResource(R.string.debt_balance)) }, modifier = Modifier.weight(1f))
                        DatePickerField(value = date, onValueChange = { date = it; viewModel.updateEntryDate(entry, it) }, label = stringResource(R.string.date_label), modifier = Modifier.weight(1f), allowEmpty = false)
                    }
                    TextButton(onClick = { viewModel.toggleEntryEdit(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${entry.amount} $currency", fontWeight = FontWeight.SemiBold)
                            Text(maskedAmount(stringResource(R.string.debt_balance_value, formatMoney(entry.balance), currency)) + " · ${entry.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.toggleEntryEdit(entry.id) }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntrySheet(viewModel: DebtViewModel, cd: Debt) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayLabel()) }

    ModalBottomSheet(onDismissRequest = viewModel::closeNewEntrySheet, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.debt_new_payment), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = amount,
                onValueChange = { new ->
                    // Real bug found while testing: comparing against autoFillBalance(amount)
                    // AFTER reassigning amount==new made the "did the user manually edit
                    // balance away from auto-fill" check compare the new value to itself,
                    // so auto-fill silently froze after the very first keystroke. Capture
                    // whether balance was still following auto-fill BEFORE updating amount.
                    val stillAutoFilled = balance.isEmpty() || balance == viewModel.autoFillBalance(amount)
                    amount = new
                    if (stillAutoFilled) balance = viewModel.autoFillBalance(new)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.debt_payment_amount)) },
            )
            OutlinedTextField(value = balance, onValueChange = { balance = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.debt_new_balance)) })
            DatePickerField(value = date, onValueChange = { date = it }, label = stringResource(R.string.date_label), modifier = Modifier.fillMaxWidth(), allowEmpty = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = viewModel::closeNewEntrySheet) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { viewModel.addEntry(amount, balance, date) }, enabled = !viewModel.saving) { Text(stringResource(R.string.action_add)) }
            }
        }
    }
}
