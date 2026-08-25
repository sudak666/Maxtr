package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import ua.rytm.app.ui.theme.RytmDimens
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import ua.rytm.app.data.WidgetSettingsSyncRepository
import ua.rytm.app.data.local.SettingsStore
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.BlueDark
import ua.rytm.app.ui.theme.BitcoinOrange
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.ArrowDownward
import ua.rytm.app.ui.icons.ArrowUpward
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.LocalFireDepartment
import ua.rytm.app.ui.icons.TipsAndUpdates

private data class WidgetDef(val key: String, @StringRes val title: Int, @StringRes val subtitle: Int, val icon: ImageVector, val color: Color)
private val widgetDefs = listOf(
    WidgetDef("goals", R.string.widget_goals, R.string.widget_goals_subtitle, RytmIcons.Flag, GreenDark),
    WidgetDef("dailyTip", R.string.widget_tip, R.string.widget_tip_subtitle, RytmIcons.TipsAndUpdates, BlueDark),
    WidgetDef("cryptoTop", R.string.widget_crypto, R.string.widget_crypto_subtitle, RytmIcons.LocalFireDepartment, BitcoinOrange),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsManagerSheet(settingsStore: SettingsStore, syncRepository: WidgetSettingsSyncRepository, uid: String, profileId: String, onDismiss: () -> Unit) {
    val config by settingsStore.financeWidgets.collectAsState(initial = ua.rytm.app.data.local.FinanceWidgetsConfig(emptySet(), emptyList()))
    val scope = rememberCoroutineScope()
    var syncError by rememberSaveable { mutableStateOf(false) }
    var busy by rememberSaveable { mutableStateOf(false) }
    fun update(block: suspend () -> Unit) = scope.launch {
        if (busy) return@launch
        busy = true
        runCatching { block(); syncRepository.save(uid, profileId) }
            .onSuccess { syncError = false }
            .onFailure { syncError = true }
        busy = false
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmSheetTitle(stringResource(R.string.widgets_title), subtitle = stringResource(R.string.widgets_body))
            Text(stringResource(R.string.widgets_body), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp).align(Alignment.CenterHorizontally))
            config.order.mapNotNull { key -> widgetDefs.firstOrNull { it.key == key } }.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(CircleShape).background(item.color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(stringResource(item.title), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(item.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        IconButton(onClick = { update { settingsStore.moveWidget(item.key, -1) } }, enabled = !busy && index > 0, modifier = Modifier.size(RytmDimens.TouchTarget)) { Icon(RytmIcons.ArrowUpward, stringResource(R.string.action_move_up), modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { update { settingsStore.moveWidget(item.key, 1) } }, enabled = !busy && index < config.order.lastIndex, modifier = Modifier.size(RytmDimens.TouchTarget)) { Icon(RytmIcons.ArrowDownward, stringResource(R.string.action_move_down), modifier = Modifier.size(18.dp)) }
                    }
                    Switch(checked = item.key in config.enabled, onCheckedChange = { on -> update { settingsStore.setWidgetEnabled(item.key, on) } }, enabled = !busy)
                }
            }
            if (syncError) Text(stringResource(R.string.widgets_sync_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
