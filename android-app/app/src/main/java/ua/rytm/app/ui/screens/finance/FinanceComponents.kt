package ua.rytm.app.ui.screens.finance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.onFocusChanged
import ua.rytm.app.ui.LocalReducedMotion
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import ua.rytm.app.ui.components.SwipeOpenThreshold
import ua.rytm.app.ui.components.SwipeRevealWidth
import ua.rytm.app.ui.components.SwipeReleaseAction
import ua.rytm.app.ui.components.swipeReleaseAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.ui.maskedAmount
import ua.rytm.app.ui.localizedDomainText
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.ui.components.RytmEmptyState
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmInteraction
import ua.rytm.app.ui.motionAwareSpec
import ua.rytm.app.ui.RealtimeStateBanner
import ua.rytm.app.ui.ScreenLoadErrorState
import ua.rytm.app.ui.ScreenLoadingState
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.Event
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Build
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.theme.tabularNums

// Building blocks of FinanceScreen, split out so the screen file holds only the screen itself.

@Composable
internal fun HeroBalanceCard(vm: FinanceViewModel) {
    // Matches the PWA's .hero-balance: a subtle bg1→bg2 diagonal gradient
    // plus a soft brand-purple glow shadow (--surface-hero/--shadow-raised),
    // not a flat Card — see ANDROID_MIGRATION.md visual-parity note.
    val shape = MaterialTheme.shapes.large
    val largeText = LocalDensity.current.fontScale >= 1.2f
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
        Column(Modifier.padding(horizontal = RytmDimens.HeroHorizontal, vertical = RytmDimens.HeroVertical)) {
            Text(
                text = stringResource(if (vm.isMultiCurrency) R.string.finance_estimated_balance else R.string.finance_total_balance),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = maskedAmount(stringResource(if (vm.isMultiCurrency) R.string.finance_amount_uah_estimated else R.string.finance_amount_uah, formatMoney(vm.totalBalanceUah))),
                // Tabular figures: proportional digits make the balance jitter
                // horizontally as it changes.
                style = MaterialTheme.typography.displayMedium.tabularNums(),
                fontWeight = FontWeight.Black,
            )

            val net = vm.monthIncomeUah - vm.monthExpenseUah
            val trendColor = RytmSemantic.signed(net)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Icon(
                    imageVector = if (net < 0) RytmIcons.TrendingDown else RytmIcons.TrendingUp,
                    contentDescription = null,
                    tint = trendColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                val sign = if (net > 0) "+" else if (net < 0) "−" else ""
                Text(
                    text = maskedAmount(stringResource(R.string.finance_month_net, sign, formatMoney(kotlin.math.abs(net)))),
                    style = MaterialTheme.typography.bodySmall,
                    color = trendColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            val outlook = vm.monthOutlook
            if (!outlook.isEmpty) {
                val parts = buildList {
                    if (outlook.shiftsEarnings > 0) add(stringResource(R.string.finance_outlook_shifts, formatMoney(outlook.shiftsEarnings)))
                    if (outlook.recurringIn > 0) add(stringResource(R.string.finance_outlook_recurring_in, formatMoney(outlook.recurringIn)))
                    if (outlook.recurringOut > 0) add(stringResource(R.string.finance_outlook_recurring_out, formatMoney(outlook.recurringOut)))
                }
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(RytmIcons.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.finance_outlook_prefix) + " " + maskedAmount(parts.joinToString(" · ")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val contributions = vm.monthContributions
            if (contributions.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.finance_contributions_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    contributions.forEach { c ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (c.isMe) stringResource(R.string.finance_contributions_me, c.name) else c.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                maskedAmount("+${formatMoney(c.income)} · −${formatMoney(c.expense)} ₴"),
                                style = MaterialTheme.typography.bodySmall.tabularNums(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (vm.isMultiCurrency) {
                Text(
                    text = stringResource(R.string.finance_conversion_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (largeText) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatCard(label = stringResource(R.string.finance_month_income), value = vm.monthIncomeUah, positive = true, modifier = Modifier.fillMaxWidth())
                    MiniStatCard(label = stringResource(R.string.finance_month_expense), value = vm.monthExpenseUah, positive = false, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStatCard(label = stringResource(R.string.finance_month_income), value = vm.monthIncomeUah, positive = true, modifier = Modifier.weight(1f))
                    MiniStatCard(label = stringResource(R.string.finance_month_expense), value = vm.monthExpenseUah, positive = false, modifier = Modifier.weight(1f))
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.wallets, key = { it.id }) { wallet ->
                    WalletChip(wallet = wallet, balance = vm.walletBalance(wallet.id))
                }
            }
        }
    }
}

@Composable
internal fun MiniStatCard(label: String, value: Double, positive: Boolean, modifier: Modifier = Modifier) {
    // Matches the PWA's .fin-mini-stat.income/.expense: a tinted
    // green/red gradient wash + matching border, not a neutral surface —
    // see ANDROID_MIGRATION.md visual-parity note.
    // Wash tint and text tone are separate: the light-theme wash needs the
    // brighter green/red to read as a tint at all, while the value on top of
    // it needs the deeper tone to clear 4.5:1 against that wash.
    val tint = if (positive) RytmSemantic.incomeWash else RytmSemantic.expenseWash
    val valueColor = if (positive) RytmSemantic.income else RytmSemantic.expense
    val shape = RoundedCornerShape(RytmRadii.Input)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.03f))))
            .border(1.dp, tint.copy(alpha = 0.28f), shape)
            .padding(12.dp),
    ) {
        Column {
            // Same icon-circle + label treatment as ToolsSheet's
            // AnalyticsTotalCard (income/expense-this-month, the same
            // underlying figures) — this card used to render label+value
            // only, reading as a different component instead of a compact
            // variant of the same one; PWA's equivalent (.fin-mini-stat-icon)
            // already got this fix, this one hadn't yet.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(22.dp).background(valueColor.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (positive) RytmIcons.TrendingUp else RytmIcons.TrendingDown, contentDescription = null, tint = valueColor, modifier = Modifier.size(13.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = maskedAmount(stringResource(R.string.finance_signed_uah, if (value == 0.0) "" else if (positive) "+" else "−", formatMoney(value))),
                style = MaterialTheme.typography.titleMedium.tabularNums(),
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun WalletChip(wallet: Wallet, balance: Double) {
    Card(shape = RoundedCornerShape(RytmRadii.Pill), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Solid color dot for the wallet, matching .wallet-chip-dot.
            Box(Modifier.size(8.dp).background(Color(wallet.colorHex), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(localizedDomainText(wallet.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                maskedAmount("${formatMoney(balance)} ${currencySymbol(wallet.currency)}"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun QuickActionsRow(canEdit: Boolean, onTools: () -> Unit, onBudgets: () -> Unit, onGoals: () -> Unit) {
    data class QuickAction(val label: String, val icon: ImageVector, val primary: Boolean, val onClick: () -> Unit)

    // "+ Операція" is not repeated here: the FAB (and the empty state's CTA)
    // already own that action on this screen.
    val actions = listOf(
        QuickAction(stringResource(R.string.tools_title), RytmIcons.Build, primary = false, onClick = onTools),
        QuickAction(stringResource(R.string.budgets_title), RytmIcons.PieChart, primary = false, onClick = onBudgets),
        QuickAction(stringResource(R.string.goals_title), RytmIcons.Flag, primary = false, onClick = onGoals),
    ).filterIndexed { index, _ -> canEdit || index == 0 }
    val largeText = LocalDensity.current.fontScale >= 1.2f
    val columnCount = if (largeText) 1 else 3

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(columnCount).forEach { rowActions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowActions.forEach { action ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val pressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) RytmInteraction.ButtonPressedScale else 1f,
                        animationSpec = motionAwareSpec(tween(100)),
                        label = "quick-action-press",
                    )
                    Card(
                        onClick = action.onClick,
                        modifier = Modifier.weight(1f).height(RytmDimens.QuickActionMinHeight).graphicsLayer { scaleX = scale; scaleY = scale },
                        shape = RoundedCornerShape(RytmRadii.Row),
                        // A hairline border here (fixed to onSurfaceVariant
                        // for contrast, then reported live as reading too
                        // harsh/stark next to the rest of the screen) was
                        // fighting the wrong problem: this app's established
                        // way to separate a card from the page isn't a
                        // stroke at all -- it's a soft tonal fill, the same
                        // surfaceContainerLow DebtScreen's InfoPanel already
                        // uses with no border whatsoever. Matches here too,
                        // and sidesteps the outline-contrast question
                        // entirely by not drawing an outline.
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        interactionSource = interactionSource,
                    ) {
                        // Three across: icon over label, so "Інструменти" never truncates
                        // at 360dp; one per row at large font scale keeps icon + label inline.
                        val tile: @Composable () -> Unit = {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = if (action.primary) RytmSemantic.income else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(RytmDimens.QuickActionIcon),
                            )
                            Text(
                                action.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (columnCount == 1) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) { tile() }
                        } else {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) { tile() }
                        }
                    }
                }
                repeat(columnCount - rowActions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun HistoryHeader(vm: FinanceViewModel, resultCount: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.finance_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(pluralStringResource(R.plurals.finance_records, resultCount, resultCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SearchField(vm: FinanceViewModel) {
    ua.rytm.app.ui.components.RytmSearchField(
        value = vm.search,
        onValueChange = vm::onSearchChange,
        prefix = stringResource(R.string.finance_search_prefix),
        hints = listOf(
            stringResource(R.string.finance_search_what_comment),
            stringResource(R.string.finance_search_what_category),
            stringResource(R.string.finance_search_what_tag),
            stringResource(R.string.finance_search_what_wallet),
        ),
        clearDescription = stringResource(R.string.finance_clear_search),
    )
}

@Composable
internal fun FilterRow(vm: FinanceViewModel) {
    val types = listOf(
        Triple(TxTypeFilter.ALL, stringResource(R.string.filter_all), null),
        Triple(TxTypeFilter.INCOME, stringResource(R.string.tx_income), RytmIcons.TrendingUp),
        Triple(TxTypeFilter.EXPENSE, stringResource(R.string.tx_expense), RytmIcons.TrendingDown),
        Triple(TxTypeFilter.TRANSFER, stringResource(R.string.tx_transfer), RytmIcons.SwapHoriz),
    )
    val periods = listOf(
        PeriodFilter.DAY to stringResource(R.string.action_today),
        PeriodFilter.MONTH to stringResource(R.string.period_month),
        PeriodFilter.ALL to stringResource(R.string.period_all),
    )
    // One scrollable row (type, divider, period) instead of two stacked rows
    // — ~56dp more of the actual history above the fold.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        items(types, key = { "t-${it.first}" }) { (value, label, icon) ->
            FilterChip(
                selected = vm.typeFilter == value,
                onClick = { vm.onTypeFilterChange(value) },
                label = { Text(label) },
                leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
            )
        }
        item(key = "divider") {
            Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        items(periods, key = { "p-${it.first}" }) { (value, label) ->
            FilterChip(
                selected = vm.periodFilter == value,
                onClick = { vm.onPeriodFilterChange(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun DayHeader(date: String, netUah: Double) {
    val locale = LocalConfiguration.current.locales[0]
    val parsed = remember(date) { runCatching { java.time.LocalDate.parse(date) }.getOrNull() }
    val today = java.time.LocalDate.now()
    val label = when {
        parsed == null -> date
        parsed == today -> stringResource(R.string.action_today)
        parsed == today.minusDays(1) -> stringResource(R.string.finance_day_yesterday)
        parsed.year == today.year -> parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        else -> parsed.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
    }.replaceFirstChar { it.titlecase(locale) }
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (netUah != 0.0) {
            val sign = if (netUah > 0) "+" else "−"
            Text(
                maskedAmount(stringResource(R.string.finance_signed_uah, sign, formatMoney(kotlin.math.abs(netUah)))),
                style = MaterialTheme.typography.labelLarge.tabularNums(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CategoryFilterChip(category: String, onClear: () -> Unit) {
    FilterChip(
        selected = true,
        onClick = onClear,
        label = { Text(stringResource(R.string.finance_category_clear, localizedDomainText(category))) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
internal fun EmptyState(isSearching: Boolean, canEdit: Boolean, onAddFirst: () -> Unit) {
    // The no-transactions state had no call to action at all, while the FAB
    // that would have been the obvious next tap can itself be hidden (large
    // font / compact height / scrolled far down the list).
    RytmEmptyState(
        icon = if (isSearching) RytmIcons.Search else RytmIcons.AccountBalanceWallet,
        title = stringResource(if (isSearching) R.string.finance_empty_search_title else R.string.finance_empty_title),
        body = stringResource(if (isSearching) R.string.finance_empty_search_body else R.string.finance_empty_body),
        actionLabel = if (!isSearching && canEdit) stringResource(R.string.finance_empty_cta) else null,
        onAction = if (!isSearching && canEdit) onAddFirst else null,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionRow(
    tx: Transaction,
    walletName: (String?) -> String?,
    tagLookup: (String) -> Tag?,
    iconOverride: String?,
    canEdit: Boolean,
    resetGeneration: Int,
    onDelete: () -> Boolean,
    onClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val swipeThresholdPx = with(density) { SwipeOpenThreshold.toPx() }
    val revealWidthPx = with(density) { SwipeRevealWidth.toPx() }
    var rowWidthPx by remember(tx.id) { mutableIntStateOf(0) }
    var offsetPx by remember(tx.id) { mutableFloatStateOf(0f) }
    // Fires once per crossing (not every drag frame) when the swipe passes
    // the point where releasing now would delete the row -- the same
    // "point of no return" tick competitor apps (Gmail, Mail) give on a
    // full swipe-to-delete gesture.
    var pastDeleteThreshold by remember(tx.id) { mutableStateOf(false) }

    suspend fun settleAt(target: Float) {
        animate(offsetPx, target, animationSpec = tween(180)) { value, _ -> offsetPx = value }
    }

    LaunchedEffect(resetGeneration) {
        settleAt(0f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            // Clip + the red reveal layer below only while the row is actually
            // swiped: drawing a full-size red layer (plus a rounded clip) under
            // EVERY row was pure GPU overdraw — the expanded 443-row history
            // was RenderThread-bound on a Galaxy A51 (Perfetto, live).
            .then(if (offsetPx < 0f) Modifier.clip(MaterialTheme.shapes.large) else Modifier)
            .onSizeChanged { rowWidthPx = it.width },
    ) {
        // The covering Card below is sized from rowWidthPx, which starts at
        // 0 until the Box above reports its real width via onSizeChanged —
        // for that one frame the Card is 0-wide and this full-red
        // delete-reveal layer underneath is fully exposed. Invisible for a
        // single freshly-composed row, but "View all" composes dozens at
        // once, turning that one frame into a visible red flash across the
        // whole list (reported live). Not drawing this layer until the row
        // has a real measured width closes the gap.
        if (rowWidthPx > 0 && offsetPx < 0f) {
            // fillMaxSize() here (not matchParentSize()) sizes against this
            // Box's OWN incoming constraints, not against the Card sibling's
            // actual measured height — and a LazyColumn item's incoming
            // height constraint is unbounded, so fillMaxSize() silently
            // falls back to wrap-content for that axis (Compose's own
            // documented behavior for fillMaxSize under an unbounded
            // constraint). This layer, and the trash-icon box inside it,
            // both collapsed down to roughly their own icon's intrinsic
            // height instead of the Card's real height -- reported live,
            // screenshot: red only covering the row's top third while the
            // card's real content (date/amount/tag) showed through
            // unclipped underneath. matchParentSize() defers measurement of
            // this child until the Box's own size is resolved from its
            // properly-sized sibling (the Card) and always matches that
            // exactly, regardless of what the parent's own constraints are.
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(SwipeRevealWidth)
                        .fillMaxHeight()
                        .clickable(enabled = canEdit && offsetPx <= -revealWidthPx / 2, role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (onDelete()) scope.launch { settleAt(-rowWidthPx.toFloat()) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        RytmIcons.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        // Was `.fillMaxWidth().offset { IntOffset(offsetPx, 0) }` — a
        // horizontal translate, the exact anti-pattern the PWA's own swipe
        // rows explicitly avoid (see js/analytics-csv.js's setupTxSwipe()
        // doc comment: "shrinks its own width... never transform:translateX
        // — clips left-edge content"). Translating a rounded-corner Card
        // left exposes ITS OWN rounded corner mid-row once it's no longer
        // flush with the row's right edge — reported live via screenshot as
        // a stray diagonal/rounded cut where the trash icon reveals. Shrink
        // the card's actual width instead (anchored at the row's start), so
        // the reveal edge is a straight vertical line matching the shrunk
        // box's own corner, not a rounded corner floating mid-row.
        val cardWidthDp = with(density) { (rowWidthPx + offsetPx).coerceAtLeast(0f).toDp() }
        // Was an M3 Card, which always clips its content to the rounded
        // shape. A/B on the A51 (expanded 449-row history, alternating
        // order, 4 runs each): GPU p50 12-13ms -> 8-10ms, janky frames
        // avg 33% -> 13%, every B run better than every A run. A shaped
        // background draws the same card without a clip; the clip is only
        // applied while pressed so the ripple keeps its rounded corners.
        // fillMaxWidth() while not swiped also skips the width derived from
        // onSizeChanged (a second layout pass for every newly composed row).
        val shape = MaterialTheme.shapes.large
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .then(if (offsetPx < 0f) Modifier.width(cardWidthDp) else Modifier.fillMaxWidth())
                .then(if (pressed) Modifier.clip(shape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape)
                .clickable(
                    enabled = canEdit,
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    role = Role.Button,
                ) {
                    if (offsetPx < -1f) scope.launch { settleAt(0f) } else onClick()
                }
                .draggable(
                    enabled = canEdit,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetPx = (offsetPx + delta).coerceIn(-rowWidthPx.toFloat(), 0f)
                        val nowPast = rowWidthPx > 0 && offsetPx <= -rowWidthPx * 0.5f
                        if (nowPast != pastDeleteThreshold) {
                            pastDeleteThreshold = nowPast
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragStopped = { velocity ->
                        when (swipeReleaseAction(offsetPx, rowWidthPx.toFloat(), swipeThresholdPx, velocity)) {
                            SwipeReleaseAction.Delete -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (onDelete()) settleAt(-rowWidthPx.toFloat()) else settleAt(0f)
                            }
                            SwipeReleaseAction.Reveal -> settleAt(-revealWidthPx)
                            SwipeReleaseAction.Settle -> settleAt(0f)
                        }
                        pastDeleteThreshold = false
                    },
                ),
        ) {
            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryIconBadge(tx.category, iconOverride = iconOverride)
                Spacer(Modifier.width(12.dp))
                // Every Text below is single-line + ellipsized on purpose:
                // this Column's width follows the Card's own width, which
                // shrinks live while the row is being swiped (cardWidthDp
                // above). Without a maxLines cap, a long comment or a
                // compound Ukrainian word with no spaces (e.g. "Підробіток")
                // wraps letter-by-letter as the column narrows mid-swipe,
                // exploding the row's height (reported live, screenshot).
                Column(Modifier.weight(1f)) {
                    val categoryLabel = localizedDomainText(tx.category)
                    val walletLabel = walletName(tx.walletId)?.let { localizedDomainText(it) }
                    val targetWalletLabel = walletName(tx.targetWalletId)?.let { localizedDomainText(it) }
                    val catLine = buildString {
                        append(categoryLabel)
                        tx.subcategory?.let { append(" · $it") }
                        walletLabel?.let { append(" · $it") }
                        if (tx.type == TxType.TRANSFER) {
                            targetWalletLabel?.let { append(" → $it") }
                        }
                    }
                    Text(catLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The date lives in the day header above.
                    tx.comment?.takeIf { it.isNotBlank() }?.let { comment ->
                        Text(comment, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val rowTags = tx.tags.mapNotNull(tagLookup)
                    if (rowTags.isNotEmpty()) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowTags.forEach { tag ->
                                val color = Color(tag.colorHex)
                                Box(
                                    Modifier
                                        .background(color.copy(alpha = 0.12f), MaterialTheme.shapes.small)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(tag.name, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val (amountText, amountColor) = when (tx.type) {
                    TxType.INCOME -> maskedAmount("+${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to RytmSemantic.income
                    TxType.EXPENSE -> maskedAmount("−${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to RytmSemantic.expense
                    TxType.TRANSFER -> maskedAmount("${formatMoney(tx.amount)} ${currencySymbol(tx.currency)}") to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(amountText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = amountColor)
            }
        }
    }
}
