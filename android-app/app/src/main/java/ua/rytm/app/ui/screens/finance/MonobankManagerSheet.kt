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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import ua.rytm.app.data.MonobankConnection
import ua.rytm.app.data.MonobankHttpException
import ua.rytm.app.data.MonobankRepository
import ua.rytm.app.data.MonobankSyncProgress
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
fun MonobankManagerSheet(uid: String, profileId: String, repository: MonobankRepository, financeRepository: FinanceRepository, onDismiss: () -> Unit) {
    var connection by remember(uid, profileId) { mutableStateOf<MonobankConnection?>(null) }
    var token by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(true) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var importedCount by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableStateOf<MonobankSyncProgress?>(null) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val wallets by financeRepository.wallets.collectAsState(initial = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun errorText(throwable: Throwable) = when (throwable) {
        is MonobankHttpException -> when (throwable.status) {
            401, 403 -> R.string.monobank_invalid_token
            429 -> R.string.monobank_rate_limit
            else -> R.string.monobank_connection_failed
        }
        else -> R.string.monobank_connection_failed
    }

    LaunchedEffect(uid, profileId) {
        loading = true
        runCatching { repository.load(uid, profileId) }.onSuccess { connection = it }.onFailure { errorRes = errorText(it) }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        onValueChange = { token = it; errorRes = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.monobank_token)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy,
                        isError = errorRes != null,
                        supportingText = errorRes?.let { textRes -> { Text(stringResource(textRes)) } },
                    )
                    Button(
                        onClick = { scope.launch {
                            busy = true; errorRes = null
                            runCatching { repository.connect(uid, profileId, token) }
                                .onSuccess { connection = it; token = ""; messageRes = R.string.monobank_connected }
                                .onFailure { errorRes = errorText(it) }
                            busy = false
                        } },
                        enabled = !busy && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.monobank_connect)) }
                }
                else -> {
                    val mono = connection!!
                    Text(mono.clientName.ifBlank { stringResource(R.string.monobank_connected) }, fontWeight = FontWeight.SemiBold)
                    mono.accounts.forEach { account ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(account.label)
                                val walletName = mono.mapping[account.id]?.let { id -> wallets.firstOrNull { it.id == id }?.name }
                                Text(walletName?.let { localizedDomainText(it) } ?: stringResource(R.string.monobank_unmapped, account.currency))
                            }
                        }
                    }
                    val locale = Locale.forLanguageTag(LocalConfiguration.current.locales[0].toLanguageTag())
                    val lastSync = mono.lastSyncAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(it * 1000)) } ?: stringResource(R.string.monobank_never_synced)
                    Text(stringResource(R.string.monobank_last_sync, lastSync))
                    progress?.let { Text(stringResource(R.string.monobank_sync_progress, it.current, it.total)) }
                    errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    messageRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary) }
                    importedCount?.let { Text(stringResource(R.string.monobank_sync_result, it), color = MaterialTheme.colorScheme.primary) }
                    Button(
                        onClick = { scope.launch {
                            busy = true; errorRes = null; messageRes = null; importedCount = null
                            runCatching { repository.sync(uid, profileId, mono) { progress = it } }
                                .onSuccess { (next, count) -> connection = next; importedCount = count }
                                .onFailure { errorRes = errorText(it) }
                            progress = null; busy = false
                        } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(RytmIcons.Sync, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.monobank_sync)) }
                    }
                    OutlinedButton(
                        onClick = { confirmDisconnect = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.monobank_disconnect)) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmDisconnect) ua.rytm.app.ui.components.RytmDestructiveConfirm(
        title = stringResource(R.string.monobank_disconnect_title),
        body = stringResource(R.string.monobank_disconnect_body),
        confirmLabel = stringResource(R.string.monobank_disconnect),
        onConfirm = {
            confirmDisconnect = false
            scope.launch {
                busy = true
                runCatching { repository.disconnect(uid, profileId) }
                    .onSuccess { connection = null; messageRes = R.string.monobank_disconnected }
                    .onFailure { errorRes = errorText(it) }
                busy = false
            }
        },
        onDismiss = { confirmDisconnect = false },
    )
}
