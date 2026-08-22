package ua.rytm.app.ui.screens.finance

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
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
import kotlinx.coroutines.launch
import ua.rytm.app.data.MonobankConnection
import ua.rytm.app.data.MonobankHttpException
import ua.rytm.app.data.MonobankRepository
import ua.rytm.app.data.MonobankSyncProgress
import ua.rytm.app.data.FinanceRepository
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonobankManagerSheet(uid: String, profileId: String, repository: MonobankRepository, financeRepository: FinanceRepository, onDismiss: () -> Unit) {
    var connection by remember(uid, profileId) { mutableStateOf<MonobankConnection?>(null) }
    var token by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<MonobankSyncProgress?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val wallets by financeRepository.wallets.collectAsState(initial = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun errorText(throwable: Throwable) = when (throwable) {
        is MonobankHttpException -> when (throwable.status) {
            401, 403 -> "Токен недійсний. Перевірте його та спробуйте ще раз."
            429 -> "Monobank дозволяє не більше одного запиту на хвилину. Спробуйте пізніше."
            else -> "Не вдалося з’єднатися з Monobank"
        }
        else -> throwable.message ?: "Не вдалося з’єднатися з Monobank"
    }

    LaunchedEffect(uid, profileId) {
        loading = true
        runCatching { repository.load(uid, profileId) }.onSuccess { connection = it }.onFailure { error = errorText(it) }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Monobank", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            when {
                loading -> CircularProgressIndicator()
                connection == null -> {
                    Text("Вкажіть персональний токен Monobank Open API — підтягнемо картки, банки та операції. Токен зберігається у вашому профілі й передається лише Monobank через захищений сервер Rytm.")
                    TextButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api.monobank.ua/"))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Отримати токен на api.monobank.ua") }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Персональний токен") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !busy,
                        isError = error != null,
                        supportingText = error?.let { text -> { Text(text) } },
                    )
                    Button(
                        onClick = { scope.launch {
                            busy = true; error = null
                            runCatching { repository.connect(uid, profileId, token) }
                                .onSuccess { connection = it; token = ""; message = "Monobank підключено" }
                                .onFailure { error = errorText(it) }
                            busy = false
                        } },
                        enabled = !busy && token.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Підключити") }
                }
                else -> {
                    val mono = connection!!
                    Text(mono.clientName.ifBlank { "Monobank підключено" }, fontWeight = FontWeight.SemiBold)
                    mono.accounts.forEach { account ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(account.label)
                                val walletName = mono.mapping[account.id]?.let { id -> wallets.firstOrNull { it.id == id }?.name }
                                Text(walletName ?: "${account.currency} · не прив’язано")
                            }
                        }
                    }
                    Text("Востаннє синхронізовано: " + (mono.lastSyncAt?.let { DateFormat.getDateTimeInstance().format(Date(it * 1000)) } ?: "ще не синхронізовано"))
                    progress?.let { Text("Синхронізую рахунок ${it.current}/${it.total}…") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Button(
                        onClick = { scope.launch {
                            busy = true; error = null; message = null
                            runCatching { repository.sync(uid, profileId, mono) { progress = it } }
                                .onSuccess { (next, count) -> connection = next; message = "Синхронізовано, нових операцій: $count" }
                                .onFailure { error = errorText(it) }
                            progress = null; busy = false
                        } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else { Icon(Icons.Filled.Sync, null); Spacer(Modifier.width(8.dp)); Text("Синхронізувати") }
                    }
                    OutlinedButton(
                        onClick = { confirmDisconnect = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Відключити") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmDisconnect) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        title = { Text("Відключити Monobank") },
        text = { Text("Гаманці й уже імпортовані операції залишаться, але автоматичне підтягування нових операцій припиниться.") },
        confirmButton = { TextButton(onClick = {
            confirmDisconnect = false
            scope.launch {
                busy = true
                runCatching { repository.disconnect(uid, profileId) }
                    .onSuccess { connection = null; message = "Monobank відключено" }
                    .onFailure { error = errorText(it) }
                busy = false
            }
        }) { Text("Відключити", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Скасувати") } },
    )
}
