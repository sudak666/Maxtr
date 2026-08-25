package ua.rytm.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.data.ProfileSyncCoordinator

val LocalRealtimeState = staticCompositionLocalOf<ProfileSyncCoordinator.RealtimeState> { ProfileSyncCoordinator.RealtimeState.Stopped }

/**
 * Retry action for [ScreenLoadErrorState]. Provided once by RytmNavHost so a
 * failed load is never a dead end; defaults to a no-op for previews/tests.
 */
val LocalRetryLoad = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Offline/error banner. Was a plain Card with `onSurfaceVariant` text — among
 * the other cards on the screen it did not read as a system state at all.
 * Now icon + accent container, so it is recognisably a status strip.
 */
@Composable
fun RealtimeStateBanner() {
    val state = LocalRealtimeState.current
    val message: Int
    val icon = when (state) {
        ProfileSyncCoordinator.RealtimeState.Offline -> {
            message = R.string.sync_status_offline
            Icons.Filled.CloudOff
        }
        is ProfileSyncCoordinator.RealtimeState.Error -> {
            message = R.string.sync_status_error
            Icons.Filled.WarningAmber
        }
        else -> return
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(stringResource(message), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Skeleton placeholder rows.
 *
 * The old loading state was a spinner inside a Card inserted as a list item
 * *above* the content — it pushed the whole list down and then vanished, a
 * visible layout jump on every load. Skeletons occupy roughly the shape of
 * what is coming instead.
 */
@Composable
fun ScreenLoadingState(rows: Int = 3) {
    val loadingLabel = stringResource(R.string.common_loading)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f * alpha * 2)
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = loadingLabel },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows) {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(shimmer))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SkeletonBar(shimmer, widthFraction = 0.55f)
                        SkeletonBar(shimmer, widthFraction = 0.35f)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(color: Color, widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color),
    )
}

/**
 * Load failure. Previously a flat card with red text and no action at all —
 * a dead end (pull-to-refresh existed but was neither visible nor mentioned).
 */
@Composable
fun ScreenLoadErrorState() {
    val retry = LocalRetryLoad.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                stringResource(R.string.common_data_load_failed),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = retry) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.action_retry),
                    modifier = Modifier.padding(start = 6.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
