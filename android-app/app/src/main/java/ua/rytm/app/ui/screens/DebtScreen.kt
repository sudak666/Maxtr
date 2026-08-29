package ua.rytm.app.ui.screens
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.launch
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.theme.onColorFor
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.components.RytmStatChip
import ua.rytm.app.ui.components.RytmStatChipRow
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.ReducedMotionVisibility
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.DatePickerField
import ua.rytm.app.ui.components.CurrencyPickerField
import ua.rytm.app.ui.screens.debt.DEBT_COLORS
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.DebtEntry
import ua.rytm.app.ui.screens.debt.DebtForecastCard
import ua.rytm.app.ui.screens.debt.DebtViewModel
import ua.rytm.app.ui.screens.debt.currentBalance
import ua.rytm.app.ui.screens.debt.paid
import ua.rytm.app.ui.screens.debt.todayLabel
import ua.rytm.app.ui.screens.finance.formatMoney
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.components.RytmDestructiveConfirm
import androidx.compose.runtime.LaunchedEffect
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import ua.rytm.app.ui.screens.debt.parsePlainDebtAmount
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.CheckCircle
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.Edit
import ua.rytm.app.ui.icons.Event
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.Receipt
import ua.rytm.app.ui.theme.tabularNums

// Implements the in-scope subset of CLAUDE.md §1.4: debt chips, hero balance,
// progress bar, chip stats, due chip, payoff-forecast burndown chart,
// collapsible info/history, payment CRUD with swipe-to-delete, FAB → new-
// payment sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtScreen(
    viewModel: DebtViewModel = viewModel(
        factory = DebtViewModel.factory(LocalContext.current.applicationContext as RytmApplication),
    ),
) {
    val cd = viewModel.currentDebt
    val canEdit = LocalCanEditProfile.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val historyHeaderIndex = 7 + (if (viewModel.loading) 1 else 0) + (if (viewModel.loadFailed) 1 else 0)

    fun collapseHistory() {
        if (!viewModel.historyExpanded) return
        viewModel.toggleHistoryPanel()
        scope.launch { listState.animateScrollToItem(historyHeaderIndex) }
    }

    Scaffold(
        floatingActionButton = {
            if (cd != null && canEdit) {
                val shape = RoundedCornerShape(RytmRadii.Pill)
                val collapse = viewModel.historyExpanded
                Row(
                    modifier = Modifier
                        .padding(bottom = RytmDimens.BottomContentClearance)
                        .shadow(10.dp, shape)
                        .clip(shape)
                        .background(
                            if (collapse) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary))
                            else Brush.linearGradient(listOf(ua.rytm.app.ui.theme.OrangeDark, ua.rytm.app.ui.theme.OrangeLight2)),
                        )
                        .clickable(role = Role.Button, onClick = if (collapse) ::collapseHistory else viewModel::openNewEntrySheet)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (collapse) RytmIcons.ExpandLess else RytmIcons.Add, contentDescription = null, tint = Color.White)
                    Text(stringResource(if (collapse) R.string.action_collapse_list else R.string.debt_payment), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = innerPadding.calculateBottomPadding() + RytmDimens.BottomContentClearance),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { RealtimeStateBanner() }
            if (viewModel.loading) item { ScreenLoadingState() }
            if (viewModel.loadFailed) item { ScreenLoadErrorState() }
            item { DebtChipsRow(viewModel, canEdit) }

            if (!viewModel.loading && !viewModel.loadFailed && cd == null) {
                item { EmptyDebtState() }
            } else if (cd != null) {
                item { HeroBalance(cd) }
                item { ProgressBarSection(cd) }
                item { ChipStatsRow(cd) }
                item { DebtForecastCard(cd) }
                item { InfoPanel(viewModel, cd, canEdit) }
                val newestEntries = cd.entries.reversed()
                val visibleEntries = if (viewModel.historyExpanded) newestEntries else newestEntries.take(3)
                if (visibleEntries.isEmpty()) {
                    item { EmptyEntriesState() }
                } else {
                    items(visibleEntries, key = { it.id }) { entry ->
                        DebtEntryRow(viewModel, entry, cd.currency, canEdit)
                    }
                }
                if (newestEntries.size > 3) {
                    item {
                        OutlinedButton(
                            onClick = { if (viewModel.historyExpanded) collapseHistory() else viewModel.toggleHistoryPanel() },
                            // Was `padding(end = 178.dp)` — an eyeballed offset
                            // to dodge the FAB horizontally, leaving ~110dp for
                            // the label on a 320dp screen; fragile against any
                            // FAB-width/translation change. The follow-up that
                            // dropped it to a bare fillMaxWidth() assumed the
                            // list's own bottom contentPadding already cleared
                            // the FAB too — it doesn't, that padding only clears
                            // the bottom NAV bar (see RytmDimens.FabRowClearance's
                            // own comment) — so this button ended up sitting
                            // directly behind "+ Платіж" (reported live via
                            // screenshot). Real fix: extra bottom margin so this
                            // button stacks fully above the FAB instead of
                            // narrowing to dodge it sideways.
                            modifier = Modifier.fillMaxWidth().padding(bottom = RytmDimens.FabRowClearance),
                            shape = RoundedCornerShape(RytmRadii.Row),
                        ) {
                            Text(stringResource(if (viewModel.historyExpanded) R.string.action_collapse_list else R.string.action_view_all))
                        }
                    }
                }
            }
        }
    }

    if (canEdit && viewModel.newEntrySheetOpen && cd != null) {
        NewEntrySheet(viewModel, cd)
    }

    if (viewModel.pendingDeleteDebt) {
        RytmDestructiveConfirm(
            title = stringResource(R.string.debt_delete_title),
            body = stringResource(R.string.debt_delete_body),
            onConfirm = viewModel::confirmDeleteDebt,
            onDismiss = viewModel::cancelDeleteDebt,
        )
    }

    if (viewModel.pendingDeleteEntryId != null) {
        RytmDestructiveConfirm(
            title = stringResource(R.string.debt_payment_delete_title),
            body = stringResource(R.string.debt_payment_delete_body),
            onConfirm = viewModel::confirmDeleteEntry,
            onDismiss = viewModel::cancelDeleteEntry,
        )
    }

    // Transient save failures are a notification, not a decision — snackbar,
    // like Finance/Shifts/Settings, not a modal AlertDialog.
    viewModel.errorMessageRes?.let { messageRes ->
        val message = stringResource(messageRes)
        val host = LocalSnackbarHost.current
        LaunchedEffect(messageRes) {
            host?.showSnackbar(message)
            viewModel.consumeError()
        }
    }
}

