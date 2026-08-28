package ua.rytm.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import ua.rytm.app.R
import ua.rytm.app.ui.theme.RytmRadii
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.CalendarMonth
import ua.rytm.app.ui.icons.Close

// Shared with the rest of the app — see components/DateFormat.kt.
private val displayDateFormatter = NumericDateFormatter

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
    val clearDate = stringResource(R.string.action_clear)
    OutlinedTextField(
        value = selected?.format(displayDateFormatter).orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        trailingIcon = {
            // Clearing lives here, not as a button inside the calendar dialog
            // below — that placement used to visually collide with the
            // DatePicker's own month/year header row. A field-level "x" is
            // also the more standard pattern: clear without opening the
            // calendar at all.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (allowEmpty && selected != null) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(RytmIcons.Close, contentDescription = clearDate)
                    }
                }
                IconButton(onClick = { open = true }) {
                    Icon(RytmIcons.CalendarMonth, contentDescription = chooseDate)
                }
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
        }
    }
}
