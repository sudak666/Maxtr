package ua.zminka.app.ui.shifts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.zminka.app.data.model.ShiftType
import java.util.Calendar
import java.util.Locale

@Composable
fun ShiftsScreen(viewModel: ShiftsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthNav(
            year = state.year,
            month = state.month,
            onPrev = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
        )
        HeroEarned(earned = state.earned)
        StatChipRow(hours = state.hours, shiftsCount = state.shiftsCount, daysOff = state.daysOffCount)
        CalendarGrid(
            year = state.year,
            month = state.month,
            shiftTypes = state.shiftTypes,
            dayShiftTypeIds = state.dayShiftTypeIds,
            onDayClick = { day -> selectedDay = day },
        )
    }

    val day = selectedDay
    if (day != null) {
        DayShiftPickerDialog(
            day = day,
            shiftTypes = state.shiftTypes,
            assignedIds = state.dayShiftTypeIds[day] ?: emptyList(),
            onToggle = { id -> viewModel.toggleShiftType(day, id) },
            onDismiss = { selectedDay = null },
        )
    }
}

@Composable
private fun MonthNav(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val label = remember(year, month) {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        java.text.SimpleDateFormat("LLLL yyyy", Locale("uk")).format(cal.time)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, contentDescription = "Попередній місяць") }
        Text(label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, contentDescription = "Наступний місяць") }
    }
}

@Composable
private fun HeroEarned(earned: Double) {
    // Mirrors .hero-metric — one primary number (earned this month), same
    // pattern as FinanceScreen's balance hero (CLAUDE.md's Mobile UI
    // redesign section).
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            "Зароблено цього місяця",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            "${String.format(Locale.US, "%,.0f", earned)} ₴",
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}

@Composable
private fun StatChipRow(hours: Double, shiftsCount: Int, daysOff: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(onClick = {}, label = { Text("${hours.toInt()} год") })
        SuggestionChip(onClick = {}, label = { Text("$shiftsCount змін") })
        SuggestionChip(onClick = {}, label = { Text("$daysOff вихідних") })
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    shiftTypes: List<ShiftType>,
    dayShiftTypeIds: Map<Int, List<String>>,
    onDayClick: (Int) -> Unit,
) {
    val cal = remember(year, month) { Calendar.getInstance().apply { set(year, month, 1) } }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Calendar.DAY_OF_WEEK: Sunday=1..Saturday=7 — shift to a Monday-first index (0..6).
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val today = remember { Calendar.getInstance() }
    val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(firstDow) { Box(Modifier.aspectRatio(1f)) }
        items(daysInMonth) { index ->
            val day = index + 1
            val ids = dayShiftTypeIds[day] ?: emptyList()
            val firstType = ids.firstNotNullOfOrNull { id -> shiftTypes.find { it.id == id } }
            val isToday = isCurrentMonth && today.get(Calendar.DAY_OF_MONTH) == day
            DayCell(day = day, tintColor = firstType?.let { parseColor(it.color) }, isToday = isToday, onClick = { onDayClick(day) })
        }
    }
}

@Composable
private fun DayCell(day: Int, tintColor: Color?, isToday: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tintColor?.copy(alpha = 0.18f) ?: Color.Transparent)
            .then(
                if (isToday) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(day.toString(), color = tintColor ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DayShiftPickerDialog(
    day: Int,
    shiftTypes: List<ShiftType>,
    assignedIds: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("День $day") },
        text = {
            Column {
                if (shiftTypes.isEmpty()) {
                    Text("Спершу додайте типи змін у веб-версії або PWA.")
                }
                shiftTypes.forEach { type ->
                    val selected = type.id in assignedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(type.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(parseColor(type.color)),
                        )
                        Text(type.name, modifier = Modifier.padding(end = 8.dp))
                        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
    )
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: IllegalArgumentException) {
    Color(0xFF8B5CF6)
}
