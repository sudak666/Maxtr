package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.theme.RytmRadii

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
 * destructive actions with no guard at all. A later design-review pass found
 * two of the highest-severity actions (reset profile data, delete account)
 * had drifted onto a hand-rolled AlertDialog with a plain text confirm
 * button — visually *weaker* than the low-stakes "sign out" dialog, i.e.
 * backwards from what the button weight should signal. [busy]/[busyLabel]
 * were added so those two (which show a progress spinner while the
 * operation runs) could adopt this component instead of re-diverging.
 */
@Composable
fun RytmDestructiveConfirm(
    title: String,
    body: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    busy: Boolean = false,
    busyLabel: String = body,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(busyLabel)
                }
            } else {
                Text(body)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
                enabled = !busy,
                shape = RoundedCornerShape(RytmRadii.Row),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            OutlinedButton(enabled = !busy, onClick = onDismiss, shape = RoundedCornerShape(RytmRadii.Row)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
