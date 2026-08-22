package ua.rytm.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import ua.rytm.app.R

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val selected = value.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
            ?: runCatching { LocalDate.parse(it, displayDateFormatter) }.getOrNull()
    }
    Box(modifier) {
        OutlinedTextField(
            value = selected?.format(displayDateFormatter).orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.action_choose_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selected?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis -> onValueChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = {
                if (allowEmpty) TextButton(onClick = { onValueChange(""); open = false }) { Text(stringResource(R.string.action_clear)) }
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}