@Composable
private fun DebtChipsRow(viewModel: DebtViewModel, canEdit: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.debts, key = { it.id }) { debt ->
            val index = viewModel.debts.indexOf(debt)
            val active = debt.id == viewModel.currentDebt?.id
            Card(
                onClick = { viewModel.switchDebt(debt.id) },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (active) Color(DEBT_COLORS[index % DEBT_COLORS.size]) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    // Fixed white on these mid-tone accents measured as low as
                    // 2.15:1; the on-color is computed from the chip's own
                    // luminance instead.
                    contentColor = if (active) onColorFor(Color(DEBT_COLORS[index % DEBT_COLORS.size])) else MaterialTheme.colorScheme.onSurface,
                ),
                shape = RoundedCornerShape(RytmRadii.Pill),
            ) {
                Text(localizedDomainText(debt.name), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.SemiBold)
            }
        }
        if (canEdit) item {
            var addOpen by rememberSaveable { mutableStateOf(false) }
            Card(onClick = { addOpen = true }, shape = RoundedCornerShape(RytmRadii.Pill)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(RytmIcons.Add, contentDescription = null, modifier = Modifier)
                    Text(stringResource(R.string.debt_new_default), fontWeight = FontWeight.SemiBold)
                }
            }
            if (addOpen) {
                var name by rememberSaveable { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { addOpen = false },
                    title = { Text(stringResource(R.string.debt_new_default)) },
                    text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.field_name)) }) },
                    confirmButton = {
                        val fallbackName = stringResource(R.string.debt_new_default)
                        Button(onClick = { viewModel.addDebt(name, fallbackName); addOpen = false }) { Text(stringResource(R.string.action_add)) }
                    },
                    dismissButton = { OutlinedButton(onClick = { addOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
                )
            }
        }
    }
}

