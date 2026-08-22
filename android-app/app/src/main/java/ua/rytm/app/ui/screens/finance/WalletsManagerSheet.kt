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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.FinanceSyncRepository

// Implements FINANCE_SCREEN_SPEC.md §10 — 1:1 with js/settings-managers.js's
// wallets-modal: inline-editable name, currency dropdown, palette picker, two-guard
// delete (last-wallet / in-use), matching UK copy.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletsManagerSheet(
    repository: FinanceRepository,
    syncRepository: FinanceSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: WalletsManagerViewModel = viewModel(
        key = "wallets-$uid-$profileId",
        factory = WalletsManagerViewModel.factory(repository, syncRepository, uid, profileId),
    ),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.wallets_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            viewModel.errorMessageRes?.let { messageRes ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = null) }
                }
            }

            if (viewModel.wallets.isEmpty()) {
                Text(stringResource(R.string.wallets_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.wallets.forEach { wallet ->
                WalletRow(
                    wallet = wallet,
                    onRename = { viewModel.renameWallet(wallet, it) },
                    onCurrencyChange = { viewModel.changeCurrency(wallet, it) },
                    onColorChange = { viewModel.changeColor(wallet, it) },
                    onDelete = { viewModel.requestDelete(wallet.id) },
                )
            }

            val newWalletName = stringResource(R.string.wallet_new_default)
            TextButton(onClick = { viewModel.addWallet(newWalletName) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.wallet_add))
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.wallet_delete_title)) },
            text = { Text(stringResource(R.string.wallet_delete_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletRow(
    wallet: Wallet,
    onRename: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    var nameText by remember(wallet.id) { mutableStateOf(wallet.name) }
    var currencyExpanded by remember { mutableStateOf(false) }
    var colorExpanded by remember { mutableStateOf(false) }
    val colorDescription = stringResource(R.string.wallet_color)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            IconButton(onClick = { colorExpanded = true }, modifier = Modifier.size(40.dp).semantics { contentDescription = colorDescription }) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(wallet.colorHex)))
            }
            DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                PALETTE.chunked(4).forEach { colors ->
                    Row {
                        colors.forEach { color ->
                            IconButton(onClick = { onColorChange(color); colorExpanded = false }, modifier = Modifier.semantics { contentDescription = colorDescription }) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(color)))
                            }
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text(stringResource(R.string.field_name)) },
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

        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
    }
}
