package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ua.rytm.app.R
import ua.rytm.app.data.StatementTable
import ua.rytm.app.ui.components.RytmFilterChip
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.screens.finance.formatMoney
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmSpacing
import java.time.LocalDate

/**
 * «Як читати цю виписку?»: the user points at the date, amount and
 * description columns of another bank's table and sees the result live
 * before anything is imported. The choice is remembered per header line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementMappingSheet(
    table: List<List<String>>,
    initial: StatementTable.Mapping,
    onApply: (StatementTable.Mapping) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var headerRow by rememberSaveable { mutableStateOf(initial.headerRow) }
    var date by rememberSaveable { mutableStateOf(initial.date) }
    var split by rememberSaveable { mutableStateOf(initial.amount == null && initial.debit != null) }
    var amount by rememberSaveable { mutableStateOf(initial.amount ?: 0) }
    var debit by rememberSaveable { mutableStateOf(initial.debit ?: 0) }
    var credit by rememberSaveable { mutableStateOf(initial.credit ?: 0) }
    var details by rememberSaveable { mutableStateOf(initial.details ?: -1) }

    val header = table.getOrNull(headerRow).orEmpty()
    val width = table.maxOf { it.size }
    val columnLabel = stringResource(R.string.statement_column_n)
    val columns = (0 until width).map { i -> header.getOrNull(i)?.takeIf { it.isNotBlank() } ?: columnLabel.format(i + 1) }
    val mapping = StatementTable.Mapping(
        headerRow, date,
        amount = if (split) null else amount, debit = if (split) debit else null, credit = if (split) credit else null,
        details = details.takeIf { it >= 0 },
    )
    val result = remember(table, mapping) { StatementTable.apply(table, mapping) }

    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
                RytmSheetTitle(stringResource(R.string.statement_map_title))
                Text(stringResource(R.string.statement_map_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val headerOptions = table.take(15).mapIndexed { i, row -> i to row.filter { it.isNotBlank() }.joinToString(" · ").take(60) }
                Picker(stringResource(R.string.statement_map_header_row), headerOptions, headerRow) { headerRow = it }
                Picker(stringResource(R.string.statement_map_date), columns.withIndex().map { it.index to it.value }, date) { date = it }
                Row(horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                    RytmFilterChip(selected = !split, onClick = { split = false }, label = { Text(stringResource(R.string.statement_map_one_amount)) })
                    RytmFilterChip(selected = split, onClick = { split = true }, label = { Text(stringResource(R.string.statement_map_two_amounts)) })
                }
                if (split) {
                    Picker(stringResource(R.string.statement_map_debit), columns.withIndex().map { it.index to it.value }, debit) { debit = it }
                    Picker(stringResource(R.string.statement_map_credit), columns.withIndex().map { it.index to it.value }, credit) { credit = it }
                } else {
                    Picker(stringResource(R.string.statement_map_amount), columns.withIndex().map { it.index to it.value }, amount) { amount = it }
                    Text(stringResource(R.string.statement_map_amount_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Picker(
                    stringResource(R.string.statement_map_details),
                    listOf(-1 to stringResource(R.string.statement_map_none)) + columns.withIndex().map { it.index to it.value },
                    details,
                ) { details = it }

                Text(stringResource(R.string.statement_map_preview), style = MaterialTheme.typography.titleSmall)
                result.statement.rows.take(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                        Text(LocalDate.parse(row.date).format(StatementTable.PREVIEW_DATE), style = MaterialTheme.typography.bodySmall)
                        Text(row.details, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            maskedAmount((if (row.amount > 0) "+" else "−") + formatMoney(kotlin.math.abs(row.amount))),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            color = if (row.amount > 0) RytmSemantic.income else RytmSemantic.expense,
                        )
                    }
                }
                Text(
                    pluralStringResource(R.plurals.statement_map_found, result.statement.rows.size, result.statement.rows.size) +
                        if (result.skipped > 0) " · " + pluralStringResource(R.plurals.statement_map_skipped, result.skipped, result.skipped) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = RytmSpacing.md), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget), shape = RoundedCornerShape(RytmRadii.Row)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { onApply(mapping) },
                    enabled = result.statement.rows.isNotEmpty(),
                    modifier = Modifier.weight(1f).heightIn(min = RytmDimens.TouchTarget),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.action_next)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Picker(label: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
