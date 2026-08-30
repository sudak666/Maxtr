package ua.rytm.app.ui.screens.shifts
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.ShiftsRepository
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.Edit

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
        ShiftTypesManagerContent(viewModel, showTitle = true)
    }
}

// Extracted so Quick Fill (ShiftsScreen.kt) can swap this in as CONTENT
// inside its own single ModalBottomSheet instead of opening a second,
// independently-dismissible sheet stacked on top of it. Two stacked
// ModalBottomSheets sharing the same bottom-of-screen swipe-to-dismiss
// gesture area turned out to be unsafe regardless of which layer is a
// Dialog vs a Sheet (PR #475 fixed the Dialog-under-Sheet ghost-click
// variant of this, but the account owner reported it was STILL happening
// afterward): a swipe-down drag that crosses the top sheet's dismiss
// threshold removes that sheet from composition while the finger is still
// moving, and the still-in-progress pointer events land on whatever sheet
// is now underneath -- which, since it's also swipe-to-dismiss, keeps
// interpreting the same continued downward motion as ITS OWN dismiss drag,
// closing Quick Fill a beat after Shift Types with the reported "стрибає"
// jump. Never nesting two independently-dismissible sheets in the first
// place removes the entire bug class rather than chasing its next shape.
// `showTitle` is true for the standalone Settings/Quick-Fill-launcher
// entry point above; ShiftsScreen.kt passes false and renders its own
// back-affordance instead, since a nested "screen" inside Quick Fill's
// sheet reads better as a sub-view than a second title block.
@Composable
fun ShiftTypesManagerContent(viewModel: ShiftTypesManagerViewModel, showTitle: Boolean = true) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        viewModel.errorMessageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (showTitle) RytmSheetTitle(stringResource(R.string.shift_types_title), subtitle = stringResource(R.string.shift_types_body))

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
        androidx.compose.material3.Button(onClick = { viewModel.addShiftType(newShiftName) }, modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Row)) {
            Icon(RytmIcons.Add, contentDescription = null)
            Text(stringResource(R.string.shift_type_add))
        }
    }

    // A Dialog (AlertDialog/RytmDestructiveConfirm) layered on top of a
    // ModalBottomSheet is the proven-safe nesting direction elsewhere in
    // this app (unlike sheet-on-sheet above) -- unaffected by this change,
    // works identically whether this content sits in its own sheet or is
    // embedded inside Quick Fill's.
    viewModel.pendingDeleteId?.let {
        ua.rytm.app.ui.components.RytmDestructiveConfirm(
            title = stringResource(R.string.shift_type_delete_title),
            body = stringResource(R.string.shift_type_delete_body),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
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
    val summary = if (type.isOff) stringResource(R.string.shift_day_off) else stringResource(R.string.shift_type_summary, type.amount.toInt(), type.hours)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(RytmRadii.Chart),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(type.colorHex)))
            Column(Modifier.weight(1f)) {
                Text(localizedDomainText(type.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onToggleEdit) {
                Icon(if (expanded) RytmIcons.Close else RytmIcons.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = onDelete, colors = androidx.compose.material3.IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Icon(RytmIcons.Delete, contentDescription = stringResource(R.string.action_delete)) }
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
                isError = nameText.isBlank(),
                supportingText = if (nameText.isBlank()) ({ Text(stringResource(R.string.validation_name_required)) }) else null,
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
                    isError = amountText.isNotBlank() && amountText.toDoubleOrNull() == null,
                    supportingText = if (amountText.isNotBlank() && amountText.toDoubleOrNull() == null) ({ Text(stringResource(R.string.validation_invalid_amount)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    isError = hoursText.isNotBlank() && hoursText.toDoubleOrNull() == null,
                    supportingText = if (hoursText.isNotBlank() && hoursText.toDoubleOrNull() == null) ({ Text(stringResource(R.string.validation_invalid_amount)) }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.shift_hours)) },
                )
                androidx.compose.runtime.LaunchedEffect(hoursText) {
                    kotlinx.coroutines.delay(400)
                    val parsed = hoursText.toDoubleOrNull() ?: 0.0
                    if (parsed != type.hours) onHoursChange(parsed)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = type.isOff, role = Role.Checkbox, onValueChange = onIsOffChange)
                    .semantics(mergeDescendants = true) {}
                    .heightIn(min = RytmDimens.TouchTarget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ua.rytm.app.ui.components.RoundCheckbox(checked = type.isOff, modifier = Modifier.padding(end = 12.dp))
                Text(stringResource(R.string.shift_day_off_unpaid), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    }
}
