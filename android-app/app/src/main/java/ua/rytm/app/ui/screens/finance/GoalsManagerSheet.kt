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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

// Mirrors js/goals-profile.js's goals-modal — see GoalsManagerViewModel's
// doc comment. Same collapsed-summary-row-with-pencil-toggle shape as
// budgets/recurring, plus a progress bar (js/goals-profile.js's
// renderGoalsManagerList() `salary-bar-fill`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsManagerSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: GoalsManagerViewModel = viewModel(factory = GoalsManagerViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Цілі", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.goals.isEmpty()) {
                Text("Немає цілей", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.goals.forEach { g ->
                GoalRow(
                    goal = g,
                    expanded = viewModel.expandedId == g.id,
                    wallets = viewModel.wallets,
                    saved = viewModel.walletBalances[g.walletId] ?: 0.0,
                    onToggleEdit = { viewModel.toggleEdit(g.id) },
                    onWalletChange = { viewModel.updateWallet(g, it) },
                    onTargetAmountChange = { viewModel.updateTargetAmount(g, it) },
                    onTargetDateChange = { viewModel.updateTargetDate(g, it) },
                    onDelete = { viewModel.requestDelete(g.id) },
                )
            }

            TextButton(onClick = viewModel::addGoal, modifier = Modifier.fillMaxWidth(), enabled = viewModel.wallets.isNotEmpty()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Додати ціль")
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Видалити ціль") },
            text = { Text("Видалити цю ціль?") },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text("Видалити", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Скасувати") } },
        )
    }
}

@Composable
private fun GoalRow(
    goal: Goal,
    expanded: Boolean,
    wallets: List<Wallet>,
    saved: Double,
    onToggleEdit: () -> Unit,
    onWalletChange: (String) -> Unit,
    onTargetAmountChange: (Double) -> Unit,
    onTargetDateChange: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val wallet = wallets.firstOrNull { it.id == goal.walletId }
    val target = goal.targetAmount
    val savedClamped = saved.coerceAtLeast(0.0)
    val progress = if (target > 0) (savedClamped / target).coerceIn(0.0, 1.0) else 0.0
    val done = target > 0 && savedClamped >= target
    val summary = if (wallet != null) {
        "${savedClamped.toInt()} / ${target.toInt()} ${wallet.currency}" + if (done) " · Досягнуто" else ""
    } else {
        "Оберіть гаманець"
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text((wallet?.name ?: "Ціль") + if (goal.targetDate.isNotBlank()) " · ${goal.targetDate}" else "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = "Редагувати")
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Видалити") }
        }

        if (expanded) {
            GoalWalletDropdown(wallets = wallets, selectedId = goal.walletId, onSelect = onWalletChange)

            // Keyed only on goal.id (not goal.targetAmount): the debounced
            // LaunchedEffect below round-trips this field's own edits back
            // through the repository, and re-keying on the echoed value would
            // reset mid-typing local state back to whatever partial value was
            // last committed, silently truncating fast input.
            var amountText by remember(goal.id) { mutableStateOf(if (goal.targetAmount == 0.0) "" else goal.targetAmount.toString()) }
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Цільова сума") },
            )
            LaunchedEffect(amountText) {
                delay(400)
                val parsed = amountText.toDoubleOrNull() ?: 0.0
                if (parsed != goal.targetAmount) onTargetAmountChange(parsed)
            }

            // Same reasoning as amountText above.
            var dateText by remember(goal.id) { mutableStateOf(goal.targetDate) }
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Дата (напр. Грудень 2026)") },
            )
            LaunchedEffect(dateText) {
                delay(400)
                if (dateText != goal.targetDate) onTargetDateChange(dateText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalWalletDropdown(wallets: List<Wallet>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = wallets.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let { "${it.name} (${it.currency})" } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Гаманець") },
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
