package ua.rytm.app.ui.screens.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import ua.rytm.app.ui.components.SwipeOpenThreshold
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile

// Implements SHOPPING_SCREEN_SPEC.md end to end: chip stats, add form,
// sorted checklist (unbought first), clear-bought with confirm, empty
// state. Room-backed from the start (ShoppingRepository), no sample-only
// UI-layer step — the pattern is now proven from Finance (Steps 2-5).
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = viewModel(
        factory = ShoppingViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val canEdit = LocalCanEditProfile.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ChipStatsRow(remaining = viewModel.remainingCount, bought = viewModel.boughtCount) }
        if (canEdit) item { AddItemForm(viewModel) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Список покупок", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (canEdit && viewModel.boughtCount > 0) {
                    TextButton(onClick = viewModel::requestClearBought) { Text("Очистити куплені", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        val sorted = viewModel.sortedItems
        if (sorted.isEmpty()) {
            item { ShoppingEmptyState() }
        } else {
            items(sorted, key = { it.id }) { item ->
                ShoppingRow(item = item, canEdit = canEdit, onToggle = { viewModel.toggle(item, it) }, onDelete = { viewModel.delete(item.id) })
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
    viewModel.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("Не вдалося зберегти") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::consumeError) { Text("Гаразд") } },
        )
    }
}

@Composable
private fun ChipStatsRow(remaining: Int, bought: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(Icons.Filled.Checklist, remaining.toString(), "Залишилось", Modifier.weight(1f))
        StatChip(Icons.Filled.CheckCircle, bought.toString(), "Куплено", Modifier.weight(1f))
    }
}

// Matches the PWA's .chip-stat/.chip-stat-icon: a pill with a small circular
// purple-gradient icon badge, not a plain Card — same treatment Finance/
// Shifts/Debt's chip stats got (steps 38-39 and the Debt visual-parity pass).
@Composable
private fun StatChip(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(999.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
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
private fun AddItemForm(viewModel: ShoppingViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = viewModel.nameInput,
            onValueChange = viewModel::onNameChange,
            label = { Text("Назва товару") },
            placeholder = { Text("напр. Молоко") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            isError = viewModel.nameInvalid,
            supportingText = if (viewModel.nameInvalid) ({ Text(stringResource(R.string.shopping_name_required)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = viewModel.qtyInput,
            onValueChange = viewModel::onQtyChange,
            label = { Text("К-сть") },
            placeholder = { Text("1") },
            modifier = Modifier.width(108.dp),
            singleLine = true,
            isError = viewModel.quantityInvalid,
            supportingText = if (viewModel.quantityInvalid) ({ Text(stringResource(R.string.shopping_quantity_invalid)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addItem() }),
        )
        FilledTonalButton(onClick = viewModel::addItem, enabled = !viewModel.saving && viewModel.nameInput.isNotBlank()) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingRow(item: ShoppingItem, canEdit: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { swipeThresholdPx }, confirmValueChange = { value ->
        if (canEdit && value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onError, modifier = Modifier.padding(end = 20.dp))
            }
        },
    ) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.done, onCheckedChange = if (canEdit) onToggle else null, enabled = canEdit)
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
            if (canEdit) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
        }
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
