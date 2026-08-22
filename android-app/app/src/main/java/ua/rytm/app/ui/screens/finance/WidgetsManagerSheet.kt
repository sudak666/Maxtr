package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TipsAndUpdates
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import ua.rytm.app.data.WidgetSettingsSyncRepository
import ua.rytm.app.data.local.SettingsStore

private data class WidgetDef(val key: String, val title: String, val subtitle: String, val icon: ImageVector, val color: Color)
private val widgetDefs = listOf(
    WidgetDef("goals", "Цілі", "Прогрес накопичення на гаманцях", Icons.Filled.Flag, Color(0xFF10B981)),
    WidgetDef("dailyTip", "Порада дня", "Коротка фінансова порада, що змінюється щодня", Icons.Filled.TipsAndUpdates, Color(0xFF3B82F6)),
    WidgetDef("cryptoTop", "Топ криптовалюти", "Курс і графік за 7 днів (BTC, ETH)", Icons.Filled.LocalFireDepartment, Color(0xFFF7931A)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetsManagerSheet(settingsStore: SettingsStore, syncRepository: WidgetSettingsSyncRepository, uid: String, profileId: String, onDismiss: () -> Unit) {
    val config by settingsStore.financeWidgets.collectAsState(initial = ua.rytm.app.data.local.FinanceWidgetsConfig(emptySet(), emptyList()))
    val scope = rememberCoroutineScope()
    var syncError by remember { mutableStateOf<String?>(null) }
    fun update(block: suspend () -> Unit) = scope.launch {
        block()
        runCatching { syncRepository.save(uid, profileId) }
            .onSuccess { syncError = null }
            .onFailure { syncError = "Не вдалося синхронізувати налаштування віджетів" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Віджети", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Увімкни, вимкни й переставляй блоки на вкладці «Фінанси».", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp).align(Alignment.CenterHorizontally))
            config.order.mapNotNull { key -> widgetDefs.firstOrNull { it.key == key } }.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(CircleShape).background(item.color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column {
                        IconButton(onClick = { update { settingsStore.moveWidget(item.key, -1) } }, enabled = index > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowUpward, "Вище", modifier = Modifier.size(18.dp)) }
                        IconButton(onClick = { update { settingsStore.moveWidget(item.key, 1) } }, enabled = index < config.order.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowDownward, "Нижче", modifier = Modifier.size(18.dp)) }
                    }
                    Switch(checked = item.key in config.enabled, onCheckedChange = { on -> update { settingsStore.setWidgetEnabled(item.key, on) } })
                }
            }
            syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
