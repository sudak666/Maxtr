package ua.rytm.app.ui.screens.shifts
import androidx.compose.foundation.layout.navigationBarsPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Checkbox
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
import ua.rytm.app.ui.icons.BeachAccess
import ua.rytm.app.ui.icons.Bolt
import ua.rytm.app.ui.icons.ChevronLeft
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.EventAvailable
import ua.rytm.app.ui.icons.ExpandMore
import ua.rytm.app.ui.icons.Schedule
import ua.rytm.app.ui.icons.Style
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.theme.tabularNums

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
        factory = ShiftsViewModel.factory(app.shiftsRepository, dataUid, profileId),
    )
    val stats = viewModel.monthStats
    var shiftTypesSheetOpen by rememberSaveable { mutableStateOf(false) }
    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbar = LocalSnackbarHost.current ?: ownHost
    val errorMessage = viewModel.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }

    Scaffold(snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost) }) { padding ->
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
        item { HeroMetric(stats.earned) }
        item { ChipStats(stats) }
        item { IncomeChartSection(viewModel.sixMonthEarnings) }
        if (canEdit) item { QuickFillLauncher(onClick = viewModel::toggleQuickFillExpanded) }
        item { MonthNav(viewModel) }
        item { LegendRow(viewModel.shiftTypes) }
        if (!viewModel.loading && !viewModel.loadFailed && canEdit && stats.shiftsCount + stats.offCount == 0) {
            item { CalendarEmptyBanner(onQuickFill = { if (!viewModel.quickFillExpanded) viewModel.toggleQuickFillExpanded() }) }
        }
        item { WeekdayHeaderRow() }
        item { CalendarGrid(viewModel, canEdit) }
    }
    }

    if (canEdit && viewModel.quickFillExpanded) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = viewModel::toggleQuickFillExpanded,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(RytmRadii.Sheet))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                QuickFillPanel(
                    viewModel,
                    onOpenShiftTypes = {
                        viewModel.toggleQuickFillExpanded()
                        shiftTypesSheetOpen = true
                    },
                )
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
                viewModel.shiftTypes.forEach { type ->
                    ShiftSelectionRow(
                        type = type,
                        checked = type.id in viewModel.dayModalSelection,
                        onToggle = { viewModel.toggleDayModalType(type.id) },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::closeDayModal) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = viewModel::saveDayModal) { Text(stringResource(R.string.action_done)) }
                }
            }
        }
    }

    if (canEdit && shiftTypesSheetOpen) {
        ShiftTypesManagerSheet(repository = app.shiftsRepository, uid = dataUid, profileId = profileId, onDismiss = { shiftTypesSheetOpen = false })
    }
}

