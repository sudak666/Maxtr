package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSpacing

/**
 * «Скільки зараз грошей?»: one field per wallet, saved as opening balances.
 * Offered on a fresh profile before the first expense and from the empty
 * history; an empty field leaves that wallet as it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartingBalanceSheet(wallets: List<Wallet>, onSave: (Map<String, Double>) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val texts = remember { mutableStateMapOf<String, String>().apply { wallets.forEach { w -> put(w.id, if (w.openingBalance == 0.0) "" else formatMoney(w.openingBalance)) } } }
    val invalid = texts.values.any { it.isNotBlank() && parseMoneyInput(it) == null }
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
                RytmSheetTitle(stringResource(R.string.start_balance_title))
                Text(stringResource(R.string.start_balance_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                wallets.forEach { w ->
                    val text = texts[w.id].orEmpty()
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> texts[w.id] = v.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' } },
                        label = { Text("${localizedDomainText(w.name)}, ${currencySymbol(w.currency)}") },
                        placeholder = { Text("0") },
                        isError = text.isNotBlank() && parseMoneyInput(text) == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = RytmSpacing.md), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget), shape = RoundedCornerShape(RytmRadii.Row)) {
                    Text(stringResource(R.string.action_skip))
                }
                Button(
                    onClick = {
                        onSave(texts.mapNotNull { (id, t) -> parseMoneyInput(t)?.let { id to it } }.toMap())
                        onDismiss()
                    },
                    enabled = !invalid,
                    modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
