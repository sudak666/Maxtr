package ua.rytm.app.ui.screens.pin
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import ua.rytm.app.R
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.ArrowBack
import ua.rytm.app.ui.icons.DeleteOutline
import ua.rytm.app.ui.icons.Fingerprint
import ua.rytm.app.ui.icons.Lock
import ua.rytm.app.ui.icons.LockClock

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
    var setupStep by remember { mutableIntStateOf(0) }
    var removeConfirmationVisible by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        shape = RoundedCornerShape(RytmRadii.Sheet),
        tonalElevation = 8.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, ua.rytm.app.ui.theme.Purple3))),
                contentAlignment = Alignment.Center,
            ) { Icon(RytmIcons.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
            RytmSheetTitle(stringResource(R.string.pin_settings_title))

            if (hasPin == true) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RytmRadii.Chart),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(RytmIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(R.string.pin_protection_active), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.pin_is_set), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (activity != null && biometricAvailable(activity)) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Chart)).clickable { viewModel.setBiometricEnabled(!biometricEnabled) },
                        shape = RoundedCornerShape(RytmRadii.Chart),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(RytmIcons.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Column(Modifier.padding(horizontal = 12.dp).weight(1f)) {
                                Text(stringResource(R.string.pin_biometric_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.pin_biometric_toggle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = biometricEnabled, onCheckedChange = null, colors = ua.rytm.app.ui.theme.rytmSwitchColors())
                        }
                    }
                }

                Button(onClick = { viewModel.lockNow(); onDismiss() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(RytmRadii.Row)) {
                    Icon(RytmIcons.LockClock, contentDescription = null)
                    Text(stringResource(R.string.pin_lock_now))
                }
                OutlinedButton(
                    onClick = { removeConfirmationVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RytmRadii.Row),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(RytmIcons.DeleteOutline, contentDescription = null)
                    Text(stringResource(R.string.pin_remove))
                }
            } else {
                Text(stringResource(R.string.pin_setup_step, setupStep + 1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(stringResource(if (setupStep == 0) R.string.pin_new else R.string.pin_confirm), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val activeLength = if (setupStep == 0) viewModel.newPin.length else viewModel.confirmPin.length
                PinDots(activeLength)
                PinKeypad(
                    onDigit = if (setupStep == 0) viewModel::setNewPinDigit else viewModel::setConfirmPinDigit,
                    onBackspace = if (setupStep == 0) viewModel::newPinBackspace else viewModel::confirmPinBackspace,
                    trailingSlot = {
                        if (setupStep == 1) TextButton(onClick = { viewModel.resetPinEntryFields(); setupStep = 0 }) {
                            Icon(RytmIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )

                viewModel.errorMessageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { if (setupStep == 0) setupStep = 1 else viewModel.savePin() },
                    enabled = activeLength >= 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) {
                    Text(stringResource(if (setupStep == 0) R.string.action_continue else R.string.pin_save))
                }
            }
        }
    }

    // Another hand-rolled destructive AlertDialog found during the button-
    // shape audit -- same drift RytmDestructiveConfirm already consolidated
    // seven other call sites onto today. Converted rather than just patching
    // its shape.
    if (removeConfirmationVisible) {
        ua.rytm.app.ui.components.RytmDestructiveConfirm(
            title = stringResource(R.string.pin_remove_title),
            body = stringResource(R.string.pin_remove_body),
            confirmLabel = stringResource(R.string.pin_remove),
            onConfirm = { viewModel.removePin(); removeConfirmationVisible = false; onDismiss() },
            onDismiss = { removeConfirmationVisible = false },
        )
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            val color = if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            Box(Modifier.size(if (i < filled) 14.dp else 12.dp).background(color, CircleShape))
        }
    }
}
