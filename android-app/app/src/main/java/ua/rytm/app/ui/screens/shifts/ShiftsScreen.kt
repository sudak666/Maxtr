package ua.rytm.app.ui.screens.shifts
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import ua.rytm.app.ui.icons.TipsAndUpdates
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ua.rytm.app.ui.ReducedMotionVisibility
import ua.rytm.app.ui.motionAwareSpec
import ua.rytm.app.ui.motionProgress
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.RytmApplication
import ua.rytm.app.R
import androidx.compose.ui.semantics.contentDescription
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.onColorFor
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.LocalSnackbarHost
import ua.rytm.app.ui.components.RytmDestructiveConfirm
import ua.rytm.app.ui.components.formatLongDate
import ua.rytm.app.ui.components.RytmStatChip
import ua.rytm.app.ui.components.RytmStatChipRow
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.DatePickerField
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.screens.finance.formatMoney
import java.time.YearMonth
import java.time.format.TextStyle
import androidx.compose.foundation.layout.imePadding
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.ArrowBack
import ua.rytm.app.ui.icons.BeachAccess
import ua.rytm.app.ui.icons.Bolt
import ua.rytm.app.ui.icons.ChevronLeft
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.Edit
import ua.rytm.app.ui.icons.EventAvailable
import ua.rytm.app.ui.icons.ExpandMore
import ua.rytm.app.ui.icons.Schedule
import ua.rytm.app.ui.icons.Style
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.theme.tabularNums
import kotlinx.coroutines.launch

