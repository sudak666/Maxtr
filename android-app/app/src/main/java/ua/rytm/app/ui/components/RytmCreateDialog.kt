package ua.rytm.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSpacing

/**
 * The one way to add something from a manager sheet: a form that saves
 * nothing until "Додати", so a stray tap never leaves a half-made entry with
 * guessed defaults (first wallet, zero amount) behind.
 */
@Composable
fun RytmCreateDialog(title: String, canCreate: Boolean, onCreate: () -> Unit, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) { content() }
        },
        confirmButton = {
            Button(onClick = onCreate, enabled = canCreate, shape = RoundedCornerShape(RytmRadii.Row)) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(RytmRadii.Row)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
