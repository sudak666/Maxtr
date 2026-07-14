package ua.zminka.app.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.zminka.app.data.model.ShoppingItem

@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var nameInput by remember { mutableStateOf("") }
    var qtyInput by remember { mutableStateOf("1") }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AddItemRow(
                name = nameInput,
                qty = qtyInput,
                onNameChange = { nameInput = it },
                onQtyChange = { qtyInput = it },
                onAdd = {
                    viewModel.addItem(nameInput, qtyInput.toIntOrNull() ?: 1)
                    nameInput = ""
                    qtyInput = "1"
                },
            )
            if (state.items.any { it.done }) {
                TextButton(
                    onClick = viewModel::clearBought,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) { Text("Прибрати куплене") }
            }
            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Список покупок порожній",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.sorted, key = { it.id }) { item ->
                        ShoppingRow(item = item, onToggle = { viewModel.toggle(item.id) }, onDelete = { viewModel.delete(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemRow(
    name: String,
    qty: String,
    onNameChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Товар") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = qty,
            onValueChange = onQtyChange,
            label = { Text("К-сть") },
            singleLine = true,
            modifier = Modifier.width(80.dp),
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Додати")
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
            Text(
                "${item.name}${if (item.qty > 1) " ×${item.qty}" else ""}",
                modifier = Modifier.weight(1f),
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.done) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Видалити")
            }
        }
    }
}
