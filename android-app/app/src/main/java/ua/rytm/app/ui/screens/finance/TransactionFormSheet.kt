package ua.rytm.app.ui.screens.finance
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.components.rememberRytmHaptics
import ua.rytm.app.ui.components.RytmModalBottomSheet
import kotlinx.coroutines.CancellationException
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
import ua.rytm.app.ui.components.fadingEdges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.ArrowDownward
import ua.rytm.app.ui.icons.ArrowUpward
import ua.rytm.app.ui.icons.CameraAlt
import ua.rytm.app.ui.icons.DocumentScanner
import ua.rytm.app.ui.icons.Mic
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.PhotoLibrary
import ua.rytm.app.ui.icons.SwapHoriz

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
                result.amount?.let { vm.form.onAmountChange(it.toString()) }
                result.date?.let(vm.form::onDateChange)
                ocrMessage = if (result.amount != null || result.date != null) ocrFound else ocrNotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ocrMessage = ocrFailed
            } finally {
                ocrBusy = false
            }
        }
    }
    var scanSourceOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val submitHaptics = rememberRytmHaptics()
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val formLocale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val decimalSeparator = remember(formLocale) { java.text.DecimalFormatSymbols.getInstance(formLocale).decimalSeparator }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(::processReceipt) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) cameraUri?.let(::processReceipt) }

    RytmModalBottomSheet(onDismissRequest = vm.form::close, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RytmSheetTitle(
                    stringResource(if (vm.form.editingTxId != null) R.string.transaction_edit_title else R.string.transaction_new_title),
                    modifier = Modifier.weight(1f),
                )
                // Always offered: it used to appear only while editing, so a
                // half-filled new transaction had no visible way back.
                TextButton(onClick = vm.form::close) { Text(stringResource(R.string.action_cancel)) }
            }

            TypeSegmentedRow(vm)

            if (vm.form.type != TxType.TRANSFER) {
                // One secondary action, not two filled Buttons competing with
                // (and sitting above) the form's real primary action. Source
                // choice moved into a small sheet, the platform-canonical shape.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    OutlinedButton(
                        onClick = { scanSourceOpen = true },
                        enabled = !ocrBusy,
                        modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                        shape = RoundedCornerShape(RytmRadii.Row),
                    ) {
                        if (!largeFont) {
                            Icon(RytmIcons.DocumentScanner, contentDescription = null)
                            Spacer(Modifier.width(RytmSpacing.sm))
                        }
                        ua.rytm.app.ui.components.FitText(stringResource(R.string.receipt_scan_short), style = MaterialTheme.typography.labelLarge)
                    }
                    // A new transaction only: one line or voice re-fills the whole form.
                    if (vm.form.editingTxId == null) {
                        OutlinedButton(
                            onClick = { vm.quickEntryOpen = true },
                            modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                            shape = RoundedCornerShape(RytmRadii.Row),
                        ) {
                            if (!largeFont) {
                                Icon(RytmIcons.Mic, contentDescription = null)
                                Spacer(Modifier.width(RytmSpacing.sm))
                            }
                            ua.rytm.app.ui.components.FitText(stringResource(R.string.quick_entry_button), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (scanSourceOpen) {
                    // Cancel used to live in AlertDialog's own confirmButton
                    // slot — M3 right-aligns that in its own padded footer,
                    // which read as disconnected from the Camera/Gallery
                    // options above it (reported live, screenshot: a big gap
                    // with Cancel floating alone at the bottom-right). This
                    // is a source picker, not a message+confirm dialog, so
                    // Cancel now joins the same option list as a third,
                    // visually muted row instead — one cohesive block, no
                    // dead space. confirmButton stays required by the API
                    // but renders nothing.
                    AlertDialog(
                        onDismissRequest = { scanSourceOpen = false },
                        title = { Text(stringResource(R.string.receipt_scan)) },
                        text = {
                            Column {
                                TextButton(
                                    onClick = {
                                        scanSourceOpen = false
                                        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "receipt-${System.currentTimeMillis()}.jpg"))
                                        cameraUri = uri
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(RytmIcons.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.width(RytmSpacing.sm))
                                    Text(stringResource(R.string.receipt_camera), modifier = Modifier.weight(1f))
                                }
                                TextButton(
                                    onClick = { scanSourceOpen = false; galleryLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(RytmIcons.PhotoLibrary, contentDescription = null)
                                    Spacer(Modifier.width(RytmSpacing.sm))
                                    Text(stringResource(R.string.receipt_gallery), modifier = Modifier.weight(1f))
                                }
                                HorizontalDivider(Modifier.padding(vertical = RytmSpacing.xs))
                                TextButton(
                                    onClick = { scanSourceOpen = false },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                ) {
                                    Text(stringResource(R.string.action_cancel), modifier = Modifier.weight(1f))
                                }
                            }
                        },
                        confirmButton = {},
                    )
                }
                if (ocrBusy) Row(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) { CircularProgressIndicator(Modifier.size(20.dp)); Text(stringResource(R.string.receipt_scanning)) }
                ocrMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            val walletLabel = when (vm.form.type) {
                TxType.INCOME -> stringResource(R.string.wallet_label)
                TxType.EXPENSE -> stringResource(R.string.transaction_wallet_expense)
                TxType.TRANSFER -> stringResource(R.string.transaction_wallet_source)
            }
            WalletDropdown(label = walletLabel, wallets = vm.wallets, selectedId = vm.form.walletId, onSelect = vm.form::onWalletChange)
            val balanceAfter = vm.formBalanceAfter
            balanceAfter?.let { BalanceAfterLine(it.source, it.sourceCurrency) }

            if (vm.form.type == TxType.TRANSFER) {
                WalletDropdown(label = stringResource(R.string.transaction_wallet_target), wallets = vm.wallets, selectedId = vm.form.targetWalletId, onSelect = vm.form::onTargetWalletChange)
                balanceAfter?.target?.let { BalanceAfterLine(it, balanceAfter.targetCurrency ?: "UAH") }
                vm.formTransferHint?.let { hint ->
                    Text(
                        text = if (hint.isWarning) stringResource(R.string.transaction_wallet_same) else stringResource(R.string.transaction_conversion_hint, hint.sourceText, hint.targetText),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hint.isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                val amountInvalid = vm.form.errorField == TxFormField.AMOUNT
                // A new entry starts in the amount field with the keyboard up —
                // the first thing typed is always the sum.
                val amountFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                LaunchedEffect(Unit) {
                    if (vm.form.editingTxId == null && vm.form.amountText.isEmpty()) runCatching { amountFocus.requestFocus() }
                }
                OutlinedTextField(
                    value = vm.form.amountText,
                    onValueChange = vm.form::onAmountChange,
                    label = { Text(stringResource(R.string.transaction_amount_currency, currencySymbol(vm.formWalletCurrency))) },
                    placeholder = { Text("0${decimalSeparator}00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    // The error used to be one generic line at the bottom of
                    // the form, with no field highlighted at all.
                    isError = amountInvalid,
                    supportingText = vm.form.errorRes.takeIf { amountInvalid }?.let { { Text(stringResource(it)) } }
                        ?: vm.formShiftCost?.let { shifts -> { Text(shiftCostText(shifts)) } }
                        ?: vm.formSalaryMatch?.let { (expected, diff) ->
                            {
                                Text(
                                    stringResource(
                                        R.string.salary_match_hint,
                                        ua.rytm.app.ui.maskedAmount("${formatMoney(expected)} ₴"),
                                        ua.rytm.app.ui.maskedAmount((if (diff >= 0) "+" else "−") + formatMoney(kotlin.math.abs(diff)) + " ₴"),
                                    ),
                                )
                            }
                        },
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus),
                    singleLine = true,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    items(listOf(50.0, 100.0, 200.0, 500.0)) { amount ->
                        AssistChip(onClick = { vm.form.setAmount(amount) }, label = { Text(amount.toLong().toString()) })
                    }
                }
            }

            if (vm.form.type != TxType.TRANSFER) {
                val categories = vm.categoriesByType[vm.form.type].orEmpty()
                val frequent = vm.formFrequentCategories
                if (frequent.size >= 2) {
                    val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyRow(state = fadeState, modifier = Modifier.fadingEdges(fadeState), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                        items(frequent, key = { it }) { cat ->
                            androidx.compose.material3.FilterChip(
                                selected = (vm.form.category ?: categories.firstOrNull()) == cat,
                                onClick = { vm.form.onCategoryChange(cat) },
                                label = { Text(ua.rytm.app.ui.localizedDomainText(cat)) },
                            )
                        }
                    }
                }
                if (categories.isNotEmpty()) {
                    DropdownField(
                        label = stringResource(R.string.tx_category),
                        options = categories,
                        selected = vm.form.category ?: categories.first(),
                        onSelect = vm.form::onCategoryChange,
                    )
                }
                if (vm.formSubcategoryOptions.isNotEmpty()) {
                    DropdownField(
                        label = stringResource(R.string.transaction_subcategory),
                        options = listOf("—") + vm.formSubcategoryOptions,
                        selected = vm.form.subcategory ?: "—",
                        onSelect = { vm.form.onSubcategoryChange(it.takeIf { s -> s != "—" }) },
                    )
                }
                if (vm.form.canSplit && categories.size >= 2) SplitPartsEditor(vm, categories)
            }

            DatePickerField(value = vm.form.date, onValueChange = vm.form::onDateChange, label = stringResource(R.string.date_label), modifier = Modifier.fillMaxWidth(), allowEmpty = false)
            val commentInvalid = vm.form.errorField == TxFormField.COMMENT
            OutlinedTextField(
                value = vm.form.comment,
                onValueChange = vm.form::onCommentChange,
                label = { Text(stringResource(R.string.comment_label)) },
                placeholder = { Text(stringResource(R.string.transaction_comment_hint)) },
                isError = commentInvalid,
                // The counter used to be a loose Text under the field: not
                // aligned to M3's own supporting-text metrics, and not read by
                // TalkBack as part of the field.
                supportingText = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(vm.form.errorRes?.takeIf { commentInvalid }?.let { stringResource(it) } ?: "")
                        // Only near the limit: an always-on 0/500 is noise in a form opened daily.
                        if (vm.form.comment.length > TX_COMMENT_MAX - 100) Text("${vm.form.comment.length}/$TX_COMMENT_MAX")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); vm.submitForm() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (vm.tags.isNotEmpty()) {
                val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyRow(state = fadeState, modifier = Modifier.fadingEdges(fadeState), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    items(vm.tags) { tag ->
                        androidx.compose.material3.FilterChip(
                            selected = tag.id in vm.form.selectedTagIds,
                            onClick = { vm.form.toggleTag(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }

            // Only the errors that do not belong to a visible field land here
            // now; everything else is marked on the field itself.
            if (vm.form.errorField == null || vm.form.errorField == TxFormField.WALLET ||
                vm.form.errorField == TxFormField.TARGET_WALLET || vm.form.errorField == TxFormField.CATEGORY ||
                vm.form.errorField == TxFormField.DATE
            ) {
                vm.form.errorRes?.let { errorRes ->
                    Text(stringResource(errorRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            Button(onClick = { submitHaptics.confirm(); vm.submitForm() }, enabled = !vm.isSaving, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(RytmRadii.Row)) {
                Text(stringResource(if (vm.isSaving) R.string.action_saving else if (vm.form.editingTxId != null) R.string.action_save_changes else R.string.transaction_add))
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
    val typeHaptics = rememberRytmHaptics()
    val largeFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val options = listOf(
        TransactionTypeOption(TxType.INCOME, stringResource(R.string.tx_income), RytmIcons.ArrowUpward, RytmSemantic.income),
        TransactionTypeOption(TxType.EXPENSE, stringResource(R.string.tx_expense), RytmIcons.ArrowDownward, RytmSemantic.expense),
        TransactionTypeOption(TxType.TRANSFER, stringResource(R.string.tx_transfer), RytmIcons.SwapHoriz, if (isDark) BlueDark2 else BlueLight2),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
    ) {
        options.forEach { option ->
            val isSelected = vm.form.type == option.type
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
                    .clickable(role = Role.RadioButton) {
                        if (!isSelected) typeHaptics.tick()
                        vm.form.onTypeChange(option.type)
                    }
                    .padding(horizontal = RytmSpacing.sm, vertical = RytmSpacing.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // At a large system font the arrow ate the label ("Дохі"): the
                // label alone carries the meaning, and it shrinks rather than cut.
                if (!largeFont) {
                    Icon(option.icon, contentDescription = null, tint = option.color, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                }
                ua.rytm.app.ui.components.FitText(option.label, style = MaterialTheme.typography.labelLarge, color = option.color, fontWeight = FontWeight.Bold)
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

/** "Баланс після: 417,09 ₴" under a wallet field, as a bank statement shows it. */
/**
 * One receipt, several categories: each part becomes its own entry with the
 * same date/wallet/comment/tags; the form's own category keeps the rest.
 * Plain entries on purpose — no new field, so the PWA and old builds read them as-is.
 */
@Composable
private fun SplitPartsEditor(vm: FinanceViewModel, categories: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
        vm.form.splits.forEachIndexed { index, part ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                Box(Modifier.weight(1.3f)) {
                    DropdownField(
                        label = stringResource(R.string.tx_category),
                        options = categories,
                        selected = part.category.takeIf { it in categories } ?: categories.first(),
                        onSelect = { vm.form.updateSplitPart(index, part.copy(category = it)) },
                    )
                }
                OutlinedTextField(
                    value = part.amountText,
                    onValueChange = { vm.form.updateSplitPart(index, part.copy(amountText = it)) },
                    label = { Text(currencySymbol(vm.formWalletCurrency)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.form.removeSplitPart(index) }, modifier = Modifier.size(RytmDimens.TouchTarget)) {
                    Icon(RytmIcons.Close, contentDescription = stringResource(R.string.split_remove))
                }
            }
        }
        vm.form.splitRemainder?.let { rest ->
            val main = ua.rytm.app.ui.localizedDomainText(vm.form.category ?: categories.first())
            Text(
                stringResource(R.string.split_remainder, main, ua.rytm.app.ui.maskedAmount("${formatMoney(rest)} ${currencySymbol(vm.formWalletCurrency)}")),
                style = MaterialTheme.typography.bodySmall,
                color = if (rest > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = vm.form::addSplitPart, modifier = Modifier.heightIn(min = RytmDimens.TouchTarget)) {
            Icon(RytmIcons.Add, contentDescription = null)
            Spacer(Modifier.width(RytmSpacing.sm))
            Text(stringResource(if (vm.form.splits.isEmpty()) R.string.split_add else R.string.split_add_part))
        }
    }
}

@Composable
private fun BalanceAfterLine(balance: Double, currency: String) {
    val sign = if (balance < 0) "−" else ""
    Text(
        text = stringResource(R.string.transaction_balance_after, ua.rytm.app.ui.maskedAmount("$sign${formatMoney(kotlin.math.abs(balance))} ${currencySymbol(currency)}")),
        style = MaterialTheme.typography.bodySmall,
        color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = RytmSpacing.lg),
    )
}

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
            singleLine = true,
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

/** "≈ 1,4 зміни роботи" / "≈ 3 зміни роботи" — whole numbers take the plural form. */
@Composable
private fun shiftCostText(shifts: Double): String {
    val rounded = Math.round(shifts * 10) / 10.0
    return when {
        rounded < 0.1 -> stringResource(R.string.transaction_shift_cost_tiny)
        rounded == Math.floor(rounded) -> androidx.compose.ui.res.pluralStringResource(R.plurals.transaction_shift_cost_whole, rounded.toInt(), rounded.toInt())
        else -> stringResource(R.string.transaction_shift_cost, java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(rounded))
    }
}
