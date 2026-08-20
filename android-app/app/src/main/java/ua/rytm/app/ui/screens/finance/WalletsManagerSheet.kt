package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository

// Implements FINANCE_SCREEN_SPEC.md §10 — 1:1 with js/settings-managers.js's
// wallets-modal: inline-editable name, currency dropdown, color swatch
// (fixed at creation, no picker yet — disclosed in the spec), two-guard
// delete (last-wallet / in-use), matching UK copy.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsManagerSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: WalletsManagerViewModel = viewModel(factory = WalletsManagerViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Гаманці", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            viewModel.errorMessage?.let { message ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            }

            if (viewModel.wallets.isEmpty()) {
                Text("Немає гаманців", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.wallets.forEach { wallet ->
                WalletRow(
                    wallet = wallet,
                    onRename = { viewModel.renameWallet(wallet, it) },
                    onCurrencyChange = { viewModel.changeCurrency(wallet, it) },
                    onDelete = { viewModel.requestDelete(wallet.id) },
                )
            }

            TextButton(onClick = viewModel::addWallet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Додати гаманець")
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Видалити гаманець") },
            text = { Text("Видалити цей гаманець?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Скасувати") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletRow(
    wallet: Wallet,
    onRename: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var nameText by remember(wallet.id) { mutableStateOf(wallet.name) }
    var currencyExpanded by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(wallet.colorHex)))

        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("Назва") },
            // Live-save on every keystroke, matching the PWA's inline <input> with no separate "Save".
            trailingIcon = null,
        )
        // Commit on unfocus-equivalent: since Compose has no cheap onBlur here, save as the user types
        // (debounce-free, matches the field's small size/low write cost).
        androidx.compose.runtime.LaunchedEffect(nameText) {
            kotlinx.coroutines.delay(400)
            if (nameText != wallet.name) onRename(nameText)
        }

        ExposedDropdownMenuBox(expanded = currencyExpanded, onExpandedChange = { currencyExpanded = it }, modifier = Modifier.size(width = 110.dp, height = 56.dp)) {
            OutlinedTextField(
                value = wallet.currency,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                CURRENCY_LIST.forEach { code ->
                    DropdownMenuItem(text = { Text(code) }, onClick = { onCurrencyChange(code); currencyExpanded = false })
                }
            }
        }

        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
    }
}
