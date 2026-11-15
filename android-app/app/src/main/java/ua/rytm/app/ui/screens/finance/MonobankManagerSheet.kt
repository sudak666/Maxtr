package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.core.net.toUri

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import ua.rytm.app.R
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.data.MonobankConnection
import ua.rytm.app.data.MonobankRepository
import ua.rytm.app.data.MonobankSyncCoordinator
import ua.rytm.app.data.FinanceRepository
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Sync

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonobankManagerSheet(uid: String, profileId: String, repository: MonobankRepository, syncCoordinator: MonobankSyncCoordinator, financeRepository: FinanceRepository, onDismiss: () -> Unit) {
    val vm: MonobankManagerViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        key = "monobank-$uid-$profileId",
        factory = MonobankManagerViewModel.factory(repository, uid, profileId),
    )
    val connection = vm.connection
    val loading = vm.loading
    val busy = vm.busy
    var token by rememberSaveable { mutableStateOf("") }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    val syncState by syncCoordinator.state.collectAsStateWithLifecycle()
    val profileKey = "$uid/$profileId"
    val syncing = syncState.running && syncState.profileKey == profileKey
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val wallets by financeRepository.wallets.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(syncState) {
        if (syncState.running || syncState.profileKey != profileKey) return@LaunchedEffect
        syncState.result?.let { (next, count) -> vm.connection = next; importedCount = count }
        syncState.error?.let { vm.errorRes = MonobankManagerViewModel.errorText(it) }
        if (syncState.result != null || syncState.error != null) syncCoordinator.consume()
    }

    ua.rytm.app.ui.components.RytmModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(ua.rytm.app.ui.theme.RytmSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ua.rytm.app.ui.theme.RytmSpacing.md),
        ) {
            RytmSheetTitle("Monobank")
            when {
                loading -> CircularProgressIndicator()
                connection == null -> {
                    Text(stringResource(R.string.monobank_intro))
                    TextButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://api.monobank.ua/".toUri())) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.monobank_get_token)) }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; vm.errorRes = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.monobank_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy,
                        isError = vm.errorRes != null,
                        supportingText = vm.errorRes?.let { textRes -> { Text(stringResource(textRes)) } },
                    )
                    Button(
                        onClick = { vm.connect(token) { token = "" } },
                        enabled = !busy && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RytmRadii.Row),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.monobank_connect)) }
                }
                else -> connection?.let { mono ->
                    Text(mono.clientName.ifBlank { stringResource(R.string.monobank_connected) }, fontWeight = FontWeight.SemiBold)
                    mono.accounts.forEach { account ->
                        Card(Modifier.fillMaxWidth()) {
                            // Card on the first line, the wallet it fills on the second: side by side
                            // they ran together («…5129Monobank 4441…5129»).
                            Column(Modifier.fillMaxWidth().padding(ua.rytm.app.ui.theme.RytmSpacing.md)) {
                                val pan = Regex("\\d{4}$").find(account.label)?.value
                                Text(
                                    if (account.kind == "jar") account.label else if (pan != null) stringResource(R.string.monobank_card_label, pan, account.currency) else account.label,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                val walletName = mono.mapping[account.id]?.let { id -> wallets.firstOrNull { it.id == id }?.name }
                                Text(
                                    walletName?.let { stringResource(R.string.monobank_into_wallet, localizedDomainText(it)) } ?: stringResource(R.string.monobank_unmapped, account.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
                    val lastSync = mono.lastSyncAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it * 1000)) } ?: stringResource(R.string.monobank_never_synced)
                    Text(stringResource(R.string.monobank_last_sync, lastSync))
                    Text(stringResource(R.string.monobank_balance_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (syncing) {
                        syncState.progress?.let { Text(stringResource(R.string.monobank_sync_progress, it.current, it.total)) }
                        Text(stringResource(R.string.monobank_sync_background), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    vm.messageRes?.let { Text(stringResource(it), color = ua.rytm.app.ui.theme.RytmSemantic.link) }
                    importedCount?.let { Text(stringResource(R.string.monobank_sync_result, it), color = ua.rytm.app.ui.theme.RytmSemantic.link) }
                    Button(
                        onClick = {
                            vm.errorRes = null; vm.messageRes = null; importedCount = null
                            syncCoordinator.start(uid, profileId, mono)
                        },
                        enabled = !busy && !syncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RytmRadii.Row),
                    ) {
                        if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(RytmIcons.Sync, null); Spacer(Modifier.width(ua.rytm.app.ui.theme.RytmSpacing.sm)); Text(stringResource(R.string.monobank_sync)) }
                    }
                    ua.rytm.app.ui.components.RytmOutlinedButton(
                        onClick = { confirmDisconnect = true },
                        enabled = !busy && !syncing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RytmRadii.Row),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.monobank_disconnect)) }
                }
            }
            Spacer(Modifier.height(ua.rytm.app.ui.theme.RytmSpacing.xl))
        }
    }

    if (confirmDisconnect) ua.rytm.app.ui.components.RytmDestructiveConfirm(
        title = stringResource(R.string.monobank_disconnect_title),
        body = stringResource(R.string.monobank_disconnect_body),
        confirmLabel = stringResource(R.string.monobank_disconnect),
        onConfirm = {
            confirmDisconnect = false
            vm.disconnect()
        },
        onDismiss = { confirmDisconnect = false },
    )
}
