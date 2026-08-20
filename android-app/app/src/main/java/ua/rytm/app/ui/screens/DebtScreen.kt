package ua.rytm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
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
        factory = DebtViewModel.factory((LocalContext.current.applicationContext as RytmApplication).debtRepository),
    ),
) {
    val cd = viewModel.currentDebt

    Scaffold(
        floatingActionButton = {
            if (cd != null) {
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
            item { DebtChipsRow(viewModel) }

            if (cd == null) {
                item { EmptyDebtState() }
            } else {
                item { HeroBalance(cd) }
                item { ProgressBarSection(cd) }
                item { ChipStatsRow(cd) }
                item { DueChip(cd) }
                item { DebtForecastCard(cd) }
                item { InfoPanel(viewModel, cd) }
                item { HistoryHeader(viewModel, cd) }
                if (viewModel.historyExpanded) {
                    if (cd.entries.isEmpty()) {
                        item { EmptyEntriesState() }
                    } else {
                        // Newest first, matching js/debt.js's lc.prepend() display order.
                        items(cd.entries.reversed(), key = { it.id }) { entry ->
                            DebtEntryRow(viewModel, entry, cd.currency)
                        }
                    }
                }
            }
        }
    }

    if (viewModel.newEntrySheetOpen && cd != null) {
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

    viewModel.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("Увага") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text("Гаразд") } },
        )
    }
}

@Composable
private fun DebtChipsRow(viewModel: DebtViewModel) {
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
        item {
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
                        TextButton(onClick = { viewModel.addDebt(name); addOpen = false }) { Text("Додати") }
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

@Composable
private fun HeroBalance(cd: Debt) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Залишок", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatMoney(cd.currentBalance())} ${cd.currency}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ProgressBarSection(cd: Debt) {
    val target = cd.startAmount
    if (target <= 0) return
    val paid = cd.paid()
    val pct = (paid / target * 100).coerceIn(0.0, 100.0)
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(progress = { (pct / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text("${pct.toInt()}% сплачено", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ChipStatsRow(cd: Debt) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip("${formatMoney(cd.startAmount)} ${cd.currency}", "Стартова сума", Modifier.weight(1f))
        StatChip("${formatMoney(cd.paid())} ${cd.currency}", "Сплачено", Modifier.weight(1f))
        StatChip(cd.entries.size.toString(), "Платежів", Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DueChip(cd: Debt) {
    if (cd.dueDate.isBlank()) return
    val diffDays = try {
        val due = java.time.LocalDate.parse(cd.dueDate)
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due)
    } catch (e: Exception) { return }
    val suffix = when {
        diffDays < 0 -> "прострочено на ${-diffDays} дн."
        diffDays == 0L -> "сьогодні"
        else -> "через $diffDays дн."
    }
    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (diffDays <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Text("Термін сплати: ${cd.dueDate} · $suffix", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoPanel(viewModel: DebtViewModel, cd: Debt) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Дані розрахунку", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = viewModel::toggleInfoPanel) {
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
                    OutlinedTextField(value = currency, onValueChange = { currency = it; commit() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Валюта") })
                }
                OutlinedTextField(value = dueDate, onValueChange = { dueDate = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Термін сплати (yyyy-MM-dd)") })
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
private fun DebtEntryRow(viewModel: DebtViewModel, entry: DebtEntry, currency: String) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                viewModel.requestDeleteEntry(entry.id)
                true
            } else false
        },
    )
    val editing = viewModel.entryEditId == entry.id

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Filled.Delete, contentDescription = "Видалити", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(end = 20.dp))
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
                        OutlinedTextField(value = date, onValueChange = { date = it; viewModel.updateEntryDate(entry, it) }, singleLine = true, label = { Text("Дата") }, modifier = Modifier.weight(1f))
                    }
                    TextButton(onClick = { viewModel.toggleEntryEdit(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${entry.amount} $currency", fontWeight = FontWeight.SemiBold)
                            Text("Залишок: ${formatMoney(entry.balance)} $currency · ${entry.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            OutlinedTextField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Дата") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::closeNewEntrySheet) { Text("Скасувати") }
                TextButton(onClick = { viewModel.addEntry(amount, balance, date) }) { Text("Додати") }
            }
        }
    }
}
