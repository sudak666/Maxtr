package ua.rytm.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.data.ProfileSyncCoordinator
import ua.rytm.app.data.TransactionSyncState

val LocalRealtimeState = staticCompositionLocalOf<ProfileSyncCoordinator.RealtimeState> { ProfileSyncCoordinator.RealtimeState.Stopped }
val LocalSyncRetry = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
fun RealtimeStateBanner() {
    val state = LocalRealtimeState.current
    val message = when (state) {
        ProfileSyncCoordinator.RealtimeState.Offline -> R.string.sync_status_offline
        is ProfileSyncCoordinator.RealtimeState.Error -> when (state.failure.kind) {
            ua.rytm.app.data.SyncFailure.Kind.NETWORK -> R.string.sync_error_network
            ua.rytm.app.data.SyncFailure.Kind.AUTH -> R.string.sync_error_auth
            ua.rytm.app.data.SyncFailure.Kind.PERMISSION -> R.string.sync_error_permission
            ua.rytm.app.data.SyncFailure.Kind.RATE_LIMITED -> R.string.sync_error_rate_limited
            ua.rytm.app.data.SyncFailure.Kind.CONFLICT -> R.string.sync_error_conflict
            ua.rytm.app.data.SyncFailure.Kind.DATA -> R.string.sync_error_data
            ua.rytm.app.data.SyncFailure.Kind.UNKNOWN -> R.string.sync_status_error
        }
        else -> return
    }
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(stringResource(message), Modifier.weight(1f).padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state !is ProfileSyncCoordinator.RealtimeState.Error || state.failure.retryable) {
                TextButton(onClick = LocalSyncRetry.current) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
fun OperationSyncStateBanner(state: TransactionSyncState?) {
    state ?: return
    val error = state == TransactionSyncState.ERROR
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (error) Icons.Filled.SyncProblem else Icons.Filled.CloudUpload,
            contentDescription = null,
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(if (error) R.string.sync_operation_error else R.string.sync_operation_pending),
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ScreenLoadingState() {
    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(Modifier.padding(16.dp)) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp))
            Text(stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScreenLoadErrorState() {
    Card(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.common_data_load_failed), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}
