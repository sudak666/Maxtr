package ua.rytm.app.ui.screens.finance

import ua.rytm.app.ui.theme.RytmSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.ChevronLeft
import androidx.compose.foundation.layout.Box
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
import ua.rytm.app.ui.icons.Share
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
// No-break space: "−1 190,93" and "₴" were split across lines (P5).
private fun uah(amount: Double): String = maskedAmount("${formatMoney(amount)} ₴")

/** Shown on Finance in the first week of a month until opened or closed. */
@Composable
fun MonthSummaryCard(summary: MonthSummary, onOpen: () -> Unit, onClose: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(start = RytmSpacing.lg, top = RytmSpacing.sm, bottom = RytmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(RytmIcons.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(RytmSpacing.md))
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
fun MonthSummarySheet(
    summary: MonthSummary,
    onDismiss: () -> Unit,
    onSharePdf: (() -> Unit)? = null,
    onPrevMonth: (() -> Unit)? = null,
    onNextMonth: (() -> Unit)? = null,
) {
    RytmModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = RytmSpacing.xl, vertical = RytmSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { RytmSheetTitle(stringResource(R.string.month_summary_title, monthName(summary.month))) }
                if (onPrevMonth != null) {
                    androidx.compose.material3.IconButton(onClick = onPrevMonth) { Icon(RytmIcons.ChevronLeft, contentDescription = stringResource(R.string.month_summary_prev)) }
                    androidx.compose.material3.IconButton(onClick = { onNextMonth?.invoke() }, enabled = onNextMonth != null) { Icon(RytmIcons.ChevronRight, contentDescription = stringResource(R.string.month_summary_next)) }
                }
            }
            if (summary.isEmpty) {
                Text(stringResource(R.string.month_summary_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            MonthStories(summary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.analytics_income_label), uah(summary.income), RytmSemantic.income, Modifier.weight(1f))
                StatTile(stringResource(R.string.analytics_expense_label), uah(summary.expense), RytmSemantic.expense, Modifier.weight(1f))
            }
            val net = summary.net
            Text(
                stringResource(if (net >= 0) R.string.month_summary_saved else R.string.month_summary_overspent, uah(kotlin.math.abs(net))),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (net >= 0) RytmSemantic.income else RytmSemantic.expense,
            )
            // The month-over-month change lives on the first story card now.
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
            if (!summary.isEmpty && onSharePdf != null) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onSharePdf,
                    modifier = Modifier.fillMaxWidth().heightIn(min = RytmDimens.TouchTarget),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) {
                    Icon(RytmIcons.Share, contentDescription = null)
                    Spacer(Modifier.width(RytmSpacing.sm))
                    Text(stringResource(R.string.month_report_share))
                }
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

/**
 * The month as 3–4 swipeable "story" cards, one big fact each (Spotify
 * Wrapped / Revolut style), above the detailed figures. Expressive on purpose:
 * the brand gradient is used here and nowhere else in the app.
 */
@Composable
private fun MonthStories(summary: MonthSummary) {
    data class Story(val label: String, val value: String, val caption: String?)
    // "за вересень": Ukrainian months are lowercase mid-sentence; English keeps the capital.
    val month = monthName(summary.month).let { if (LocalConfiguration.current.locales[0].language == "en") it else it.lowercase() }
    val stories = buildList {
        add(Story(
            stringResource(R.string.story_spent, month), uah(summary.expense),
            summary.expenseChangePercent?.let { pct -> stringResource(if (pct >= 0) R.string.month_summary_spent_more else R.string.month_summary_spent_less, kotlin.math.abs(pct)) },
        ))
        summary.topCategories.firstOrNull()?.let { c ->
            val share = if (summary.expense > 0) Math.round(c.amount / summary.expense * 100).toInt() else 0
            add(Story(stringResource(R.string.story_top_category), localizedDomainText(c.category), stringResource(R.string.story_top_share, uah(c.amount), share)))
        }
        summary.biggestExpense?.let { t ->
            add(Story(
                stringResource(R.string.story_biggest), maskedAmount("${formatMoney(t.amount)} ${currencySymbol(t.currency)}"),
                t.comment?.takeIf { it.isNotBlank() } ?: localizedDomainText(t.category),
            ))
        }
        val net = summary.net
        add(Story(
            stringResource(if (net >= 0) R.string.story_saved else R.string.story_overspent), uah(kotlin.math.abs(net)),
            stringResource(R.string.story_count, summary.transactionCount),
        ))
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState { stories.size }
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager, pageSpacing = RytmSpacing.sm) { page ->
            val st = stories[page]
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, ua.rytm.app.ui.theme.Purple3)),
                        RoundedCornerShape(RytmRadii.Card),
                    )
                    .padding(RytmSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm, Alignment.CenterVertically),
            ) {
                Text(st.label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
                Text(st.value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                st.caption?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(stories.size) { i ->
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (i == pager.currentPage) 8.dp else 6.dp)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            androidx.compose.foundation.shape.CircleShape,
                        ),
                )
            }
        }
    }
}
