package ua.rytm.app.ui.screens.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.components.DatePickerField
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.OperationSyncStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.screens.finance.formatMoneyWithCurrency
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

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
        factory = ShiftsViewModel.factory(app.shiftsRepository, app.shiftsSyncRepository, dataUid, profileId),
    )
    val stats = viewModel.monthStats
    var shiftTypesSheetOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val errorMessage = viewModel.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { RealtimeStateBanner() }
        item { OperationSyncStateBanner(viewModel.syncState) }
        if (viewModel.loading) item { ScreenLoadingState() }
        if (viewModel.loadFailed) item { ScreenLoadErrorState() }
        item { HeroMetric(stats.earned) }
        item { ChipStats(stats) }
        item { IncomeChartSection(viewModel.sixMonthEarnings) }
        if (canEdit) item { QuickFillPanel(viewModel, onOpenShiftTypes = { shiftTypesSheetOpen = true }) }
        item { MonthNav(viewModel) }
        item { LegendRow(viewModel.shiftTypes) }
        if (!viewModel.loading && !viewModel.loadFailed && canEdit && stats.shiftsCount + stats.offCount == 0) {
            item { CalendarEmptyBanner(onQuickFill = { if (!viewModel.quickFillExpanded) viewModel.toggleQuickFillExpanded() }) }
        }
        item { WeekdayHeaderRow() }
        item { CalendarGrid(viewModel, canEdit) }
    }
    }

    val dateKey = viewModel.dayModalDateKey
    if (canEdit && dateKey != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeDayModal, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.shifts_choose), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(dateKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// Uses the shared gradient hero treatment with an income-green progress fill.
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
            Text(maskedAmount(formatMoneyWithCurrency(earned, "UAH")), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            val pct = (earned / SALARY_GOAL).coerceIn(0.0, 1.0)
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(pct.toFloat())
                        .fillMaxSize()
                        .clip(RoundedCornerShape(99.dp))
                        .background(Brush.horizontalGradient(listOf(ua.rytm.app.ui.theme.GreenDark, ua.rytm.app.ui.theme.GreenDark2))),
                )
            }
            Text(
                maskedAmount(stringResource(R.string.shifts_goal_progress, (pct * 100).toInt(), formatMoneyWithCurrency(SALARY_GOAL, "UAH"))),
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(Icons.Filled.Schedule, stats.hours.toInt().toString(), stringResource(R.string.shifts_hours_short), Modifier.weight(1f))
        StatChip(Icons.Filled.EventAvailable, stats.shiftsCount.toString(), stringResource(R.string.shifts_count), Modifier.weight(1f))
        StatChip(Icons.Filled.BeachAccess, stats.offCount.toString(), stringResource(R.string.shifts_days_off), Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(999.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(ua.rytm.app.ui.theme.PurpleDark, ua.rytm.app.ui.theme.Purple3))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun ShiftSelectionRow(type: ShiftType, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
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
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
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
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

// Matches the PWA's #tools-panel-body: collapsed by default (js/calendar.js's
// toggleQuickFill()), a labeled toggle button with a rotating chevron.
@Composable
private fun QuickFillPanel(vm: ShiftsViewModel, onOpenShiftTypes: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        val quickFillState = stringResource(if (vm.quickFillExpanded) R.string.accessibility_expanded else R.string.accessibility_collapsed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RytmDimens.TouchTarget)
                .semantics { stateDescription = quickFillState }
                .clickable(role = Role.Button, onClick = vm::toggleQuickFillExpanded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Text(
                stringResource(R.string.shifts_quick_fill),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            val rotation by androidx.compose.animation.core.animateFloatAsState(
                if (vm.quickFillExpanded) 180f else 0f,
                animationSpec = motionAwareSpec(androidx.compose.animation.core.spring()),
                label = "chevron",
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
        }
        ReducedMotionVisibility(visible = vm.quickFillExpanded) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                androidx.compose.material3.Button(onClick = vm::applyTemplate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_apply))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(onClick = onOpenShiftTypes, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.shift_types_title), modifier = Modifier.padding(start = 6.dp))
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = vm::clearCurrentMonth,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.shifts_clear_month))
                    }
                }

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledDropdown(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Text(
                stringResource(R.string.shifts_empty_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(R.string.shifts_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            androidx.compose.material3.Button(onClick = onQuickFill, modifier = Modifier.padding(top = 14.dp)) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.shifts_quick_fill), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun LegendRow(types: List<ShiftType>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(types) { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(type.colorHex).copy(alpha = 0.25f))
                        .border(1.dp, Color(type.colorHex).copy(alpha = 0.6f), RoundedCornerShape(4.dp)),
                )
                Text(localizedDomainText(type.name), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
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
                color = if (i >= 5) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthNav(viewModel: ShiftsViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::goToPreviousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.action_previous_month)) }
            val locale = LocalConfiguration.current.locales[0]
            val label = viewModel.visibleMonth.month.getDisplayName(TextStyle.FULL, locale) + " " + viewModel.visibleMonth.year
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::goToToday) { Text(stringResource(R.string.action_today)) }
                IconButton(onClick = viewModel::goToNextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.action_next_month)) }
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().height(((cells.size + 6) / 7 * 80).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(cells) { cell ->
            val date = cell.date
            if (date == null) {
                Box(Modifier)
                return@items
            }
            val day = date.dayOfMonth
            val dateKey = date.toString()
            val assigned = viewModel.shiftsFor(dateKey)
            val isToday = dateKey == todayKey
            val locale = if (LocalConfiguration.current.locales[0].language == "uk") java.util.Locale("uk") else java.util.Locale.ENGLISH
            val spokenDate = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
            val accessibilityLabel = if (assigned.isEmpty()) {
                stringResource(R.string.shifts_day_empty_accessibility, spokenDate)
            } else {
                stringResource(R.string.shifts_day_assigned_accessibility, spokenDate, assigned.joinToString { it.name })
            }
            DayCell(day = day, assigned = assigned, isToday = isToday, isWeekend = cell.isWeekend, enabled = canEdit, accessibilityLabel = accessibilityLabel, onClick = { viewModel.openDayModal(dateKey) })
        }
    }
}

@Composable
private fun DayCell(day: Int, assigned: List<ShiftType>, isToday: Boolean, isWeekend: Boolean, enabled: Boolean, accessibilityLabel: String, onClick: () -> Unit) {
    val bg = when {
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        assigned.isNotEmpty() -> MaterialTheme.colorScheme.surfaceContainerHigh
        isWeekend -> MaterialTheme.colorScheme.error.copy(alpha = 0.03f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { contentDescription = accessibilityLabel }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            day.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Black else FontWeight.SemiBold,
            color = if (isWeekend) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            assigned.take(2).forEach { type ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(type.colorHex).copy(alpha = 0.22f))
                        .border(1.dp, Color(type.colorHex).copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(type.code, style = MaterialTheme.typography.labelSmall, color = Color(type.colorHex), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
