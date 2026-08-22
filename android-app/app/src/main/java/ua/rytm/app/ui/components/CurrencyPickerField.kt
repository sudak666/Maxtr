package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

val SUPPORTED_CURRENCIES = listOf("UAH", "USD", "EUR", "GBP", "PLN")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyPickerField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Валюта") {
    var expanded by remember { mutableStateOf(false) }
    val normalized = value.takeIf { it in SUPPORTED_CURRENCIES } ?: "UAH"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = normalized,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SUPPORTED_CURRENCIES.forEach { code ->
                DropdownMenuItem(text = { Text(code) }, onClick = { onValueChange(code); expanded = false })
            }
        }
    }
}