// Matches the PWA's .hero-metric: a subtle bg1->bg2 diagonal gradient plus a
// soft brand-purple glow shadow, and a green-gradient progress fill
// (.salary-bar-fill) rather than the theme's purple — same treatment
// FinanceScreen's HeroBalanceCard got in step 38.
@Composable
private fun HeroMetric(earned: Double) {
    val shape = MaterialTheme.shapes.large
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
            Text(stringResource(R.string.shifts_earned_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(maskedAmount(stringResource(R.string.money_uah, formatMoney(earned))), style = MaterialTheme.typography.displayMedium.tabularNums(), fontWeight = FontWeight.Black)
            val pct = (earned / SALARY_GOAL).coerceIn(0.0, 1.0)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    // 6% onSurface left the unfilled part of the bar at ~1.1:1
                    // (WCAG 1.4.11 wants 3:1 for meaningful non-text content).
                    .height(6.dp)
                    .clip(RoundedCornerShape(RytmRadii.Pill))
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
            Text(
                maskedAmount(stringResource(R.string.shifts_goal_progress, (pct * 100).toInt(), formatMoney(SALARY_GOAL))),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// Matches the PWA's .chip-stat/.chip-stat-icon: a pill with a small circular
// purple-gradient icon badge, not a plain Card — same default gradient every
// chip-stat-icon gets in index.html regardless of what it's showing.
@Composable
private fun ChipStats(stats: ShiftsViewModel.MonthStats) {
    RytmStatChipRow {
        item { RytmStatChip(RytmIcons.Schedule, stats.hours.toInt().toString(), stringResource(R.string.shifts_hours_short)) }
        item { RytmStatChip(RytmIcons.EventAvailable, stats.shiftsCount.toString(), stringResource(R.string.shifts_count)) }
        item { RytmStatChip(RytmIcons.BeachAccess, stats.offCount.toString(), stringResource(R.string.shifts_days_off)) }
    }
}

@Composable
internal fun ShiftSelectionRow(type: ShiftType, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RytmRadii.Control))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) {}
            .heightIn(min = RytmDimens.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(localizedDomainText(type.name), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun localizedPatternOptions(): List<Pair<String, String>> = listOf(
    "every" to stringResource(R.string.shift_pattern_daily),
    "alt" to stringResource(R.string.shift_pattern_alternate),
    "2_2" to stringResource(R.string.shift_pattern_2_2),
    "3_3" to stringResource(R.string.shift_pattern_3_3),
)

// Matches the PWA's .chart-section card + .chart-bars single-series bar
// chart (js/calendar.js's renderIncomeChart()) — current month solid purple,
// the other 5 faded purple, mirroring var(--purple)/rgba(139,92,246,.35).
@Composable
private fun IncomeChartSection(months: List<ShiftsViewModel.MonthEarning>) {
    val locale = LocalConfiguration.current.locales[0]
    val progress = motionProgress(months, 500)
    Card(shape = RoundedCornerShape(RytmRadii.AuthCard)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                Icon(RytmIcons.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(R.string.shifts_income_chart),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
            val maxVal = (months.maxOfOrNull { it.earned } ?: 0.0).coerceAtLeast(1.0)
            val curYm = YearMonth.now()
            val purple = MaterialTheme.colorScheme.primary
            // The chart was invisible to TalkBack: a bare Row of colored
            // Boxes with no semantics at all. One spoken summary carries the
            // same information the sighted reading does.
            val monthAmounts = months.map { m ->
                m.yearMonth.month.getDisplayName(TextStyle.FULL, locale) + " " +
                    stringResource(R.string.money_uah, formatMoney(m.earned))
            }
            val chartSummary = stringResource(R.string.shifts_income_chart) + ": " + monthAmounts.joinToString(", ")
            Row(
                Modifier.fillMaxWidth().height(80.dp)
                    .semantics(mergeDescendants = true) { contentDescription = chartSummary },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                months.forEach { m ->
                    val isCur = m.yearMonth == curYm
                    val heightFraction = ((m.earned / maxVal).coerceIn(0.0, 1.0).toFloat().coerceAtLeast(0.02f) * progress)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                                .background(if (isCur) purple else purple.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 3.dp).clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                months.forEach { m ->
                    val isCur = m.yearMonth == curYm
                    Text(
                        m.yearMonth.month.getDisplayName(TextStyle.SHORT, locale),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCur) FontWeight.Black else FontWeight.Bold,
                        color = if (isCur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickFillLauncher(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, ua.rytm.app.ui.theme.Purple3)),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(RytmIcons.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(stringResource(R.string.shifts_quick_fill), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.shifts_quick_fill_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(RytmIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Full editor opens in a modal sheet so expanding it never pushes the calendar
// far down the screen or causes a large in-place layout jump.
@Composable
private fun QuickFillPanel(vm: ShiftsViewModel, onOpenShiftTypes: () -> Unit) {
    var clearMonthConfirmVisible by rememberSaveable { mutableStateOf(false) }
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (vm.quickFillExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f) else MaterialTheme.colorScheme.surface,
        animationSpec = motionAwareSpec(androidx.compose.animation.core.tween(220)),
        label = "quick-fill-container",
    )
    val headerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (vm.quickFillExpanded) MaterialTheme.colorScheme.surface.copy(alpha = 0.78f) else Color.Transparent,
        animationSpec = motionAwareSpec(androidx.compose.animation.core.tween(220)),
        label = "quick-fill-header",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (vm.quickFillExpanded) 6.dp else 1.dp),
    ) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(RytmRadii.Row)).background(headerColor).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = vm::toggleQuickFillExpanded,
            ).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, ua.rytm.app.ui.theme.Purple3)),
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(RytmIcons.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp)) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(stringResource(R.string.shifts_quick_fill), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.shifts_quick_fill_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                if (vm.quickFillExpanded) 180f else 0f,
                animationSpec = motionAwareSpec(androidx.compose.animation.core.spring()),
                label = "chevron",
            )
            Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(RytmIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).rotate(rotation))
            }
        }
        ReducedMotionVisibility(visible = vm.quickFillExpanded) {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(RytmRadii.Input))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabeledDropdown(
                    label = stringResource(R.string.shift_type),
                    options = vm.shiftTypes.filter { !it.isOff }.map { it.id to it.name },
                    selected = vm.templateTypeId,
                    onSelect = vm::setTemplateType,
                )
                LabeledDropdown(
                    label = stringResource(R.string.shift_pattern),
                    options = localizedPatternOptions(),
                    selected = vm.templatePattern,
                    onSelect = vm::onTemplatePatternChanged,
                )
                androidx.compose.material3.Button(
                    onClick = {
                        vm.applyTemplate()
                        vm.toggleQuickFillExpanded()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = onOpenShiftTypes, modifier = Modifier.weight(1f)) {
                        Icon(RytmIcons.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.shift_types_title), modifier = Modifier.padding(start = 6.dp))
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { clearMonthConfirmVisible = true },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.shifts_clear_month))
                    }
                }
                // This button sits right next to "Shift types" in an equal-weight
                // Row: a mis-tap used to wipe the whole month instantly, with no
                // confirmation and no undo.
                if (clearMonthConfirmVisible) {
                    RytmDestructiveConfirm(
                        title = stringResource(R.string.shifts_clear_month),
                        body = stringResource(R.string.shifts_clear_month_confirm),
                        onConfirm = {
                            clearMonthConfirmVisible = false
                            vm.clearCurrentMonth()
                        },
                        onDismiss = { clearMonthConfirmVisible = false },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Control))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.shifts_autofill_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.shifts_autofill_body),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.autoFillSchedule.enabled, onCheckedChange = vm::setAutoFillEnabled)
                }
                ReducedMotionVisibility(visible = vm.autoFillSchedule.enabled) {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabeledDropdown(
                            label = stringResource(R.string.shift_type),
                            options = vm.shiftTypes.filter { !it.isOff }.map { it.id to it.name },
                            selected = vm.autoFillDraftTypeId,
                            onSelect = vm::setAutoFillDraftType,
                        )
                        LabeledDropdown(
                            label = stringResource(R.string.shift_pattern),
                            options = localizedPatternOptions(),
                            selected = vm.autoFillDraftPattern,
                            onSelect = vm::onAutoFillDraftPatternChanged,
                        )
                        DatePickerField(
                            value = vm.autoFillDraftAnchorDate,
                            onValueChange = vm::onAutoFillDraftAnchorDateChanged,
                            label = stringResource(R.string.shifts_anchor_date),
                            modifier = Modifier.fillMaxWidth(),
                            allowEmpty = false,
                        )
                        androidx.compose.material3.Button(onClick = vm::saveAutoFillConfig, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedLabel = localizedDomainText(options.firstOrNull { it.first == selected }?.second.orEmpty())
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(localizedDomainText(name)) }, onClick = { onSelect(id); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun CalendarEmptyBanner(onQuickFill: () -> Unit) {
    RytmEmptyState(
        icon = RytmIcons.Bolt,
        title = stringResource(R.string.shifts_empty_title),
        body = stringResource(R.string.shifts_empty_body),
        actionLabel = stringResource(R.string.shifts_quick_fill),
        onAction = onQuickFill,
    )
}

@Composable
private fun LegendRow(types: List<ShiftType>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(types) { type ->
            val accent = Color(type.colorHex)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(RytmRadii.Pill))
                    .background(accent.copy(alpha = 0.12f))
                    .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(RytmRadii.Pill))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent.copy(alpha = 0.25f))
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                )
                Text(
                    localizedDomainText(type.name),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    val weekdays = listOf(R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed, R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat, R.string.weekday_sun)
    Row(Modifier.fillMaxWidth()) {
        weekdays.forEachIndexed { i, d ->
            Text(
                stringResource(d),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = if (i >= 5) RytmSemantic.expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthNav(viewModel: ShiftsViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::goToPreviousMonth) { Icon(RytmIcons.ChevronLeft, contentDescription = stringResource(R.string.action_previous_month)) }
            val locale = LocalConfiguration.current.locales[0]
            val label = viewModel.visibleMonth.month.getDisplayName(TextStyle.FULL, locale) + " " + viewModel.visibleMonth.year
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(RytmRadii.Pill))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = viewModel::goToToday,
                    shape = RoundedCornerShape(RytmRadii.Pill),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text(stringResource(R.string.action_today), fontWeight = FontWeight.Bold) }
                IconButton(onClick = viewModel::goToNextMonth) { Icon(RytmIcons.ChevronRight, contentDescription = stringResource(R.string.action_next_month)) }
            }
        }
        Text(stringResource(R.string.shifts_edit_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarGrid(viewModel: ShiftsViewModel, canEdit: Boolean) {
    val month = viewModel.visibleMonth
    val cells = monthCalendarCells(month)
    val todayKey = viewModel.today.toString()

    // Plain Rows, not a LazyVerticalGrid nested inside the screen's own
    // LazyColumn: same-axis nested lazy containers can't measure themselves,
    // which is why the grid used to carry a hardcoded `height(rows * 80.dp)`.
    // At 360dp that wasted ~27dp per row; at >=600dp it clipped the cells.
    // Height now follows the cell's own aspect ratio at any width.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    val date = cell.date
                    if (date == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        val dateKey = date.toString()
                        DayCell(
                            date = date,
                            assigned = viewModel.shiftsFor(dateKey),
                            isToday = dateKey == todayKey,
                            isWeekend = cell.isWeekend,
                            enabled = canEdit,
                            onClick = { viewModel.openDayModal(dateKey) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // A short final week still has to keep the 7-column rhythm.
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: java.time.LocalDate,
    assigned: List<ShiftType>,
    isToday: Boolean,
    isWeekend: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        assigned.isNotEmpty() -> MaterialTheme.colorScheme.surfaceContainerHigh
        isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.03f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val weekendAccent = RytmSemantic.expense
    val shown = assigned.take(2)
    val overflow = assigned.size - shown.size

    // TalkBack used to read "25", "Д", "Н" as three unrelated fragments. One
    // merged, labelled, Role.Button node instead.
    val locale = LocalConfiguration.current.locales[0]
    val dayLabel = date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM", locale))
    val parts = buildList {
        add(dayLabel)
        if (isToday) add(stringResource(R.string.action_today))
        if (isWeekend) add(stringResource(R.string.shifts_weekend_a11y))
        assigned.forEach { add(localizedDomainText(it.name)) }
    }
    val cellDescription = parts.joinToString(", ")
    val editLabel = stringResource(R.string.shifts_edit_day_a11y)

    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .heightIn(min = RytmDimens.TouchTarget)
            .clip(RoundedCornerShape(RytmRadii.Control))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(RytmRadii.Control))
            .clickable(enabled = enabled, onClickLabel = editLabel, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = cellDescription }
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Weekends were signalled by a reddish tint alone (2.13:1 dark /
            // 2.53:1 light) — WCAG 1.4.1. A real marker carries the meaning
            // now; the number itself keeps full-contrast body color.
            if (isWeekend) Box(Modifier.size(5.dp).clip(CircleShape).background(weekendAccent))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEach { type ->
                val accent = Color(type.colorHex)
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        // Solid fill + computed on-color: the old 22%-alpha
                        // wash put the token letter at 2.26-2.49:1.
                        .background(accent)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(type.code, style = MaterialTheme.typography.labelSmall, color = onColorFor(accent), fontWeight = FontWeight.Black)
                }
            }
            // The model allows any number of shifts per day; the cell silently
            // showed the first two, so a third one read as "didn't save".
            if (overflow > 0) {
                Text(
                    "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
