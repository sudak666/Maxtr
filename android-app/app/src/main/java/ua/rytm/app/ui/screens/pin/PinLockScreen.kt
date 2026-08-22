package ua.rytm.app.ui.screens.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ua.rytm.app.RytmApplication
import ua.rytm.app.R
import ua.rytm.app.data.local.clearAllProfileScopedTables
import kotlinx.coroutines.launch

// Mirrors the PWA's #pin-screen: touch keypad (0-9 + back), dot indicators
// (never the raw digits, per the account owner's original design reference —
// see js/auth.js's updatePinDots() comment), biometric fallback shown only
// when enabled. A local re-lock gate on an already-signed-in session, not a
// login screen — see CLAUDE.md's Auth section.
@Composable
fun PinLockScreen(viewModel: PinViewModel) {
    val context = LocalContext.current
    val biometricEnabled by viewModel.biometricEnabled.collectAsState(initial = false)
    val app = context.applicationContext as RytmApplication
    val biometricOnboardingDismissed by app.settingsStore.biometricOnboardingDismissed(viewModel.uid).collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    var forgotConfirm by remember { mutableStateOf(false) }
    var biometricOnboardingVisible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(biometricEnabled, biometricOnboardingDismissed) {
        val activity = context as? FragmentActivity
        biometricOnboardingVisible = activity != null && !biometricEnabled && !biometricOnboardingDismissed && biometricAvailable(activity)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.pin_enter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
                Box(
                    Modifier.size(16.dp).background(
                        color = if (i < viewModel.pinInput.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                    ),
                )
            }
        }

        viewModel.errorMessage?.let { message ->
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(40.dp))

        PinKeypad(
            onDigit = viewModel::press,
            onBackspace = viewModel::backspace,
            trailingSlot = {
                if (biometricEnabled) {
                    IconButton(onClick = {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            showBiometricPrompt(activity, onSuccess = viewModel::unlockWithBiometric)
                        }
                    }) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = stringResource(R.string.pin_unlock_biometric), modifier = Modifier.size(28.dp))
                    }
                }
            },
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = { forgotConfirm = true }) { Text(stringResource(R.string.pin_forgot_short)) }
    }

    if (forgotConfirm) AlertDialog(
        onDismissRequest = { forgotConfirm = false },
        title = { Text(stringResource(R.string.pin_forgot_title)) },
        text = { Text(stringResource(R.string.pin_forgot_body)) },
        confirmButton = {
            TextButton(onClick = {
                forgotConfirm = false
                viewModel.forgotPin {
                    if (!app.settingsStore.isPrivacyCacheEnabled()) app.database.clearAllProfileScopedTables()
                }
            }) { Text(stringResource(R.string.pin_reset_sign_out), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { forgotConfirm = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (biometricOnboardingVisible) AlertDialog(
        onDismissRequest = {
            biometricOnboardingVisible = false
            scope.launch { app.settingsStore.setBiometricOnboardingDismissed(viewModel.uid, true) }
        },
        title = { Text(stringResource(R.string.pin_biometric_title)) },
        text = { Text(stringResource(R.string.pin_biometric_body)) },
        confirmButton = {
            TextButton(onClick = {
                val activity = context as? FragmentActivity ?: return@TextButton
                showBiometricPrompt(activity) {
                    viewModel.setBiometricEnabled(true)
                    viewModel.unlockWithBiometric()
                    biometricOnboardingVisible = false
                    scope.launch { app.settingsStore.setBiometricOnboardingDismissed(viewModel.uid, true) }
                }
            }) { Text(stringResource(R.string.action_enable)) }
        },
        dismissButton = {
            TextButton(onClick = {
                biometricOnboardingVisible = false
                scope.launch { app.settingsStore.setBiometricOnboardingDismissed(viewModel.uid, true) }
            }) { Text(stringResource(R.string.action_not_now)) }
        },
    )
}

@Composable
internal fun PinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    trailingSlot: @Composable () -> Unit = {},
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> KeypadButton(digit) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { trailingSlot() }
            KeypadButton("0") { onDigit("0") }
            Box(
                Modifier.size(64.dp).clickable(onClick = onBackspace),
                contentAlignment = Alignment.Center,
            ) { Text("⌫", style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
private fun KeypadButton(digit: String, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}
