package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ua.rytm.app.R
import ua.rytm.app.ui.components.RytmModalBottomSheet
import ua.rytm.app.ui.components.RytmSheetTitle
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmSemantic
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
private fun monthName(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    return month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
private fun uah(amount: Double): String = maskedAmount("${formatMoney(amount)} ₴")

/** Shown on Finance in the first week of a month until opened or closed. */
@Composable
fun MonthSummaryCard(summary: MonthSummary, onOpen: () -> Unit, onClose: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(RytmIcons.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.month_summary_card_title, monthName(summary.month)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(stringResource(R.string.month_summary_card_body, uah(summary.expense)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(RytmDimens.TouchTarget)) {
                Icon(RytmIcons.Clear, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSummarySheet(summary: MonthSummary, onDismiss: () -> Unit) {
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RytmSheetTitle(stringResource(R.string.month_summary_title, monthName(summary.month)))
            if (summary.isEmpty) {
                Text(stringResource(R.string.month_summary_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.tx_income), uah(summary.income), RytmSemantic.income, Modifier.weight(1f))
                StatTile(stringResource(R.string.tx_expense), uah(summary.expense), RytmSemantic.expense, Modifier.weight(1f))
            }
            val net = summary.net
            Text(
                stringResource(if (net >= 0) R.string.month_summary_saved else R.string.month_summary_overspent, uah(kotlin.math.abs(net))),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (net >= 0) RytmSemantic.income else RytmSemantic.expense,
            )
            summary.expenseChangePercent?.let { pct ->
                Text(
                    stringResource(if (pct >= 0) R.string.month_summary_spent_more else R.string.month_summary_spent_less, kotlin.math.abs(pct)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.topCategories.isNotEmpty()) {
                Text(stringResource(R.string.month_summary_top), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                summary.topCategories.forEach { c ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(localizedDomainText(c.category), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(uah(c.amount), fontWeight = FontWeight.SemiBold)
                            if (c.previousAmount > 0) {
                                val diff = Math.round((c.amount - c.previousAmount) / c.previousAmount * 100).toInt()
                                Text(
                                    stringResource(R.string.month_summary_vs_previous, (if (diff >= 0) "+" else "−") + kotlin.math.abs(diff)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            summary.biggestExpense?.let { t ->
                Text(
                    stringResource(R.string.month_summary_biggest, t.comment?.takeIf { it.isNotBlank() } ?: localizedDomainText(t.category), maskedAmount("${formatMoney(t.amount)} ${currencySymbol(t.currency)}")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(RytmRadii.Row), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}
