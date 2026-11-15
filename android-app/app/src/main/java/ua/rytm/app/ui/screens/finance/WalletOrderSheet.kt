package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.R
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.FinanceSyncRepository
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.components.rememberRytmHaptics
import ua.rytm.app.ui.icons.ArrowUpward
import ua.rytm.app.ui.icons.GripVertical
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSpacing

/**
 * Wallet order (owner, 2026-11-15): new wallets land at the end of the strip on
 * the main screen, but they are usually the ones in use. Opened by a long press
 * on a wallet chip ([focusId] = that wallet, offered as "move to the front") or
 * from the wallets manager. Rows move by dragging the handle; the order is saved
 * once the drag ends — same gesture model as WidgetsManagerSheet, except the drag
 * starts at once on the handle (the rest of the row still scrolls the sheet).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WalletOrderSheet(
    repository: FinanceRepository,
    syncRepository: FinanceSyncRepository,
    recurringSyncRepository: ua.rytm.app.data.RecurringSyncRepository,
    uid: String,
    profileId: String,
    focusId: String?,
    onDismiss: () -> Unit,
    viewModel: WalletsManagerViewModel = viewModel(
        key = "wallets-$uid-$profileId",
        factory = WalletsManagerViewModel.factory(repository, syncRepository, recurringSyncRepository, uid, profileId),
    ),
) {
    val haptics = rememberRytmHaptics()
    val persisted = viewModel.wallets
    // Live preview while dragging; follows the stored order otherwise, so a sync
    // from another device still shows up.
    var localOrder by remember { mutableStateOf(persisted.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }
    if (draggingId == null) localOrder = persisted.map { it.id }
    val byId = persisted.associateBy { it.id }
    val focused = focusId?.let { byId[it] }

    RytmModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
        ) {
            RytmSheetTitle(stringResource(R.string.wallet_order_title), subtitle = stringResource(R.string.wallet_order_body))

            if (focused != null && localOrder.firstOrNull() != focused.id) {
                FilledTonalButton(
                    onClick = { haptics.confirm(); viewModel.moveFirst(focused.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) {
                    Icon(RytmIcons.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(RytmSpacing.sm))
                    Text(stringResource(R.string.wallet_order_move_first, localizedDomainText(focused.name)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // No spacing between rows: one row's measured height is exactly one slot.
            Column(Modifier.fillMaxWidth()) {
                localOrder.mapNotNull { byId[it] }.forEach { wallet ->
                    key(wallet.id) {
                        val isDragging = draggingId == wallet.id
                        val highlighted = isDragging || wallet.id == focusId
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { rowHeightPx = it.height.toFloat() }
                                .then(if (isDragging) Modifier.graphicsLayer { translationY = dragOffsetY }.zIndex(1f) else Modifier)
                                .padding(vertical = 2.dp)
                                .background(
                                    if (highlighted) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                    RoundedCornerShape(RytmRadii.Row),
                                )
                                .padding(end = RytmSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                RytmIcons.GripVertical,
                                contentDescription = stringResource(R.string.action_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(RytmDimens.TouchTarget)
                                    .padding(10.dp)
                                    .pointerInput(wallet.id) {
                                        detectDragGestures(
                                            onDragStart = { haptics.tick(); draggingId = wallet.id; dragOffsetY = 0f },
                                            onDragEnd = {
                                                // Read the order before clearing draggingId: that write
                                                // resets localOrder to the stored order on recomposition.
                                                val finalOrder = localOrder
                                                draggingId = null
                                                dragOffsetY = 0f
                                                if (finalOrder != persisted.map { it.id }) viewModel.reorder(finalOrder)
                                            },
                                            onDragCancel = { draggingId = null; dragOffsetY = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                val height = rowHeightPx
                                                if (height > 0f) {
                                                    val currentIndex = localOrder.indexOf(wallet.id)
                                                    val slots = (dragOffsetY / height).toInt()
                                                    val targetIndex = (currentIndex + slots).coerceIn(0, localOrder.lastIndex)
                                                    if (currentIndex >= 0 && targetIndex != currentIndex) {
                                                        localOrder = localOrder.toMutableList().apply {
                                                            removeAt(currentIndex)
                                                            add(targetIndex, wallet.id)
                                                        }
                                                        dragOffsetY -= (targetIndex - currentIndex) * height
                                                        haptics.tick()
                                                    }
                                                }
                                            },
                                        )
                                    },
                            )
                            Box(Modifier.size(10.dp).background(Color(wallet.colorHex), CircleShape))
                            Spacer(Modifier.width(RytmSpacing.md))
                            Text(
                                localizedDomainText(wallet.name),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(currencySymbol(wallet.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
