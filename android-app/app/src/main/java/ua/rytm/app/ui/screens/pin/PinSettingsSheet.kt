package ua.rytm.app.ui.screens.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity

// Mirrors js/auth.js's openPinSettings()/setPin()/removePin()/renderBioSettingsUI() —
// set/change/remove PIN, biometric toggle (only offered when the device
// actually has a usable biometric sensor), "lock now". Reuses PinKeypad, the
// same touch-keypad-with-dots component the unlock screen uses (the PWA's own
// account-owner-specified convention — see js/auth.js's PIN_DOTS_MAP comment).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSettingsSheet(viewModel: PinViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val hasPin by viewModel.hasPin.collectAsState(initial = null)
    val biometricEnabled by viewModel.biometricEnabled.collectAsState(initial = false)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("PIN-код", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (hasPin == true) {
                Text("PIN встановлено", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (activity != null && biometricAvailable(activity)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Розблокування відбитком/обличчям")
                            Switch(checked = biometricEnabled, onCheckedChange = viewModel::setBiometricEnabled)
                        }
                    }
                }

                TextButton(onClick = { viewModel.lockNow(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Заблокувати зараз")
                }
                TextButton(onClick = { viewModel.removePin(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Видалити PIN", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text("Новий PIN (4-6 цифр)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PinDots(viewModel.newPin.length)
                PinKeypad(onDigit = viewModel::setNewPinDigit, onBackspace = viewModel::newPinBackspace)

                Text("Підтвердіть PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PinDots(viewModel.confirmPin.length)
                PinKeypad(onDigit = viewModel::setConfirmPinDigit, onBackspace = viewModel::confirmPinBackspace)

                viewModel.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                TextButton(onClick = { viewModel.savePin() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Зберегти PIN")
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            val color = if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            Box(Modifier.size(12.dp).background(color, CircleShape))
        }
    }
}
