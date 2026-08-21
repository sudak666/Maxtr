package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.data.PushRepository

// Mirrors js/notifications.js's 4 independent alert-type checkboxes plus its
// reminder-time <select> pair — see NotificationSettingsViewModel's own doc
// comment for the fuller picture, and PushRepository's for why this exists
// as a separate step from step 27's single master Push toggle. Only
// reachable from SettingsScreen while the master toggle is already on (see
// that screen's own row) — configuring *which* alerts to send is
// meaningless before push itself is even registered.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsSheet(
    uid: String,
    repository: PushRepository,
    onDismiss: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel(factory = NotificationSettingsViewModel.factory(uid, repository)),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Типи сповіщень", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (viewModel.loading) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            NotifToggleRow(
                title = "Щоденне нагадування",
                subtitle = "Якщо сьогодні ще немає жодної операції",
                checked = viewModel.dailyReminderEnabled,
                onCheckedChange = viewModel::onDailyReminderChanged,
            )
            if (viewModel.dailyReminderEnabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeDropdown(
                        label = "Година",
                        options = (0..23).map { it.toString().padStart(2, '0') },
                        selected = viewModel.reminderHour,
                        onSelect = { viewModel.onReminderTimeChanged(it, viewModel.reminderMinute) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeDropdown(
                        label = "Хвилина",
                        options = (0..55 step 5).map { it.toString().padStart(2, '0') },
                        selected = viewModel.reminderMinute,
                        onSelect = { viewModel.onReminderTimeChanged(viewModel.reminderHour, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            NotifToggleRow(
                title = "Перевищення бюджету",
                subtitle = "Коли витрати за категорією перевищують місячний ліміт",
                checked = viewModel.budgetAlerts,
                onCheckedChange = viewModel::onBudgetAlertsChanged,
            )
            NotifToggleRow(
                title = "Регулярні платежі",
                subtitle = "За день до наступного регулярного платежу",
                checked = viewModel.recurringAlerts,
                onCheckedChange = viewModel::onRecurringAlertsChanged,
            )
            NotifToggleRow(
                title = "Розрахунки",
                subtitle = "За день до терміну сплати",
                checked = viewModel.debtAlerts,
                onCheckedChange = viewModel::onDebtAlertsChanged,
            )
        }
    }
}

@Composable
private fun NotifToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
