package ua.rytm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.data.BackupInfo
import ua.rytm.app.ui.components.RytmDestructiveConfirm
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.CloudDone
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmRadii
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Settings → Резервні копії: the active own profile's cloud backups (functions/lib/backup.js). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsSheet(
    uid: String,
    profileId: String,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val backups by remember(uid, profileId) { viewModel.backups(uid, profileId) }.collectAsState(initial = null)
    var pendingRestore by rememberSaveable { mutableStateOf<String?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM, HH:mm", locale) }
    fun format(b: BackupInfo) = Instant.ofEpochMilli(b.createdAt).atZone(ZoneId.systemDefault()).format(formatter)

    LaunchedEffect(viewModel.restoreSucceeded) {
        if (viewModel.restoreSucceeded) { pendingRestore = null; viewModel.consumeRestoreSucceeded() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    RytmSheetTitle(stringResource(R.string.settings_backups_title), subtitle = stringResource(R.string.backups_body))
                    Button(
                        onClick = { viewModel.createBackup(profileId) },
                        enabled = !viewModel.backupBusy && viewModel.restoringBackupId == null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(RytmRadii.Row),
                    ) {
                        if (viewModel.backupBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(stringResource(R.string.backups_create))
                        }
                    }
                }
            }
            val list = backups
            when {
                list == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                list.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(R.string.backups_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> items(list, key = { it.id }) { b ->
                    BackupRow(
                        title = format(b),
                        subtitle = "${stringResource(reasonLabel(b.reason))} · ${pluralStringResource(R.plurals.backups_tx_count, b.txCount, b.txCount)}",
                        restoring = viewModel.restoringBackupId == b.id,
                        enabled = viewModel.restoringBackupId == null && !viewModel.backupBusy,
                        onRestore = { pendingRestore = b.id },
                    )
                }
            }
            item(key = "bottom-space") { Box(Modifier.padding(bottom = 16.dp)) }
        }
    }

    val target = pendingRestore?.let { id -> backups?.firstOrNull { it.id == id } }
    if (target != null) {
        RytmDestructiveConfirm(
            title = stringResource(R.string.backups_restore_title),
            body = stringResource(R.string.backups_restore_body, format(target)),
            confirmLabel = stringResource(R.string.backups_restore),
            busy = viewModel.restoringBackupId != null,
            busyLabel = stringResource(R.string.backups_restoring),
            onConfirm = { viewModel.restoreBackup(uid, profileId, target.id) },
            onDismiss = { pendingRestore = null },
        )
    }
}

private fun reasonLabel(reason: String) = when (reason) {
    "manual" -> R.string.backups_reason_manual
    "pre_reset" -> R.string.backups_reason_pre_reset
    "pre_import" -> R.string.backups_reason_pre_import
    "pre_restore" -> R.string.backups_reason_pre_restore
    else -> R.string.backups_reason_daily
}

@Composable
private fun BackupRow(title: String, subtitle: String, restoring: Boolean, enabled: Boolean, onRestore: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(RytmRadii.Row))
            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(RytmIcons.CloudDone, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (restoring) {
            CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onRestore, enabled = enabled) { Text(stringResource(R.string.backups_restore)) }
        }
    }
}
