package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import ua.rytm.app.ui.theme.RytmDimens
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import ua.rytm.app.data.WidgetSettingsSyncRepository
import ua.rytm.app.data.local.FinanceWidgetsConfig
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
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.GripVertical
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
    val config by settingsStore.financeWidgets.collectAsState(initial = FinanceWidgetsConfig(emptySet(), emptyList()))
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

    // Drag-to-reorder replaced a pair of up/down arrow buttons (account
    // owner's explicit call — matches the tactile drag-handle pattern used
    // for reordering everywhere else in the industry, e.g. home-screen/
    // notification-settings editors). Only 3 widgets ever exist, so a plain
    // Column (not LazyColumn) is fine — no virtualization needed.
    // [localOrder] is the live preview during a drag; it resets from the
    // persisted [config] whenever nothing is being dragged, so an external
    // change (sync) still reaches the UI. The final order is only persisted
    // via replaceFinanceWidgets() once the drag ends.
    var localOrder by remember { mutableStateOf(config.order) }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    if (draggingKey == null) localOrder = config.order

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(start = 18.dp, end = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RytmSheetTitle(stringResource(R.string.widgets_title), subtitle = stringResource(R.string.widgets_body))
            localOrder.mapNotNull { key -> widgetDefs.firstOrNull { it.key == key } }.forEach { item ->
                key(item.key) {
                    val isDragging = draggingKey == item.key
                    val rowModifier = if (isDragging) {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                            .graphicsLayer { translationY = dragOffsetY }
                            .zIndex(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .onSizeChanged { rowHeightPx = it.height.toFloat() }
                    }
                    Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            RytmIcons.GripVertical,
                            contentDescription = stringResource(R.string.action_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(RytmDimens.TouchTarget)
                                .padding(10.dp)
                                .pointerInput(item.key) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { draggingKey = item.key; dragOffsetY = 0f },
                                        onDragEnd = {
                                            // Capture the just-dragged order into a plain val *before*
                                            // clearing draggingKey — that write schedules a recomposition
                                            // that resets `localOrder` back to the stale `config.order`,
                                            // and since update{} launches its block asynchronously, it
                                            // would otherwise read `localOrder` *after* that reset had
                                            // already happened, silently persisting the pre-drag order.
                                            val finalOrder = localOrder
                                            draggingKey = null
                                            dragOffsetY = 0f
                                            update { settingsStore.replaceFinanceWidgets(config.copy(order = finalOrder)) }
                                        },
                                        onDragCancel = { draggingKey = null; dragOffsetY = 0f },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val height = rowHeightPx
                                            if (height > 0f) {
                                                val currentIndex = localOrder.indexOf(item.key)
                                                val slots = (dragOffsetY / height).toInt()
                                                val targetIndex = (currentIndex + slots).coerceIn(0, localOrder.lastIndex)
                                                if (targetIndex != currentIndex) {
                                                    localOrder = localOrder.toMutableList().apply {
                                                        removeAt(currentIndex)
                                                        add(targetIndex, item.key)
                                                    }
                                                    dragOffsetY -= (targetIndex - currentIndex) * height
                                                }
                                            }
                                        },
                                    )
                                },
                        )
                        Box(Modifier.size(34.dp).clip(CircleShape).background(item.color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(stringResource(item.title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(item.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // Deliberately NOT `enabled = !busy`: busy is shared across the
                        // whole sheet (guards against overlapping update() calls), but
                        // reflecting it here disabled every row's Switch — including
                        // ones the user didn't touch — for the duration of the *other*
                        // row's background sync, which read as all 3 switches
                        // flickering every time any one of them was tapped.
                        Switch(checked = item.key in config.enabled, onCheckedChange = { on -> update { settingsStore.setWidgetEnabled(item.key, on) } }, colors = ua.rytm.app.ui.theme.rytmSwitchColors())
                    }
                }
            }
            if (syncError) Text(stringResource(R.string.widgets_sync_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}