// Implements SHIFTS_SCREEN_SPEC.md end to end as of step 39: hero metric,
// chip stats, 6-month earnings chart, collapsible quick-fill (template +
// autofill), legend, month grid, day-assignment sheet — full parity with
// js/calendar.js, closing step 8's disclosed gap.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsScreen() {
    val canEdit = LocalCanEditProfile.current
    val app = LocalContext.current.applicationContext as RytmApplication
    val accountUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val profileId by app.activeProfileStore.activeProfileId(accountUid).collectAsState(initial = DEFAULT_PROFILE_ID)
    val ownerUid by app.activeProfileStore.activeProfileOwnerUid(accountUid).collectAsState(initial = null)
    val dataUid = ownerUid ?: accountUid
    val viewModel: ShiftsViewModel = viewModel(
        key = "$dataUid|$profileId",
        factory = ShiftsViewModel.factory(
            app.shiftsRepository, dataUid, profileId,
            kotlinx.coroutines.flow.combine(app.financeRepository.transactions, app.financeRepository.currencyRates) { txs, rates ->
                txs.asSequence()
                    .filter { it.type == ua.rytm.app.ui.screens.finance.TxType.EXPENSE }
                    .groupBy({ it.date }, { app.financeRepository.convertCurrency(it.amount, it.currency, "UAH", rates) })
                    .mapValues { (_, v) -> v.sum() }
            },
        ),
    )
    val stats = viewModel.monthStats
    val salaryGoal by app.settingsStore.salaryGoal(accountUid).collectAsState(initial = ua.rytm.app.data.local.DEFAULT_SALARY_GOAL)
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    // Which sub-view Quick Fill's single sheet is currently showing -- see
    // the ModalBottomSheet block below for why this replaced a second,
    // independently-dismissible sheet for Shift Types.
    var quickFillShowingShiftTypes by rememberSaveable { mutableStateOf(false) }
    // Distinct "shiftTypes|" prefix, not just "$dataUid|$profileId" --
    // ShiftsViewModel above is requested with that exact bare key in this
    // same composable/ViewModelStoreOwner scope. androidx's viewModel()
    // keys its backing ViewModelStore purely by this string, not by
    // requested class, so two DIFFERENT ViewModel classes sharing one key
    // string collide: the second .get() call can return/cache the wrong
    // instance instead of throwing where it'd be caught immediately. This
    // is the real root cause of the "Shifts tab stuck loading/zeros" bug
    // reported live right after PR #478 introduced this second viewModel()
    // call with the same key as an oversight.
    val shiftTypesViewModel: ShiftTypesManagerViewModel = viewModel(
        key = "shiftTypes|$dataUid|$profileId",
        factory = ShiftTypesManagerViewModel.factory(app.shiftsRepository, dataUid, profileId),
    )
    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbar = LocalSnackbarHost.current ?: ownHost
    val errorMessage = viewModel.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }

    Scaffold(
        containerColor = Color.Transparent, // background comes from MainActivity's Surface (overdraw)
        snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost) },
    ) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
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
        item { HeroMetric(stats.earned, salaryGoal, canEdit) { editingGoal = true } }
        // The calendar is what this tab is opened for — it used to sit below
        // the stats, forecast and six-month chart, i.e. off-screen on a phone.
        item { MonthNav(viewModel) }
        item { LegendRow(viewModel.shiftTypes, brushTypeId = if (canEdit) viewModel.brushTypeId else null, onSelect = if (canEdit) viewModel::selectBrush else null) }
        if (!viewModel.loading && !viewModel.loadFailed && canEdit && stats.shiftsCount + stats.offCount == 0) {
            item { CalendarEmptyBanner(onQuickFill = { if (!viewModel.quickFillExpanded) viewModel.toggleQuickFillExpanded() }) }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WeekdayHeaderRow()
                CalendarGrid(viewModel, canEdit)
            }
        }
        if (canEdit) item { QuickFillLauncher(onClick = viewModel::toggleQuickFillExpanded) }
        item { ChipStats(stats) }
        item { ForecastCard(viewModel.currentForecast, viewModel.nextForecast, salaryGoal, viewModel.typicalShiftPay) }
        viewModel.spendingInsight?.let { insight -> item { SpendingInsightCard(insight) } }
        item { IncomeChartSection(viewModel.sixMonthEarnings) }
    }
    }

    if (canEdit && viewModel.quickFillExpanded) {
        // History: this used to be a raw Dialog with Shift Types' own
        // separate ModalBottomSheet stacked on top (PR #475 converted the
        // Dialog to a ModalBottomSheet too, matching every other manager
        // panel). That still wasn't enough -- reported live as still
        // happening: two independently-dismissible ModalBottomSheets
        // stacked in the same bottom-of-screen swipe area is itself
        // unsafe, regardless of which layer is a Dialog vs a Sheet. A
        // swipe-down drag that crosses the TOP sheet's dismiss threshold
        // removes it from composition while the finger is still moving;
        // the still-in-progress pointer events then land on whatever is
        // now underneath, which -- being itself swipe-to-dismiss -- keeps
        // interpreting the same continued downward motion as ITS OWN
        // dismiss drag, closing Quick Fill a beat later with a visible
        // jump. Fix: never nest a second independently-dismissible sheet
        // at all. Shift Types is now content SWAPPED IN inside this one
        // sheet (see ShiftTypesManagerContent, ShiftTypesManagerSheet.kt)
        // rather than its own sheet -- there's only ever one swipe-to-
        // dismiss surface active at a time.
        val quickFillSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                quickFillShowingShiftTypes = false
                viewModel.toggleQuickFillExpanded()
            },
            sheetState = quickFillSheetState,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // AnimatedContent, not a bare if/else swap. A raw if/else
                // here (tried first) reproduced a real bug live: tapping
                // "Типи змін" silently closed the whole sheet, no crash, no
                // exception anywhere in logcat -- consistent with M3's
                // ModalBottomSheet auto-dismissing (firing onDismissRequest
                // itself) when its content's measured height drops abruptly
                // in a single frame, which is exactly what swapping from
                // Quick Fill's taller form to Shift Types' shorter list did.
                // Switching to AnimatedContent (which interpolates the
                // container size across the transition instead of jumping)
                // was verified live, on-device, over adb, to fix it -- both
                // navigating in (tap) and back out (arrow) now work with no
                // dismiss. The exact root cause inside M3's sheet-anchor
                // recalculation isn't independently confirmed (no access to
                // its internals), but the fix is confirmed against the
                // actual reported symptom, not just plausible in theory.
                androidx.compose.animation.AnimatedContent(
                    targetState = quickFillShowingShiftTypes,
                    label = "quick-fill-content-swap",
                ) { showingShiftTypes ->
                    if (showingShiftTypes) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(onClick = { quickFillShowingShiftTypes = false }) {
                                    Icon(RytmIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                                }
                                Text(
                                    stringResource(R.string.shift_types_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                            ShiftTypesManagerContent(shiftTypesViewModel, showTitle = false)
                        }
                    } else {
                        QuickFillPanel(
                            viewModel,
                            onOpenShiftTypes = { quickFillShowingShiftTypes = true },
                        )
                    }
                }
            }
        }
    }

    val dateKey = viewModel.dayModalDateKey
    if (canEdit && dateKey != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeDayModal, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RytmSheetTitle(stringResource(R.string.shifts_choose))
                // Was the raw `dateKey` (a "2026-08-25" ISO string) shown
                // straight to the user.
                val modalDate = runCatching { java.time.LocalDate.parse(dateKey) }.getOrNull()
                Text(
                    modalDate?.let { formatLongDate(it) } ?: dateKey,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (viewModel.shiftTypes.isEmpty()) {
                    // A fresh profile has no shift types; the sheet used to be blank.
                    Text(stringResource(R.string.shifts_no_types), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            viewModel.closeDayModal()
                            quickFillShowingShiftTypes = true
                            if (!viewModel.quickFillExpanded) viewModel.toggleQuickFillExpanded()
                        },
                        shape = RoundedCornerShape(RytmRadii.Row),
                    ) { Text(stringResource(R.string.shift_types_title)) }
                }
                viewModel.shiftTypes.forEach { type ->
                    ShiftSelectionRow(
                        type = type,
                        checked = type.id in viewModel.dayModalSelection,
                        onToggle = { viewModel.toggleDayModalType(type.id) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = viewModel::closeDayModal) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = viewModel::saveDayModal) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }

    // Must outlive the dialog: a scope remembered inside `if (editingGoal)` was
    // cancelled the moment "Готово" closed it, before DataStore wrote — the
    // goal never saved (reported live).
    val goalScope = androidx.compose.runtime.rememberCoroutineScope()
    if (canEdit && editingGoal) {
        var text by rememberSaveable(editingGoal) {
            mutableStateOf(if (salaryGoal == 0.0) "" else java.math.BigDecimal.valueOf(salaryGoal).stripTrailingZeros().toPlainString())
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editingGoal = false },
            title = { Text(stringResource(R.string.shifts_goal_edit_title)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.shifts_goal_edit_label)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    // Used to override unfocusedBorderColor to
                    // onSurfaceVariant here (default `outline` was invisible
                    // in light theme at the time). Now that Theme.kt's
                    // `outline` itself is a real, visible hairline tone
                    // app-wide, this override is both redundant and actively
                    // inconsistent -- it made this one field's border
                    // brighter than every other field's. Removed to fall
                    // back to the shared default.
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ua.rytm.app.ui.screens.finance.parseMoneyInput(text)?.let { goalScope.launch {
                        app.settingsStore.setSalaryGoal(accountUid, it)
                        runCatching { app.widgetSettingsSyncRepository.saveSalaryGoal(dataUid, profileId, it) }
                    } }
                    editingGoal = false
                }) { Text(stringResource(R.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { editingGoal = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
