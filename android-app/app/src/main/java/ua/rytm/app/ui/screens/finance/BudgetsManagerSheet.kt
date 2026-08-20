package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository

// Mirrors js/settings-managers.js's budgets-modal — see BudgetsManagerViewModel's
// doc comment for scope (expense categories only).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsManagerSheet(
    repository: FinanceRepository,
    onDismiss: () -> Unit,
    viewModel: BudgetsManagerViewModel = viewModel(factory = BudgetsManagerViewModel.factory(repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Бюджети", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.rows.isEmpty()) {
                Text("Немає категорій витрат", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.rows.forEach { (category, limit) ->
                BudgetRow(
                    category = category,
                    limit = limit,
                    expanded = viewModel.expandedCategory == category,
                    onToggleEdit = { viewModel.toggleEdit(category) },
                    onLimitChange = { viewModel.updateBudget(category, it) },
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(category: String, limit: Double, expanded: Boolean, onToggleEdit: () -> Unit, onLimitChange: (Double) -> Unit) {
    val summary = if (limit > 0) "${limit.toInt()} грн/міс" else "Без ліміту"

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(category, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = "Редагувати")
            }
        }

        if (expanded) {
            var limitText by remember(category) { mutableStateOf(if (limit == 0.0) "" else limit.toString()) }
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Ліміт на місяць (грн)") },
            )
            LaunchedEffect(limitText) {
                delay(400)
                val parsed = limitText.toDoubleOrNull() ?: 0.0
                if (parsed != limit) onLimitChange(parsed)
            }
        }
    }
}
