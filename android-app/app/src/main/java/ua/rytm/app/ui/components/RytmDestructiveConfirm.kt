package ua.rytm.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R

/**
 * The single confirmation dialog for irreversible/bulk destructive actions.
 *
 * App-wide destructive-action policy (one pattern, no exceptions):
 *  - deleting ONE item from a list → optimistic delete + undo snackbar
 *    (FinanceScreen / ShoppingScreen);
 *  - anything bulk or unrecoverable (clear a month of shifts, delete a debt,
 *    clear bought items, reset profile data, delete the account) → this
 *    dialog.
 *
 * Before this existed the app shipped three incompatible patterns plus two
 * destructive actions with no guard at all.
 */
@Composable
fun RytmDestructiveConfirm(
    title: String,
    body: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
