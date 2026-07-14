package ua.zminka.app.ui.debt

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.zminka.app.data.model.Debt
import ua.zminka.app.data.model.DebtEntry
import java.util.Locale

@Composable
fun DebtScreen(viewModel: DebtViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var showAddDebtDialog by remember { mutableStateOf(false) }
    var showAddEntrySheet by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (state.currentDebt != null) {
                FloatingActionButton(onClick = { showAddEntrySheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Новий платіж")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DebtChipRow(
                debts = state.debts,
                currentDebtId = state.currentDebtId,
                onSelect = viewModel::switchDebt,
                onAddClick = { showAddDebtDialog = true },
            )
            val debt = state.currentDebt
            if (debt == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Немає жодного боргу — додайте перший",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                HeroBalance(balance = state.balance, currency = debt.currency)
                StatChipRow(startAmount = debt.startAmount, paid = state.paid, count = debt.entries.size, currency = debt.currency)
                EntryList(entries = debt.entries, currency = debt.currency)
            }
        }
    }

    if (showAddDebtDialog) {
        AddDebtDialog(
            onDismiss = { showAddDebtDialog = false },
            onAdd = { name -> viewModel.addDebt(name); showAddDebtDialog = false },
        )
    }
    if (showAddEntrySheet) {
        AddEntryDialog(
            onDismiss = { showAddEntrySheet = false },
            onAdd = { amount, balance -> viewModel.addEntry(amount, balance); showAddEntrySheet = false },
        )
    }
}

@Composable
private fun DebtChipRow(
    debts: List<Debt>,
    currentDebtId: Long?,
    onSelect: (Long) -> Unit,
    onAddClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(debts, key = { it.id }) { debt ->
            FilterChip(
                selected = debt.id == currentDebtId || (currentDebtId == null && debt == debts.first()),
                onClick = { onSelect(debt.id) },
                label = { Text(debt.name) },
            )
        }
        item {
            FilterChip(selected = false, onClick = onAddClick, label = { Text("+ Додати") })
        }
    }
}

@Composable
private fun HeroBalance(balance: Double, currency: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            "Поточний баланс",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            "${String.format(Locale.US, "%,.2f", balance)} $currency",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun StatChipRow(startAmount: Double, paid: Double, count: Int, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(onClick = {}, label = { Text("Початок: ${String.format(Locale.US, "%,.0f", startAmount)} $currency") })
        SuggestionChip(onClick = {}, label = { Text("Сплачено: ${String.format(Locale.US, "%,.0f", paid)} $currency") })
        SuggestionChip(onClick = {}, label = { Text("$count платежів") })
    }
}

@Composable
private fun EntryList(entries: List<DebtEntry>, currency: String) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Ще немає платежів",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        reverseLayout = true,
    ) {
        items(entries, key = { it.id }) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(entry.amount, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            entry.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        "${String.format(Locale.US, "%,.2f", entry.balance)} $currency",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDebtDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий борг") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Назва") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }) { Text("Додати") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}

@Composable
private fun AddEntryDialog(onDismiss: () -> Unit, onAdd: (String, Double?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новий платіж") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Сума") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Залишок (необов'язково — розрахується сам)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(amount, balanceText.toDoubleOrNull()) }) { Text("Додати") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
