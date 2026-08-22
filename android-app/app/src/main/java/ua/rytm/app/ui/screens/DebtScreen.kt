package ua.rytm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.maskedAmount
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

    Scaffold(
        floatingActionButton = {
            if (cd != null && canEdit) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::openNewEntrySheet,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Платіж") },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = innerPadding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { DebtChipsRow(viewModel, canEdit) }

            if (cd == null) {
                item { EmptyDebtState() }
            } else {
                item { HeroBalance(cd) }
                item { ProgressBarSection(cd) }
                item { ChipStatsRow(cd) }
                item { DebtForecastCard(cd) }
                item { InfoPanel(viewModel, cd, canEdit) }
                item { HistoryHeader(viewModel, cd) }
                if (viewModel.historyExpanded) {
                    if (cd.entries.isEmpty()) {
                        item { EmptyEntriesState() }
                    } else {
                        // Newest first, matching js/debt.js's lc.prepend() display order.
                        items(cd.entries.reversed(), key = { it.id }) { entry ->
                            DebtEntryRow(viewModel, entry, cd.currency, canEdit)
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
            title = { Text("Видалити розрахунок") },
            text = { Text("Видалити цей розрахунок і всю історію платежів?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteDebt) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteDebt) { Text("Скасувати") } },
        )
    }

    if (viewModel.pendingDeleteEntryId != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteEntry,
            title = { Text("Видалити платіж") },
            text = { Text("Видалити цей запис з історії?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDeleteEntry) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDeleteEntry) { Text("Скасувати") } },
        )
    }

    viewModel.errorMessageRes?.let { messageRes ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("Увага") },
            text = { Text(stringResource(messageRes)) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text("Гаразд") } },
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
                Text(debt.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        if (canEdit) item {
            var addOpen by remember { mutableStateOf(false) }
            Card(onClick = { addOpen = true }, shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier)
                    Text("Новий розрахунок", fontWeight = FontWeight.SemiBold)
                }
            }
            if (addOpen) {
                var name by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { addOpen = false },
                    title = { Text("Новий розрахунок") },
                    text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Назва") }) },
                    confirmButton = {
                        val fallbackName = stringResource(R.string.debt_new_default)
                        TextButton(onClick = { viewModel.addDebt(name, fallbackName); addOpen = false }) { Text(stringResource(R.string.action_add)) }
                    },
                    dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Скасувати") } },
                )
            }
        }
    }
}

@Composable
private fun EmptyDebtState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Немає розрахунків", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Додай перший розрахунок кнопкою вище", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Поточний залишок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Сплачено", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(Icons.Filled.AccountBalanceWallet, maskedAmount("${formatMoney(cd.startAmount)} ${cd.currency}"), "Початкова сума", Modifier.weight(1f))
        StatChip(Icons.Filled.CheckCircle, maskedAmount("${formatMoney(cd.paid())} ${cd.currency}"), "Сплачено", Modifier.weight(1f))
        StatChip(Icons.Filled.Receipt, cd.entries.size.toString(), "Платежів", Modifier.weight(1f))
        if (due != null) StatChip(Icons.Filled.Event, due, "Термін сплати", Modifier.weight(1f))
    }
}

private fun dueChipInfo(cd: Debt): String? {
    if (cd.dueDate.isBlank()) return null
    val diffDays = try {
        val due = java.time.LocalDate.parse(cd.dueDate)
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due)
    } catch (e: Exception) { return null }
    return when {
        diffDays < 0 -> "−${-diffDays} дн."
        diffDays == 0L -> "Сьогодні"
        else -> "$diffDays дн."
    }
}

