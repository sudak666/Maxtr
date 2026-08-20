package ua.rytm.app.ui.screens.shifts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.screens.finance.formatMoney
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Implements SHIFTS_SCREEN_SPEC.md's in-scope subset: hero metric, chip
// stats, legend, month grid, day-assignment sheet. Quick-fill/autofill/
// income chart are deliberately deferred (see the spec's "not in this step").
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftsScreen(
    viewModel: ShiftsViewModel = viewModel(
        factory = ShiftsViewModel.factory((LocalContext.current.applicationContext as RytmApplication).shiftsRepository),
    ),
) {
    val stats = viewModel.monthStats
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroMetric(stats.earned) }
        item { ChipStats(stats) }
        item { LegendRow(viewModel.shiftTypes) }
        item { MonthNav(viewModel) }
        item { CalendarGrid(viewModel) }
    }

    val dateKey = viewModel.dayModalDateKey
    if (dateKey != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeDayModal, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Оберіть зміни", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(dateKey, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                viewModel.shiftTypes.forEach { type ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = type.id in viewModel.dayModalSelection, onCheckedChange = { viewModel.toggleDayModalType(type.id) })
                        Text(type.name, modifier = Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = viewModel::closeDayModal) { Text("Скасувати") }
                    TextButton(onClick = viewModel::saveDayModal) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(earned: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Зароблено цього місяця", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${formatMoney(earned)} грн", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            val pct = (earned / SALARY_GOAL * 100).coerceIn(0.0, 100.0)
            LinearProgressIndicator(progress = { (pct / 100).toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            Text("${pct.toInt()}% від цілі ${formatMoney(SALARY_GOAL)} грн", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ChipStats(stats: ShiftsViewModel.MonthStats) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip(stats.hours.toInt().toString(), "год", Modifier.weight(1f))
        StatChip(stats.shiftsCount.toString(), "Змін", Modifier.weight(1f))
        StatChip(stats.offCount.toString(), "Вихідних", Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendRow(types: List<ShiftType>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(types) { type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.height(8.dp).aspectRatio(1f).clip(CircleShape).background(Color(type.colorHex)))
                Text(type.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun MonthNav(viewModel: ShiftsViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = viewModel::goToPreviousMonth) { Icon(Icons.Filled.ChevronLeft, contentDescription = null) }
        val label = viewModel.visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.Builder().setLanguage("uk").build()) + " " + viewModel.visibleMonth.year
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row {
            TextButton(onClick = viewModel::goToToday) { Text("Сьогодні") }
            IconButton(onClick = viewModel::goToNextMonth) { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
        }
    }
}

@Composable
private fun CalendarGrid(viewModel: ShiftsViewModel) {
    val month = viewModel.visibleMonth
    val firstDayOffset = (month.atDay(1).dayOfWeek.value - 1) // Monday=1 -> 0
    val daysInMonth = month.lengthOfMonth()
    val todayKey = viewModel.today.toString()

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().height(((daysInMonth + firstDayOffset + 6) / 7 * 64).dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(firstDayOffset) { Box(Modifier) }
        items(daysInMonth) { index ->
            val day = index + 1
            val dateKey = "%04d-%02d-%02d".format(month.year, month.monthValue, day)
            val assigned = viewModel.shiftsFor(dateKey)
            val isToday = dateKey == todayKey
            DayCell(day = day, assigned = assigned, isToday = isToday, onClick = { viewModel.openDayModal(dateKey) })
        }
    }
}

@Composable
private fun DayCell(day: Int, assigned: List<ShiftType>, isToday: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Text(day.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        assigned.take(2).forEach { type ->
            Text(
                type.code,
                style = MaterialTheme.typography.labelSmall,
                color = Color(type.colorHex),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
