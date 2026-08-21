package ua.rytm.app.ui.screens.finance

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
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository

// Mirrors js/settings-managers.js's recurring-modal — see
// RecurringManagerViewModel's doc comment for scope. The most field-heavy
// manager sheet in the app so far (type/amount/category/wallet/frequency/
// nextDate/active), same collapsed-summary-row shape as budgets/shift types.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringManagerSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: RecurringManagerViewModel = viewModel(factory = RecurringManagerViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Регулярні платежі", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.rows.isEmpty()) {
                Text("Немає регулярних платежів", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            TextButton(onClick = viewModel::addRecurring, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Додати регулярний платіж")
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Видалити регулярний платіж") },
            text = { Text("Видалити цей регулярний платіж?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Скасувати") } },
        )
    }
}

private val FREQUENCY_OPTIONS = listOf("daily" to "Щодня", "weekly" to "Щотижня", "monthly" to "Щомісяця")

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
    val freqLabel = FREQUENCY_OPTIONS.firstOrNull { it.first == r.frequency }?.second ?: "Щомісяця"
    val summary = "${r.category} · ${r.amount.toInt()} грн · $freqLabel · ${r.nextDate}" + if (!r.active) " · на паузі" else ""

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIconBadge(r.category, iconOverride = iconOverride, size = 32.dp)
            Column(Modifier.weight(1f)) {
                Text(if (r.type == TxType.INCOME) "Дохід" else "Витрата", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = "Редагувати")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
        }

        if (expanded) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "Тип",
                    options = listOf("Дохід", "Витрата"),
                    selected = if (r.type == TxType.INCOME) "Дохід" else "Витрата",
                    onSelect = { onTypeChange(if (it == "Дохід") TxType.INCOME else TxType.EXPENSE) },
                    modifier = Modifier.weight(1f),
                )

                var amountText by remember(r.id, r.amount) { mutableStateOf(if (r.amount == 0.0) "" else r.amount.toString()) }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Сума") },
                )
                LaunchedEffect(amountText) {
                    delay(400)
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    if (parsed != r.amount) onAmountChange(parsed)
                }
            }

            DropdownField(
                label = "Категорія",
                options = categoriesByType[r.type].orEmpty(),
                selected = r.category,
                onSelect = onCategoryChange,
            )

            WalletDropdown(label = "Гаманець", wallets = wallets, selectedId = r.walletId, onSelect = onWalletChange)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    label = "Частота",
                    options = FREQUENCY_OPTIONS.map { it.second },
                    selected = freqLabel,
                    onSelect = { label -> FREQUENCY_OPTIONS.firstOrNull { it.second == label }?.let { onFrequencyChange(it.first) } },
                    modifier = Modifier.weight(1f),
                )

                var nextDateText by remember(r.id, r.nextDate) { mutableStateOf(r.nextDate) }
                OutlinedTextField(
                    value = nextDateText,
                    onValueChange = { nextDateText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Наступного разу") },
                )
                LaunchedEffect(nextDateText) {
                    delay(400)
                    if (nextDateText != r.nextDate && nextDateText.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) onNextDateChange(nextDateText)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = r.active, onCheckedChange = onActiveChange)
                Text("Активна", style = MaterialTheme.typography.bodySmall)
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
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
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
                    text = { Text("${wallet.name} (${wallet.currency})") },
                    onClick = { onSelect(wallet.id); expanded = false },
                )
            }
        }
    }
}
