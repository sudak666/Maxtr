package ua.rytm.app.ui.screens.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.components.RytmStatChip
import ua.rytm.app.ui.components.RytmStatChipRow
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.components.RytmDestructiveConfirm
import ua.rytm.app.ui.LocalSnackbarHost
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Check
import ua.rytm.app.ui.icons.CheckCircle
import ua.rytm.app.ui.icons.Checklist
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.ShoppingCart

// Implements SHOPPING_SCREEN_SPEC.md end to end: chip stats, add form,
// sorted checklist (unbought first), clear-bought with confirm, empty
// state. Room-backed from the start (ShoppingRepository), no sample-only
// UI-layer step — the pattern is now proven from Finance (Steps 2-5).
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel = viewModel(
        factory = ShoppingViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val canEdit = LocalCanEditProfile.current
    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbarHostState = LocalSnackbarHost.current ?: ownHost
    val scope = rememberCoroutineScope()
    // One destructive-action pattern app-wide (see FinanceScreen): a single
    // item deletion is optimistic + undoable, never an instant hard delete.
    // The row used to carry BOTH a swipe-to-delete and a visible trash button
    // that deleted straight through with no confirmation and no undo.
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val deletedLabel = stringResource(R.string.common_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    fun requestDelete(id: String) {
        if (pendingDeleteId != null) return
        pendingDeleteId = id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = deletedLabel,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result != SnackbarResult.ActionPerformed) viewModel.delete(id)
            pendingDeleteId = null
        }
    }
    viewModel.errorMessageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }
    Scaffold(
        // The host itself lives in RytmNavHost now — one per app.
        snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost, Modifier.padding(bottom = RytmDimens.BottomContentClearance)) },
    ) { innerPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = RytmDimens.BottomContentClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { RealtimeStateBanner() }
        if (viewModel.loading) item { ScreenLoadingState() }
        if (viewModel.loadFailed) item { ScreenLoadErrorState() }
        item { ChipStatsRow(remaining = viewModel.remainingCount, bought = viewModel.boughtCount) }
        if (canEdit) item { AddItemForm(viewModel) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.shopping_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (canEdit && viewModel.boughtCount > 0) {
                    TextButton(
                        onClick = viewModel::requestClearBought,
                        shape = RoundedCornerShape(RytmRadii.Pill),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) { Text(stringResource(R.string.shopping_clear_bought), fontWeight = FontWeight.Bold) }
                }
            }
        }

        val sorted = viewModel.sortedItems.filterNot { it.id == pendingDeleteId }
        if (!viewModel.loading && !viewModel.loadFailed && sorted.isEmpty()) {
            item { ShoppingEmptyState() }
        } else {
            items(sorted, key = { it.id }) { item ->
                ShoppingRow(item = item, canEdit = canEdit, onToggle = { viewModel.toggle(item, it) }, onDelete = { requestDelete(item.id) })
            }
        }
    }
    }

    if (viewModel.clearConfirmVisible) {
        RytmDestructiveConfirm(
            title = stringResource(R.string.shopping_clear_bought),
            body = stringResource(R.string.shopping_clear_confirmation),
            onConfirm = viewModel::confirmClearBought,
            onDismiss = viewModel::cancelClearBought,
        )
    }
}

@Composable
private fun ChipStatsRow(remaining: Int, bought: Int) {
    // Shared component + LazyRow: the fixed weight(1f) grid squeezed the
    // Ukrainian labels at 360dp / fontScale 1.3.
    RytmStatChipRow {
        item { RytmStatChip(RytmIcons.Checklist, remaining.toString(), stringResource(R.string.shopping_remaining)) }
        item { RytmStatChip(RytmIcons.CheckCircle, bought.toString(), stringResource(R.string.shopping_bought)) }
    }
}

@Composable
private fun AddItemForm(viewModel: ShoppingViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        OutlinedTextField(
            value = viewModel.nameInput,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.shopping_item_name)) },
            placeholder = { Text(stringResource(R.string.shopping_item_example)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            isError = viewModel.nameInvalid,
            supportingText = if (viewModel.nameInvalid) ({ Text(stringResource(R.string.shopping_name_required)) }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = viewModel.qtyInput,
            onValueChange = viewModel::onQtyChange,
            label = { Text(stringResource(R.string.shopping_quantity)) },
            placeholder = { Text("1") },
            modifier = Modifier.width(108.dp),
            singleLine = true,
            isError = viewModel.quantityInvalid,
            supportingText = if (viewModel.quantityInvalid) ({ Text(stringResource(R.string.shopping_quantity_invalid)) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addItem() }),
        )
        FilledTonalButton(
            onClick = viewModel::addItem,
            enabled = !viewModel.saving && viewModel.nameInput.isNotBlank(),
            modifier = Modifier.heightIn(min = RytmDimens.TouchTarget),
        ) {
            // Icon-only button: without this it announced as a bare "button".
            Icon(RytmIcons.Add, contentDescription = stringResource(R.string.action_add))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShoppingRow(item: ShoppingItem, canEdit: Boolean, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    var deleteCommitted by remember(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(positionalThreshold = { swipeThresholdPx }, confirmValueChange = { value ->
        if (canEdit && value == SwipeToDismissBoxValue.EndToStart) {
            if (!deleteCommitted) { deleteCommitted = true; onDelete() }
            true
        } else false
    })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.fillMaxHeight().width(SwipeRevealWidth).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.Center) {
                    Icon(RytmIcons.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onError)
                }
            }
        },
    ) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RytmRadii.Control))
                .toggleable(value = item.done, enabled = canEdit, role = Role.Checkbox, onValueChange = onToggle)
                .semantics(mergeDescendants = true) {}
                .heightIn(min = RytmDimens.TouchTarget)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.done) Brush.linearGradient(listOf(ua.rytm.app.ui.theme.PurpleDark, ua.rytm.app.ui.theme.Purple3))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
                    )
                    .border(
                        width = 2.dp,
                        color = if (item.done) Color.Transparent else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.done) Icon(RytmIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (item.qty > 1) {
                Text(
                    "×${item.qty}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun ShoppingEmptyState() {
    RytmEmptyState(
        icon = RytmIcons.ShoppingCart,
        title = stringResource(R.string.shopping_empty_title),
        body = stringResource(R.string.shopping_empty_body),
    )
}
