package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.DatePickerField
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.RecurringSyncRepository
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.RytmRadii

// Mirrors js/settings-managers.js's recurring-modal — see
// RecurringManagerViewModel's doc comment for scope. The most field-heavy
// manager sheet in the app so far (type/amount/category/wallet/frequency/
// nextDate/active), same collapsed-summary-row shape as budgets/shift types.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerSheet(
    repository: FinanceRepository,
    syncRepository: RecurringSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: RecurringManagerViewModel = viewModel(
        key = "recurring-$uid-$profileId",
        factory = RecurringManagerViewModel.factory(repository, syncRepository, uid, profileId),
    ),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RytmSheetTitle(stringResource(R.string.recurring_title))

            if (viewModel.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            if (viewModel.rows.isEmpty()) {
                Text(stringResource(R.string.recurring_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.rows.forEach { r ->
                RecurringRow(
                    r = r,
                    expanded = viewModel.expandedId == r.id,
                    categoriesByType = viewModel.categoriesByType,
                    wallets = viewModel.wallets,
                    iconOverride = viewModel.categoryIcons[r.category],
                    onToggleEdit = { viewModel.toggleEdit(r.id) },
                    onTypeChange = { viewModel.updateType(r, it) },
                    onAmountChange = { viewModel.updateAmount(r, it) },
                    onCategoryChange = { viewModel.updateCategory(r, it) },
                    onWalletChange = { viewModel.updateWallet(r, it) },
                    onFrequencyChange = { viewModel.updateFrequency(r, it) },
                    onNextDateChange = { viewModel.updateNextDate(r, it) },
                    onActiveChange = { viewModel.updateActive(r, it) },
                    onDelete = { viewModel.requestDelete(r.id) },
                )
            }

            androidx.compose.material3.Button(onClick = viewModel::addRecurring, modifier = Modifier.fillMaxWidth(), enabled = !viewModel.isSaving, shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.recurring_add))
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = { Text(stringResource(R.string.recurring_delete_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete, enabled = !viewModel.isSaving) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun RecurringRow(
    r: Recurring,
    expanded: Boolean,
    categoriesByType: Map<TxType, List<String>>,
    wallets: List<Wallet>,
    iconOverride: String?,
    onToggleEdit: () -> Unit,
    onTypeChange: (TxType) -> Unit,
    onAmountChange: (Double) -> Unit,
    onCategoryChange: (String) -> Unit,
    onWalletChange: (String) -> Unit,
    onFrequencyChange: (String) -> Unit,
    onNextDateChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val incomeLabel = stringResource(R.string.tx_income)
    val expenseLabel = stringResource(R.string.tx_expense)
    val frequencyOptions = listOf("daily" to stringResource(R.string.frequency_daily), "weekly" to stringResource(R.string.frequency_weekly), "monthly" to stringResource(R.string.frequency_monthly))
    val freqLabel = frequencyOptions.firstOrNull { it.first == r.frequency }?.second ?: stringResource(R.string.frequency_monthly)
    val summary = stringResource(if (r.active) R.string.recurring_summary else R.string.recurring_summary_inactive, localizedDomainText(r.category), r.amount.toInt(), freqLabel, r.nextDate)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Chart),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIconBadge(r.category, iconOverride = iconOverride, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(if (r.type == TxType.INCOME) incomeLabel else expenseLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete, colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }

        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.tx_type),
                    options = listOf(incomeLabel, expenseLabel),
                    selected = if (r.type == TxType.INCOME) incomeLabel else expenseLabel,
                    onSelect = { onTypeChange(if (it == incomeLabel) TxType.INCOME else TxType.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )

                var amountText by remember(r.id, r.amount) { mutableStateOf(if (r.amount == 0.0) "" else r.amount.toString()) }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.amount_label)) },
                )
                LaunchedEffect(amountText) {
                    delay(400)
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    if (parsed != r.amount) onAmountChange(parsed)
                }
            }

            DropdownField(
                label = stringResource(R.string.tx_category),
                options = categoriesByType[r.type].orEmpty(),
                selected = r.category,
                onSelect = onCategoryChange,
            )

            WalletDropdown(label = stringResource(R.string.wallet_label), wallets = wallets, selectedId = r.walletId, onSelect = onWalletChange)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = stringResource(R.string.frequency_label),
                    options = frequencyOptions.map { it.second },
                    selected = freqLabel,
                    onSelect = { label -> frequencyOptions.firstOrNull { it.second == label }?.let { onFrequencyChange(it.first) } },
                    modifier = Modifier.weight(1f),
                )

                DatePickerField(
                    value = r.nextDate,
                    onValueChange = onNextDateChange,
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.recurring_next_date),
                    allowEmpty = false,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = r.active, onCheckedChange = onActiveChange)
                Text(stringResource(R.string.recurring_active), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(localizedDomainText(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletDropdown(label: String, wallets: List<Wallet>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = wallets.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.currency})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            wallets.forEach { wallet ->
                DropdownMenuItem(
                    text = { Text("${localizedDomainText(wallet.name)} (${wallet.currency})") },
                    onClick = { onSelect(wallet.id); expanded = false },
                )
            }
        }
    }
}