// Matches the PWA's .chip-stat/.chip-stat-icon: a pill with a small circular
// purple-gradient icon badge, not a plain Card.
@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(999.dp), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.PurpleDark, ua.rytm.app.ui.theme.Purple3))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun InfoPanel(viewModel: DebtViewModel, cd: Debt, canEdit: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Дані розрахунку", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (canEdit) IconButton(onClick = viewModel::toggleInfoPanel) {
                Icon(if (viewModel.infoExpanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = "Редагувати")
            }
        }
        if (viewModel.infoExpanded) {
            var name by remember(cd.id) { mutableStateOf(cd.name) }
            var note by remember(cd.id) { mutableStateOf(cd.note) }
            var currency by remember(cd.id) { mutableStateOf(cd.currency) }
            var startAmount by remember(cd.id) { mutableStateOf(if (cd.startAmount == 0.0) "" else cd.startAmount.toString()) }
            var dueDate by remember(cd.id) { mutableStateOf(cd.dueDate) }

            fun commit() { viewModel.updateInfo(name, note, currency, startAmount.toDoubleOrNull() ?: 0.0, dueDate) }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Назва") })
                OutlinedTextField(value = note, onValueChange = { note = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Нотатка") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startAmount, onValueChange = { startAmount = it; commit() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Стартова сума") })
                    CurrencyPickerField(value = currency, onValueChange = { currency = it; commit() }, modifier = Modifier.weight(1f))
                }
                DatePickerField(value = dueDate, onValueChange = { dueDate = it; commit() }, label = "Термін сплати", modifier = Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::requestDeleteCurrentDebt) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Видалити розрахунок", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(viewModel: DebtViewModel, cd: Debt) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Історія платежів", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("${cd.entries.size} записів", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = viewModel::toggleHistoryPanel) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Згорнути/розгорнути", modifier = Modifier)
        }
    }
}

@Composable
private fun EmptyEntriesState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ще немає платежів", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtEntryRow(viewModel: DebtViewModel, entry: DebtEntry, currency: String, canEdit: Boolean) {
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { swipeThresholdPx },
        confirmValueChange = { value ->
            if (canEdit && value == SwipeToDismissBoxValue.EndToStart) {
                viewModel.requestDeleteEntry(entry.id)
                true
            } else false
        },
    )
    val editing = viewModel.entryEditId == entry.id

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.fillMaxHeight().width(SwipeRevealWidth).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Delete, contentDescription = "Видалити", tint = MaterialTheme.colorScheme.onError)
                }
            }
        },
    ) {
        Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (editing) {
                    var amount by remember(entry.id) { mutableStateOf(entry.amount) }
                    var balance by remember(entry.id) { mutableStateOf(entry.balance.toString()) }
                    var date by remember(entry.id) { mutableStateOf(entry.date) }
                    OutlinedTextField(value = amount, onValueChange = { amount = it; viewModel.updateEntryAmount(entry, it) }, singleLine = true, label = { Text("Сума") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = balance, onValueChange = { balance = it; viewModel.updateEntryBalance(entry, it) }, singleLine = true, label = { Text("Залишок") }, modifier = Modifier.weight(1f))
                        DatePickerField(value = date, onValueChange = { date = it; viewModel.updateEntryDate(entry, it) }, label = "Дата", modifier = Modifier.weight(1f), allowEmpty = false)
                    }
                    TextButton(onClick = { viewModel.toggleEntryEdit(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${entry.amount} $currency", fontWeight = FontWeight.SemiBold)
                            Text(maskedAmount("Залишок: ${formatMoney(entry.balance)} $currency") + " · ${entry.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.toggleEntryEdit(entry.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Редагувати") }
                    }
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
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Новий платіж", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                label = { Text("Сума платежу") },
            )
            OutlinedTextField(value = balance, onValueChange = { balance = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Новий залишок") })
            DatePickerField(value = date, onValueChange = { date = it }, label = "Дата", modifier = Modifier.fillMaxWidth(), allowEmpty = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::closeNewEntrySheet) { Text("Скасувати") }
                TextButton(onClick = { viewModel.addEntry(amount, balance, date) }) { Text("Додати") }
            }
        }
    }
}