@Composable
private fun EmptyDebtState() {
    // Was the only empty state in the app with no icon at all.
    RytmEmptyState(
        icon = RytmIcons.AccountBalanceWallet,
        title = stringResource(R.string.debt_empty_title),
        body = stringResource(R.string.debt_empty_body),
    )
}

// Matches the PWA's .hero-metric: a subtle bg1->bg2 diagonal gradient plus a
// soft brand-purple glow shadow, not a flat Card — same treatment
// FinanceScreen's HeroBalanceCard (step 38) and Shifts' HeroMetric (step 39) got.
@Composable
private fun HeroBalance(cd: Debt) {
    val shape = MaterialTheme.shapes.large
    val paidOff = cd.startAmount > 0 && cd.currentBalance() <= 0
    // Fires exactly once, the moment this debt's balance crosses to zero
    // during a live session (e.g. right after logging the payment that
    // clears it) -- not on every later visit to an already-cleared debt.
    // `previousPaidOff` seeds from the CURRENT state so a debt that was
    // already paid off before this composable ever ran doesn't celebrate.
    var previousPaidOff by remember(cd.id) { mutableStateOf(paidOff) }
    var celebrateTrigger by remember(cd.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(paidOff) {
        if (paidOff && !previousPaidOff) celebrateTrigger = (celebrateTrigger ?: 0) + 1
        previousPaidOff = paidOff
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant))),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.debt_current_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maskedAmount("${formatMoney(cd.currentBalance())} ${cd.currency}"), style = MaterialTheme.typography.displayMedium.tabularNums(), fontWeight = FontWeight.Black)
        }
        ua.rytm.app.ui.components.CelebrationBurst(trigger = celebrateTrigger, modifier = Modifier.matchParentSize())
    }
}

// Matches the PWA's .salary-bar-fill (reused as-is for #debt-progress-fill,
// same class) — a green gradient, not the theme's purple.
@Composable
private fun ProgressBarSection(cd: Debt) {
    val target = cd.startAmount
    if (target <= 0) return
    val paid = cd.paid()
    val pct = (paid / target).coerceIn(0.0, 1.0)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.debt_paid), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(RytmRadii.Pill))
                // 6% onSurface put the empty part of the bar at ~1.1:1
                // against the surface; WCAG 1.4.11 wants 3:1 for meaningful
                // non-text content.
                .background(MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct.toFloat())
                    .fillMaxSize()
                    .clip(RoundedCornerShape(RytmRadii.Pill))
                    .background(Brush.horizontalGradient(listOf(ua.rytm.app.ui.theme.GreenDark, ua.rytm.app.ui.theme.GreenDark2))),
            )
        }
    }
}

// Matches the PWA's .chip-stat-row: due date folded in as a 4th chip
// (#debt-due-chip lives inside the same row, not a separate banner card).
@Composable
private fun ChipStatsRow(cd: Debt) {
    val due = dueChipInfo(cd)
    RytmStatChipRow {
        item { RytmStatChip(RytmIcons.AccountBalanceWallet, maskedAmount("${formatMoney(cd.startAmount)} ${cd.currency}"), stringResource(R.string.debt_start_amount)) }
        item { RytmStatChip(RytmIcons.CheckCircle, maskedAmount("${formatMoney(cd.paid())} ${cd.currency}"), stringResource(R.string.debt_paid)) }
        item { RytmStatChip(RytmIcons.Receipt, cd.entries.size.toString(), stringResource(R.string.debt_payments)) }
        if (due != null) item { RytmStatChip(RytmIcons.Event, due, stringResource(R.string.debt_due_date)) }
    }
}

@Composable
private fun dueChipInfo(cd: Debt): String? {
    if (cd.dueDate.isBlank()) return null
    val diffDays = try {
        val due = java.time.LocalDate.parse(cd.dueDate)
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), due)
    } catch (e: Exception) { return null }
    return when {
        diffDays < 0 -> stringResource(R.string.debt_days_overdue, -diffDays)
        diffDays == 0L -> stringResource(R.string.action_today)
        else -> stringResource(R.string.debt_days_future, diffDays)
    }
}

