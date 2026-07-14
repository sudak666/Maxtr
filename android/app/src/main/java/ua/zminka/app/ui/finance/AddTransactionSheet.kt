package ua.zminka.app.ui.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ua.zminka.app.data.model.Wallet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mirrors #tx-form-modal (js/finance.js's addTransaction() input fields) —
 * this MVP covers income/expense only (no transfer yet, no
 * category/subcategory pickers backed by AppState.categories — free-text
 * category entry for now).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(
    wallets: List<Wallet>,
    onDismiss: () -> Unit,
    onSave: (
        type: String,
        amount: Double,
        currency: String,
        category: String,
        walletId: String,
        date: String,
        comment: String,
    ) -> Unit,
) {
    var type by remember { mutableStateOf("expense") }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var selectedWallet by remember { mutableStateOf(wallets.firstOrNull()) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Нова операція", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(16.dp))

            Row {
                FilterChip(
                    selected = type == "income",
                    onClick = { type = "income" },
                    label = { Text("Дохід") },
                )
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = type == "expense",
                    onClick = { type = "expense" },
                    label = { Text("Витрата") },
                )
            }
            Spacer(Modifier.size(12.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Сума") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Категорія") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Коментар") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.size(16.dp))

            Button(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull()
                    val wallet = selectedWallet
                    if (amount != null && amount > 0 && wallet != null && category.isNotBlank()) {
                        onSave(type, amount, wallet.currency, category.trim(), wallet.id, today, comment.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = wallets.isNotEmpty(),
            ) {
                Text(if (wallets.isEmpty()) "Спершу додайте гаманець" else "Зберегти")
            }
        }
    }
}
