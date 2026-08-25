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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.GoalsSyncRepository
import androidx.compose.foundation.layout.imePadding

// Mirrors js/goals-profile.js's goals-modal — see GoalsManagerViewModel's
// doc comment. Same collapsed-summary-row-with-pencil-toggle shape as
// budgets/recurring, plus a progress bar (js/goals-profile.js's
// renderGoalsManagerList() `salary-bar-fill`).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsManagerSheet(
    repository: FinanceRepository,
    syncRepository: GoalsSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: GoalsManagerViewModel = viewModel(
        key = "goals-$uid-$profileId",
        factory = GoalsManagerViewModel.factory(repository, syncRepository, uid, profileId),
    ),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            if (viewModel.goals.isEmpty()) {
                Text(stringResource(R.string.goals_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Button(
                onClick = viewModel::addGoal,
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.wallets.isNotEmpty() && !viewModel.isSaving,
                shape = RoundedCornerShape(16.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
                Text(stringResource(R.string.goals_add))
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.goals_delete_title)) },
            text = { Text(stringResource(R.string.goals_delete_body)) },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    enabled = !viewModel.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
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
        stringResource(if (done) R.string.goals_summary_done else R.string.goals_summary, savedClamped.toInt(), target.toInt(), wallet.currency)
    } else {
        stringResource(R.string.goals_choose_wallet)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text((wallet?.name?.let { localizedDomainText(it) } ?: stringResource(R.string.goals_default_name)) + if (goal.targetDate.isNotBlank()) " · ${goal.targetDate}" else "", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(progress = { progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            IconButton(
                onClick = onToggleEdit,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
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
                label = { Text(stringResource(R.string.goals_target_amount)) },
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
                label = { Text(stringResource(R.string.goals_target_date)) },
            )
            LaunchedEffect(dateText) {
                delay(400)
                if (dateText != goal.targetDate) onTargetDateChange(dateText)
            }
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
            label = { Text(stringResource(R.string.wallet_label)) },
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
