package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.InputStream
import ua.rytm.app.R
import ua.rytm.app.data.BackupPreview

private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

internal fun InputStream.readBoundedBackup(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= MAX_BACKUP_BYTES) { "Backup is too large" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@Composable
internal fun BackupPasswordDialog(
    restore: Boolean,
    password: String,
    busy: Boolean,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (restore) R.string.settings_backup_restore_password_title else R.string.settings_backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (restore) R.string.settings_backup_restore_password_body else R.string.settings_backup_password_body))
                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it.take(128)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(stringResource(R.string.settings_backup_password_label)) },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && password.length >= 8, onClick = onConfirm) {
                Text(stringResource(if (restore) R.string.action_continue else R.string.settings_backup_create))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun BackupRestorePreviewDialog(
    preview: BackupPreview,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settings_backup_restore_preview_title)) },
        text = { Text(stringResource(R.string.settings_backup_restore_preview_body, preview.rowCount, preview.nonEmptyTableCount)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                Text(stringResource(R.string.settings_backup_restore_action))
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