@Composable
private fun InfoPanel(viewModel: DebtViewModel, cd: Debt, canEdit: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Card(shape = RoundedCornerShape(RytmRadii.Input), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Input)).clickable(enabled = canEdit, onClick = viewModel::toggleInfoPanel).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.debt_details), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Was a filled primaryContainer circle around this pencil —
                // every per-payment row's own edit pencil just below (see
                // DebtEntryContent's IconButton) is a bare icon with no
                // circle. Same "edit" action, two different visual
                // treatments a few hundred px apart on the same screen
                // (reported live via screenshot). Bare icon matches the
                // row-level convention already established on this exact tab.
                if (canEdit) Icon(if (viewModel.infoExpanded) RytmIcons.Close else RytmIcons.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        ReducedMotionVisibility(visible = viewModel.infoExpanded) {
            var name by remember(cd.id) { mutableStateOf(cd.name) }
            var note by remember(cd.id) { mutableStateOf(cd.note) }
            var currency by remember(cd.id) { mutableStateOf(cd.currency) }
            var startAmount by remember(cd.id) { mutableStateOf(if (cd.startAmount == 0.0) "" else cd.startAmount.toString()) }
            var dueDate by remember(cd.id) { mutableStateOf(cd.dueDate) }

            fun commit() { viewModel.updateInfo(name, note, currency, startAmount.toDoubleOrNull() ?: 0.0, dueDate) }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.field_name)) })
                OutlinedTextField(value = note, onValueChange = { note = it; commit() }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.note_label)) })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = startAmount, onValueChange = { startAmount = it; commit() }, modifier = Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.debt_start_amount)) })
                    CurrencyPickerField(value = currency, onValueChange = { currency = it; commit() }, modifier = Modifier.weight(1f))
                }
                DatePickerField(value = dueDate, onValueChange = { dueDate = it; commit() }, label = stringResource(R.string.debt_due_date), modifier = Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::requestDeleteCurrentDebt) {
                    Icon(RytmIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.debt_delete_title), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EmptyEntriesState() {
    // Same fix as the "View all"/"Collapse" button a few lines up in
    // DebtScreen (see its own comment): the list's contentPadding only
    // clears the bottom NAV bar, not the floating "+ Платіж" FAB. With
    // zero entries this empty-state text is the LAST list item, so it
    // sat directly behind the FAB with no clearance of its own (reported
    // live, screenshot: "Ще немає платежів" half-covered by the button).
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp + RytmDimens.FabRowClearance), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.debt_payments_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebtEntryRow(viewModel: DebtViewModel, entry: DebtEntry, currency: String, canEdit: Boolean) {
    DebtEntrySwipeContainer(
        canEdit = canEdit,
        onDelete = { viewModel.requestDeleteEntry(entry.id) },
        pendingConfirmation = viewModel.pendingDeleteEntryId == entry.id,
    ) {
        DebtEntryContent(viewModel, entry, currency)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebtEntrySwipeContainer(canEdit: Boolean, onDelete: () -> Unit, pendingConfirmation: Boolean = false, content: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    var deleteCommitted by rememberSaveable { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { swipeThresholdPx },
        confirmValueChange = { value ->
            if (canEdit && value == SwipeToDismissBoxValue.EndToStart) {
                if (!deleteCommitted) {
                    deleteCommitted = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }
                true
            } else false
        },
    )
    // onDelete() above only REQUESTS deletion -- a RytmDestructiveConfirm
    // dialog decides whether it actually happens. But confirmValueChange
    // already told SwipeToDismissBoxState the EndToStart transition is
    // confirmed the moment the swipe crossed the threshold, so the box
    // itself has already committed to looking "dismissed" regardless of
    // what the dialog decides. Cancelling the dialog left this row
    // permanently stuck in that dismissed-looking state -- blank content,
    // the red reveal frozen open (reported live, screenshot). If the
    // delete actually goes through, this row leaves composition entirely
    // (removed from the entries list) and this effect never runs a
    // meaningful reset; if it's cancelled, the entry is still there and
    // this snaps the row back to normal.
    LaunchedEffect(pendingConfirmation) {
        if (!pendingConfirmation && deleteCommitted) {
            deleteCommitted = false
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        backgroundContent = {
            // The red fill used to live only on the narrow icon-width inner
            // Box -- the rest of the revealed area (between the shrinking
            // content and that square) was unpainted, showing the page
            // background through as a visible gap (reported live,
            // screenshot). Painting red on the OUTER full-width Box, same
            // as FinanceScreen's already-correct swipe-delete row, makes
            // the whole reveal one continuous red field.
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(Modifier.fillMaxHeight().width(SwipeRevealWidth), contentAlignment = Alignment.Center) {
                    Icon(RytmIcons.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onError)
                }
            }
        },
    ) {
        content()
    }
}

@Composable
private fun DebtEntryContent(viewModel: DebtViewModel, entry: DebtEntry, currency: String) {
    val editing = viewModel.entryEditId == entry.id
    Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                if (editing) {
                    var amount by remember(entry.id) { mutableStateOf(entry.amount) }
                    var balance by remember(entry.id) { mutableStateOf(entry.balance.toString()) }
                    var date by remember(entry.id) { mutableStateOf(entry.date) }
                    OutlinedTextField(value = amount, onValueChange = { amount = it; viewModel.updateEntryAmount(entry, it) }, singleLine = true, label = { Text(stringResource(R.string.amount_label)) }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = balance, onValueChange = { balance = it; viewModel.updateEntryBalance(entry, it) }, singleLine = true, label = { Text(stringResource(R.string.debt_balance)) }, modifier = Modifier.weight(1f))
                        DatePickerField(value = date, onValueChange = { date = it; viewModel.updateEntryDate(entry, it) }, label = stringResource(R.string.date_label), modifier = Modifier.weight(1f), allowEmpty = false)
                    }
                    TextButton(onClick = { viewModel.toggleEntryEdit(entry.id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_done)) }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${entry.amount} $currency", fontWeight = FontWeight.SemiBold)
                            Text(maskedAmount(stringResource(R.string.debt_balance_value, formatMoney(entry.balance), currency)) + " · ${entry.date}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.toggleEntryEdit(entry.id) }) { Icon(RytmIcons.Edit, contentDescription = stringResource(R.string.action_edit)) }
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewEntrySheet(viewModel: DebtViewModel, cd: Debt) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amount by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayLabel()) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Per-field validation, marked on the field itself: the sheet used to
    // accept anything and only report a problem after the save round-trip.
    val amountInvalid = submitted && parsePlainDebtAmount(amount) == null
    val balanceInvalid = submitted && balance.isNotBlank() && parsePlainDebtAmount(balance) == null

    ModalBottomSheet(onDismissRequest = viewModel::closeNewEntrySheet, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RytmSheetTitle(stringResource(R.string.debt_new_payment))
            OutlinedTextField(
                value = amount,
                onValueChange = { new ->
                    // Real bug found while testing: comparing against autoFillBalance(amount)
                    // AFTER reassigning amount==new made the "did the user manually edit
                    // balance away from auto-fill" check compare the new value to itself,
                    // so auto-fill silently froze after the very first keystroke. Capture
                    // whether balance was still following auto-fill BEFORE updating amount.
                    val stillAutoFilled = balance.isEmpty() || balance == viewModel.autoFillBalance(amount)
                    amount = new
                    if (stillAutoFilled) balance = viewModel.autoFillBalance(new)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = amountInvalid,
                supportingText = if (amountInvalid) ({ Text(stringResource(R.string.validation_invalid_amount)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                label = { Text(stringResource(R.string.debt_payment_amount)) },
            )
            OutlinedTextField(
                value = balance,
                onValueChange = { balance = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = balanceInvalid,
                supportingText = if (balanceInvalid) ({ Text(stringResource(R.string.validation_invalid_amount)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                label = { Text(stringResource(R.string.debt_new_balance)) },
            )
            DatePickerField(value = date, onValueChange = { date = it }, label = stringResource(R.string.date_label), modifier = Modifier.fillMaxWidth(), allowEmpty = false)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = viewModel::closeNewEntrySheet) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = {
                        submitted = true
                        if (parsePlainDebtAmount(amount) != null) viewModel.addEntry(amount, balance, date)
                    },
                    enabled = !viewModel.saving,
                ) { Text(stringResource(R.string.action_add)) }
            }
        }
    }
}
