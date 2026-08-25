package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import ua.rytm.app.data.ReceiptOcrRepository
import ua.rytm.app.ui.components.DatePickerField
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.BlueDark2
import ua.rytm.app.ui.theme.BlueLight2
import ua.rytm.app.ui.theme.GreenDark2
import ua.rytm.app.ui.theme.GreenLight2
import ua.rytm.app.ui.theme.RedDark2
import ua.rytm.app.ui.theme.RedLight2
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.theme.RytmDimens

// Implements FINANCE_SCREEN_SPEC.md §9 — fields, labels, and validation
// mirror js/finance.js's setFinanceType()/readTransactionForm() and
// js/tx-validation.js exactly. Tags are now real (Tag entity + FilterChip
// multi-select, see FinanceViewModel.formSelectedTagIds/toggleFormTag()).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormSheet(vm: FinanceViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocr = remember { ReceiptOcrRepository() }
    var ocrBusy by rememberSaveable { mutableStateOf(false) }
    var ocrMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val ocrFound = stringResource(R.string.receipt_found)
    val ocrNotFound = stringResource(R.string.receipt_not_found)
    val ocrFailed = stringResource(R.string.receipt_failed)
    fun processReceipt(uri: android.net.Uri) {
        if (ocrBusy) return
        scope.launch {
            ocrBusy = true
            ocrMessage = null
            try {
                val result = ocr.scan(context, uri)
                result.amount?.let { vm.onFormAmountChange(it.toString()) }
                result.date?.let(vm::onFormDateChange)
                ocrMessage = if (result.amount != null || result.date != null) ocrFound else ocrNotFound
            } catch (e: Exception) {
                ocrMessage = ocrFailed
            } finally {
                ocrBusy = false
            }
        }
    }
    var scanSourceOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::processReceipt) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) cameraUri?.let(::processReceipt) }

    ModalBottomSheet(onDismissRequest = vm::closeSheet, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RytmSheetTitle(
                    stringResource(if (vm.editingTxId != null) R.string.transaction_edit_title else R.string.transaction_new_title),
                    modifier = Modifier.weight(1f),
                )
                // Always offered: it used to appear only while editing, so a
                // half-filled new transaction had no visible way back.
                TextButton(onClick = vm::closeSheet) { Text(stringResource(R.string.action_cancel)) }
            }

            TypeSegmentedRow(vm)

            if (vm.formType != TxType.TRANSFER) {
                // One secondary action, not two filled Buttons competing with
                // (and sitting above) the form's real primary action. Source
                // choice moved into a small sheet, the platform-canonical shape.
                OutlinedButton(
                    onClick = { scanSourceOpen = true },
                    enabled = !ocrBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = RytmDimens.TouchTarget),
                ) {
                    Icon(Icons.Filled.DocumentScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.receipt_scan))
                }
                if (scanSourceOpen) {
                    AlertDialog(
                        onDismissRequest = { scanSourceOpen = false },
                        title = { Text(stringResource(R.string.receipt_scan)) },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        scanSourceOpen = false
                                        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
                                        cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "receipt-${System.currentTimeMillis()}.jpg"))
                                        cameraLauncher.launch(cameraUri!!)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.receipt_camera), modifier = Modifier.weight(1f))
                                }
                                TextButton(
                                    onClick = { scanSourceOpen = false; galleryLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.receipt_gallery), modifier = Modifier.weight(1f))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { scanSourceOpen = false }) { Text(stringResource(R.string.action_cancel)) }
                        },
                    )
                }
                if (ocrBusy) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(20.dp)); Text(stringResource(R.string.receipt_scanning)) }
                ocrMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            val walletLabel = when (vm.formType) {
                TxType.INCOME -> stringResource(R.string.wallet_label)
                TxType.EXPENSE -> stringResource(R.string.transaction_wallet_expense)
                TxType.TRANSFER -> stringResource(R.string.transaction_wallet_source)
            }
            WalletDropdown(label = walletLabel, wallets = vm.wallets, selectedId = vm.formWalletId, onSelect = vm::onFormWalletChange)

            if (vm.formType == TxType.TRANSFER) {
                WalletDropdown(label = stringResource(R.string.transaction_wallet_target), wallets = vm.wallets, selectedId = vm.formTargetWalletId, onSelect = vm::onFormTargetWalletChange)
                vm.formTransferHint?.let { hint ->
                    Text(
                        text = if (hint.isWarning) stringResource(R.string.transaction_wallet_same) else stringResource(R.string.transaction_conversion_hint, hint.sourceText, hint.targetText),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hint.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val amountInvalid = vm.formErrorField == TxFormField.AMOUNT
                OutlinedTextField(
                    value = vm.formAmountText,
                    onValueChange = vm::onFormAmountChange,
                    label = { Text(stringResource(R.string.transaction_amount_currency, currencySymbol(vm.formWalletCurrency))) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    // The error used to be one generic line at the bottom of
                    // the form, with no field highlighted at all.
                    isError = amountInvalid,
                    supportingText = vm.formErrorRes.takeIf { amountInvalid }?.let { { Text(stringResource(it)) } },
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
                        label = stringResource(R.string.tx_category),
                        options = categories,
                        selected = vm.formCategory ?: categories.first(),
                        onSelect = vm::onFormCategoryChange,
                    )
                }
                if (vm.formSubcategoryOptions.isNotEmpty()) {
                    DropdownField(
                        label = stringResource(R.string.transaction_subcategory),
                        options = listOf("—") + vm.formSubcategoryOptions,
                        selected = vm.formSubcategory ?: "—",
                        onSelect = { vm.onFormSubcategoryChange(it.takeIf { s -> s != "—" }) },
                    )
                }
            }

            DatePickerField(value = vm.formDate, onValueChange = vm::onFormDateChange, label = stringResource(R.string.date_label), modifier = Modifier.fillMaxWidth(), allowEmpty = false)
            val commentInvalid = vm.formErrorField == TxFormField.COMMENT
            OutlinedTextField(
                value = vm.formComment,
                onValueChange = vm::onFormCommentChange,
                label = { Text(stringResource(R.string.comment_label)) },
                placeholder = { Text(stringResource(R.string.transaction_comment_hint)) },
                isError = commentInvalid,
                // The counter used to be a loose Text under the field: not
                // aligned to M3's own supporting-text metrics, and not read by
                // TalkBack as part of the field.
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(vm.formErrorRes?.takeIf { commentInvalid }?.let { stringResource(it) } ?: "")
                        Text("${vm.formComment.length}/$TX_COMMENT_MAX")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.submitForm() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (vm.tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.tags) { tag ->
                        androidx.compose.material3.FilterChip(
                            selected = tag.id in vm.formSelectedTagIds,
                            onClick = { vm.toggleFormTag(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            // Only the errors that do not belong to a visible field land here
            // now; everything else is marked on the field itself.
            if (vm.formErrorField == null || vm.formErrorField == TxFormField.WALLET ||
                vm.formErrorField == TxFormField.TARGET_WALLET || vm.formErrorField == TxFormField.CATEGORY ||
                vm.formErrorField == TxFormField.DATE
            ) {
                vm.formErrorRes?.let { errorRes ->
                    Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(onClick = vm::submitForm, enabled = !vm.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (vm.isSaving) R.string.action_saving else if (vm.editingTxId != null) R.string.action_save_changes else R.string.transaction_add))
            }
        }
    }
}

@Composable
private fun TypeSegmentedRow(vm: FinanceViewModel) {
    // Was a local isDark check with its own color pairs; now the shared
    // semantic layer, whose light tones also clear 4.5:1 against this
    // segment's own tinted wash (#059669 on it was 3.43:1).
    val isDark = RytmSemantic.isDark
    val options = listOf(
        TransactionTypeOption(TxType.INCOME, stringResource(R.string.tx_income), Icons.Filled.ArrowUpward, RytmSemantic.income),
        TransactionTypeOption(TxType.EXPENSE, stringResource(R.string.tx_expense), Icons.Filled.ArrowDownward, RytmSemantic.expense),
        TransactionTypeOption(TxType.TRANSFER, stringResource(R.string.tx_transfer), Icons.Filled.SwapHoriz, if (isDark) BlueDark2 else BlueLight2),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = vm.formType == option.type
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(RytmRadii.Input))
                    .background(option.color.copy(alpha = if (isSelected) 0.20f else 0.08f))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = option.color.copy(alpha = if (isSelected) 0.75f else 0.28f),
                        shape = RoundedCornerShape(RytmRadii.Input),
                    )
                    .semantics { selected = isSelected }
                    .clickable(role = Role.RadioButton) { vm.onFormTypeChange(option.type) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = option.label,
                    color = option.color,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class TransactionTypeOption(
    val type: TxType,
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletDropdown(label: String, wallets: List<Wallet>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selected = wallets.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${localizedDomainText(it.name)} (${it.currency})" } ?: "",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = localizedDomainText(selected),
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
