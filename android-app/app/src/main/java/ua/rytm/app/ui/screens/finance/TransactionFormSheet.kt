package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// Implements FINANCE_SCREEN_SPEC.md §9 — fields, labels, and validation
// mirror js/finance.js's setFinanceType()/readTransactionForm() and
// js/tx-validation.js exactly. Deliberately out of scope: receipt scan,
// tags (no Tag entity ported yet) — see the spec's "not in Step 3" note.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(vm: FinanceViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = vm::closeSheet, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = if (vm.editingTxId != null) "Редагування операції" else "Нова операція",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (vm.editingTxId != null) {
                    TextButton(onClick = vm::closeSheet) { Text("Скасувати") }
                }
            }

            TypeSegmentedRow(vm)

            val walletLabel = when (vm.formType) {
                TxType.INCOME -> "Гаманець"
                TxType.EXPENSE -> "Звідки списати"
                TxType.TRANSFER -> "Звідки переказати"
            }
            WalletDropdown(label = walletLabel, wallets = vm.wallets, selectedId = vm.formWalletId, onSelect = vm::onFormWalletChange)

            if (vm.formType == TxType.TRANSFER) {
                WalletDropdown(label = "Куди переказати", wallets = vm.wallets, selectedId = vm.formTargetWalletId, onSelect = vm::onFormTargetWalletChange)
                vm.formTransferHint?.let { (text, isWarning) ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.formAmountText,
                    onValueChange = vm::onFormAmountChange,
                    label = { Text("Сума (${currencySymbol(vm.formWalletCurrency)})") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(50.0, 100.0, 200.0, 500.0)) { amount ->
                        AssistChip(onClick = { vm.setFormAmount(amount) }, label = { Text(amount.toLong().toString()) })
                    }
                }
            }

            if (vm.formType != TxType.TRANSFER) {
                val categories = vm.categoriesByType[vm.formType].orEmpty()
                if (categories.isNotEmpty()) {
                    DropdownField(
                        label = "Категорія",
                        options = categories,
                        selected = vm.formCategory ?: categories.first(),
                        onSelect = vm::onFormCategoryChange,
                    )
                }
                if (vm.formSubcategoryOptions.isNotEmpty()) {
                    DropdownField(
                        label = "Підкатегорія",
                        options = listOf("—") + vm.formSubcategoryOptions,
                        selected = vm.formSubcategory ?: "—",
                        onSelect = { vm.onFormSubcategoryChange(it.takeIf { s -> s != "—" }) },
                    )
                }
            }

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = vm.formDate,
                    onValueChange = vm::onFormDateChange,
                    label = { Text("Дата") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = vm::setFormDateToday) { Text("Сьогодні") }
            }

            Column {
                OutlinedTextField(
                    value = vm.formComment,
                    onValueChange = vm::onFormCommentChange,
                    label = { Text("Коментар") },
                    placeholder = { Text("Деталі операції...") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${vm.formComment.length}/$TX_COMMENT_MAX",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (vm.formComment.length > TX_COMMENT_MAX * 0.9) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            vm.formError?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(onClick = vm::submitForm, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.editingTxId != null) "Зберегти зміни" else "Додати запис")
            }
        }
    }
}

@Composable
private fun TypeSegmentedRow(vm: FinanceViewModel) {
    val options = listOf(
        Triple(TxType.INCOME, "+ Дохід", Icons.Filled.ArrowUpward),
        Triple(TxType.EXPENSE, "− Витрата", Icons.Filled.ArrowDownward),
        Triple(TxType.TRANSFER, "⇄ Переказ", Icons.Filled.SwapHoriz),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, label, icon) ->
            SegmentedButton(
                selected = vm.formType == type,
                onClick = { vm.onFormTypeChange(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            ) {
                Text(label)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
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
