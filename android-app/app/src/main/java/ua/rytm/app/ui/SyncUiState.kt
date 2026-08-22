package ua.rytm.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.data.ProfileSyncCoordinator

val LocalRealtimeState = staticCompositionLocalOf<ProfileSyncCoordinator.RealtimeState> { ProfileSyncCoordinator.RealtimeState.Stopped }

@Composable
fun RealtimeStateBanner() {
    val message = when (LocalRealtimeState.current) {
        ProfileSyncCoordinator.RealtimeState.Offline -> R.string.sync_status_offline
        is ProfileSyncCoordinator.RealtimeState.Error -> R.string.sync_status_error
        else -> return
    }
    Card(Modifier.fillMaxWidth()) {
        Text(stringResource(message), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
