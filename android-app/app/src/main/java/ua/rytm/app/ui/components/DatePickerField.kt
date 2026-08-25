package ua.rytm.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import ua.rytm.app.R
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.runtime.saveable.rememberSaveable

private val displayDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = true,
    outputIso: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = value.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
            ?: runCatching { LocalDate.parse(it, displayDateFormatter) }.getOrNull()
    }
    // The click target used to be an anonymous transparent Box overlaying the
    // field: no Role.Button, no label, so TalkBack announced an unnamed
    // clickable node next to a read-only field that looked editable. The
    // field itself now carries the role/label, and the trailing icon is a
    // real IconButton instead of something that only worked because the
    // overlay happened to cover it.
    val chooseDate = stringResource(R.string.action_choose_date)
    OutlinedTextField(
        value = selected?.format(displayDateFormatter).orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = { open = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = chooseDate)
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = chooseDate) { open = true },
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selected?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                Button(
                    onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onValueChange(if (outputIso) date.toString() else date.format(displayDateFormatter))
                    }
                    open = false
                    },
                    shape = RoundedCornerShape(RytmRadii.Pill),
                ) { Text(stringResource(R.string.action_done), fontWeight = FontWeight.Bold) }
            },
            // M3's dismissButton slot takes ONE composable; stuffing Clear +
            // Cancel in here put three buttons in the dialog's bottom row,
            // which overflows at 320dp or fontScale >= 1.3 with Ukrainian
            // labels. Clear now lives under the calendar instead.
            dismissButton = {
                OutlinedButton(onClick = { open = false }, shape = RoundedCornerShape(RytmRadii.Pill)) {
                    Text(stringResource(R.string.action_cancel), fontWeight = FontWeight.Bold)
                }
            },
        ) {
            DatePicker(
                state = state,
                title = null,
                headline = null,
                showModeToggle = false,
            )
            if (allowEmpty) {
                TextButton(
                    onClick = { onValueChange(""); open = false },
                    shape = RoundedCornerShape(RytmRadii.Pill),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                ) { Text(stringResource(R.string.action_clear), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
