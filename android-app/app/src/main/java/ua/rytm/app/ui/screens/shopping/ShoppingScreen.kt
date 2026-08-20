package ua.rytm.app.ui.screens.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication

// Implements SHOPPING_SCREEN_SPEC.md end to end: chip stats, add form,
// sorted checklist (unbought first), clear-bought with confirm, empty
// state. Room-backed from the start (ShoppingRepository), no sample-only
// UI-layer step — the pattern is now proven from Finance (Steps 2-5).
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = viewModel(
        factory = ShoppingViewModel.factory((LocalContext.current.applicationContext as RytmApplication).shoppingRepository),
    ),
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ChipStatsRow(remaining = viewModel.remainingCount, bought = viewModel.boughtCount) }
        item { AddItemForm(viewModel) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Список покупок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (viewModel.boughtCount > 0) {
                    TextButton(onClick = viewModel::requestClearBought) { Text("Очистити куплені", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        val sorted = viewModel.sortedItems
        if (sorted.isEmpty()) {
            item { ShoppingEmptyState() }
        } else {
            items(sorted, key = { it.id }) { item ->
                ShoppingRow(item = item, onToggle = { viewModel.toggle(item, it) }, onDelete = { viewModel.delete(item.id) })
            }
        }
    }

    if (viewModel.clearConfirmVisible) {
        AlertDialog(
            onDismissRequest = viewModel::cancelClearBought,
            title = { Text("Очистити куплені") },
            text = { Text("Видалити всі куплені товари зі списку?") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearBought) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelClearBought) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun ChipStatsRow(remaining: Int, bought: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(value = remaining, label = "Залишилось", modifier = Modifier.weight(1f))
        StatChip(value = bought, label = "Куплено", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(value: Int, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddItemForm(viewModel: ShoppingViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = viewModel.nameInput,
            onValueChange = viewModel::onNameChange,
            label = { Text("Назва товару") },
            placeholder = { Text("напр. Молоко") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = viewModel.qtyInput,
            onValueChange = viewModel::onQtyChange,
            label = { Text("К-сть") },
            placeholder = { Text("1") },
            modifier = Modifier.size(width = 84.dp, height = 64.dp),
            singleLine = true,
        )
        FilledTonalButton(onClick = viewModel::addItem) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.done, onCheckedChange = onToggle)
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (item.qty > 1) {
                Text(
                    "×${item.qty}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
        }
    }
}

@Composable
private fun ShoppingEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Список порожній", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Додай товари перед походом у магазин і відмічай куплене одним тапом.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
        )
    }
}
