package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.BudgetsSyncRepository
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.Edit

// Mirrors js/settings-managers.js's budgets-modal — see BudgetsManagerViewModel's
// doc comment for scope (expense categories only).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsManagerSheet(
    repository: FinanceRepository,
    syncRepository: BudgetsSyncRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: BudgetsManagerViewModel = viewModel(key = "budgets-$uid-$profileId", factory = BudgetsManagerViewModel.factory(repository, syncRepository, uid, profileId)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RytmSheetTitle(stringResource(R.string.budgets_title), subtitle = stringResource(R.string.budgets_body))

            viewModel.errorMessageRes?.let { messageRes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = viewModel::consumeError) { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.action_dismiss)) }
                }
            }

            if (viewModel.rows.isEmpty()) {
                Text(stringResource(R.string.budgets_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.rows.forEach { (category, limit) ->
                BudgetRow(
                    category = category,
                    limit = limit,
                    iconOverride = viewModel.categoryIcons[category],
                    expanded = viewModel.expandedCategory == category,
                    onToggleEdit = { viewModel.toggleEdit(category) },
                    onLimitChange = { viewModel.updateBudget(category, it) },
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(category: String, limit: Double, iconOverride: String?, expanded: Boolean, onToggleEdit: () -> Unit, onLimitChange: (Double) -> Unit) {
    val summary = if (limit > 0) stringResource(R.string.budgets_summary, limit.toInt()) else stringResource(R.string.budgets_unlimited)

    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CategoryIconBadge(category, iconOverride = iconOverride, size = 32.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(localizedDomainText(category), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) RytmIcons.Close else RytmIcons.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        }

        if (expanded) {
            var limitText by remember(category) { mutableStateOf(if (limit == 0.0) "" else limit.toString()) }
            OutlinedTextField(
                value = limitText,
                onValueChange = { limitText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.budgets_monthly_limit)) },
                // Used to override unfocusedBorderColor to onSurfaceVariant
                // here (default `outline` was invisible in light theme at
                // the time). Now that Theme.kt's `outline` itself is a real,
                // visible hairline tone app-wide, this override is both
                // redundant and actively inconsistent -- it made this one
                // field's border brighter than every other field's. Removed
                // to fall back to the shared default.
            )
            LaunchedEffect(limitText) {
                delay(400)
                val parsed = limitText.toDoubleOrNull() ?: 0.0
                if (parsed != limit) onLimitChange(parsed)
            }
        }
    }
    }
}
