package ua.rytm.app.ui.screens.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.screens.finance.formatMoneyWithCurrency
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.ShiftsRepository

// Mirrors js/settings-managers.js's shift-types-modal (openShiftTypesManager()/
// renderShiftTypesList()) — same collapsed-summary-row-with-pencil-toggle-to-expand
// shape the PWA uses for auto-rules/recurring managers. Color is fixed at creation
// (PALETTE rotation), no interactive picker yet — same disclosed scope as
// WalletsManagerSheet's swatch.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftTypesManagerSheet(
    repository: ShiftsRepository,
    uid: String,
    profileId: String,
    onDismiss: () -> Unit,
    viewModel: ShiftTypesManagerViewModel = viewModel(key = "$uid|$profileId", factory = ShiftTypesManagerViewModel.factory(repository, uid, profileId)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            viewModel.errorMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.shift_types_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.shiftTypes.isEmpty()) {
                Text(stringResource(R.string.shift_types_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            viewModel.shiftTypes.forEach { type ->
                ShiftTypeRow(
                    type = type,
                    expanded = viewModel.expandedId == type.id,
                    onToggleEdit = { viewModel.toggleEdit(type.id) },
                    onNameChange = { viewModel.updateName(type, it) },
                    onAmountChange = { viewModel.updateAmount(type, it) },
                    onHoursChange = { viewModel.updateHours(type, it) },
                    onIsOffChange = { viewModel.updateIsOff(type, it) },
                    onDelete = { viewModel.requestDelete(type.id) },
                )
            }

            val newShiftName = stringResource(R.string.shift_type_new_default)
            TextButton(onClick = { viewModel.addShiftType(newShiftName) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.shift_type_add))
            }
        }
    }

    viewModel.pendingDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text(stringResource(R.string.shift_type_delete_title)) },
            text = { Text(stringResource(R.string.shift_type_delete_body)) },
            confirmButton = { TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ShiftTypeRow(
    type: ShiftType,
    expanded: Boolean,
    onToggleEdit: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (Double) -> Unit,
    onHoursChange: (Double) -> Unit,
    onIsOffChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val summary = if (type.isOff) stringResource(R.string.shift_day_off) else stringResource(R.string.shift_type_summary, formatMoneyWithCurrency(type.amount, "UAH"), type.hours)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(type.colorHex)))
            Column(Modifier.weight(1f)) {
                Text(localizedDomainText(type.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }

        if (expanded) {
            var nameText by remember(type.id) { mutableStateOf(type.name) }
            var amountText by remember(type.id) { mutableStateOf(if (type.amount == 0.0) "" else type.amount.toString()) }
            var hoursText by remember(type.id) { mutableStateOf(if (type.hours == 0.0) "" else type.hours.toString()) }

            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.field_name)) },
            )
            androidx.compose.runtime.LaunchedEffect(nameText) {
                kotlinx.coroutines.delay(400)
                if (nameText != type.name) onNameChange(nameText)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.shift_pay)) },
                )
                androidx.compose.runtime.LaunchedEffect(amountText) {
                    kotlinx.coroutines.delay(400)
                    val parsed = amountText.toDoubleOrNull() ?: 0.0
                    if (parsed != type.amount) onAmountChange(parsed)
                }

                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(stringResource(R.string.shift_hours)) },
                )
                androidx.compose.runtime.LaunchedEffect(hoursText) {
                    kotlinx.coroutines.delay(400)
                    val parsed = hoursText.toDoubleOrNull() ?: 0.0
                    if (parsed != type.hours) onHoursChange(parsed)
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = type.isOff, onCheckedChange = onIsOffChange)
                Text(stringResource(R.string.shift_day_off_unpaid), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
