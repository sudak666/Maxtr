package ua.rytm.app.ui.screens.finance
import ua.rytm.app.ui.theme.RytmSpacing
import ua.rytm.app.ui.components.fadingEdges
import androidx.compose.foundation.background
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import ua.rytm.app.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest
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
import ua.rytm.app.ui.icons.Close
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.WarningAmber
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Add
import ua.rytm.app.ui.icons.Build
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.CurrencyExchange
import ua.rytm.app.ui.icons.Delete
import ua.rytm.app.ui.icons.ExpandLess
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.Payments
import ua.rytm.app.ui.icons.AccountBalance
import ua.rytm.app.ui.icons.CreditCard
import ua.rytm.app.ui.theme.tabularNums

// Building blocks of FinanceScreen, split out so the screen file holds only the screen itself.

@Composable
internal fun HeroBalanceCard(
    vm: FinanceViewModel,
    onOpenCapital: () -> Unit,
    onOpenForecast: () -> Unit,
    onWalletClick: (String) -> Unit = {},
    onWalletLongClick: ((String) -> Unit)? = null,
) {
    // Design pass 2026-10-20: the hero holds one number and one line. It used
    // to stack balance, month net, outlook, contributions, two tinted stat
    // cards and the wallet chips inside one gradient card with a purple glow
    // (5 levels at once; a shadow on a dark surface only muddies it). Now a
    // flat tonal surface with a hairline, and the rest sits below it.
    // Design audit 2026-11-26: the balance sits on the page itself, no card —
    // the monobank/Revolut pattern. Seven stacked grey surfaces came before
    // the first operation; the balance is the one thing that needs no box.
    val largeText = LocalDensity.current.fontScale >= 1.2f
    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.md)) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = RytmSpacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(if (vm.isMultiCurrency) R.string.finance_estimated_balance else R.string.finance_total_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // The forecast lives behind one quiet control (owner,
                    // 2026-11-03: the orange «Можливий мінус» line under the
                    // balance read as an alarm every time the app opened).
                    // Outside review 2026-11-27: a bare chart glyph beside the
                    // Analytics tab read as a second way to Analytics, so it
                    // carries its name now; a dot in the warning colour (it was
                    // the accent, the colour of everything else) says the
                    // forecast has a gap to look at.
                    val forecast = vm.cashForecast
                    if (vm.wallets.isNotEmpty() && (forecast.habit != null || forecast.events.isNotEmpty())) {
                        ForecastEntry(hasGap = forecast.shortfallDate != null, onClick = onOpenForecast)
                        Spacer(Modifier.width(RytmSpacing.sm))
                    }
                    ua.rytm.app.ui.screens.ProfileEntryAvatar()
                }
                Text(
                    text = heroAmountText(
                        maskedAmount(stringResource(if (vm.isMultiCurrency) R.string.finance_amount_uah_estimated else R.string.finance_amount_uah, formatMoney(ua.rytm.app.ui.rememberAnimatedAmount(vm.totalBalanceUah)))),
                        muted = MaterialTheme.colorScheme.onSurfaceVariant,
                        smallKopecks = true,
                    ),
                    // Tabular figures: proportional digits make the balance jitter
                    // horizontally as it changes.
                    style = MaterialTheme.typography.displayLarge.tabularNums(),
                    fontWeight = FontWeight.Black,
                    // One line that shrinks to fit: with the non-breaking group
                    // separator a long balance at a large font scale could only
                    // break in the middle of the number.
                    maxLines = 1,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 18.sp,
                        maxFontSize = MaterialTheme.typography.displayLarge.fontSize,
                    ),
                    // The balance opens «Капітал»: how it changed and where the money sits.
                    // Rounded tap area, padded and pulled back into line: the
                    // bare clickable flashed a sharp grey rectangle.
                    modifier = Modifier
                        .offset(x = -RytmSpacing.sm)
                        .clip(RoundedCornerShape(RytmRadii.Input))
                        .clickable(onClickLabel = stringResource(R.string.capital_title), onClick = onOpenCapital)
                        .padding(horizontal = RytmSpacing.sm),
                )

                // P5: income/expense live inside the hero as one quiet row
                // instead of two more bordered cards competing with the balance.
                Spacer(Modifier.height(RytmSpacing.sm))
                // Sums past ~9 digits ("+123 456 789,25 ₴") fill half the row and
                // ran into «Витрати»; such months stack like large text does.
                // Pace (Copilot's "spent vs. last month at this date") as a short
                // note under «Витрати», not a full sentence with an eighth sum.
                // Outside review 2026-11-27: «−51 250 ₴» over «−27 %» read as two
                // losses; the note says it in words and is green when spending
                // is down. The labels name the month: the balance above is all
                // time, the two sums are this month only.
                val previousMonth = ua.rytm.app.data.FinancialMonth.of(vm.today).minusMonths(1)
                val paceNote: Pair<String, Color>? = vm.monthPaceUah?.let { (now, before) ->
                    paceVsLastMonth(now, before)?.let { pct -> paceNoteText(pct, previousMonth) }
                }
                val monthLabel = monthForLabel(ua.rytm.app.data.FinancialMonth.of(vm.today))
                val incomeLabel = stringResource(R.string.finance_month_income, monthLabel)
                val expenseLabel = stringResource(R.string.finance_month_expense, monthLabel)
                val longSums = maxOf(formatMoney(vm.monthIncomeUah).length, formatMoney(vm.monthExpenseUah).length) > 11
                if (largeText || longSums) {
                    Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm)) {
                        HeroInlineStat(incomeLabel, vm.monthIncomeUah, positive = true)
                        HeroInlineStat(expenseLabel, vm.monthExpenseUah, positive = false, note = paceNote)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.lg)) {
                        HeroInlineStat(incomeLabel, vm.monthIncomeUah, positive = true, modifier = Modifier.weight(1f))
                        HeroInlineStat(expenseLabel, vm.monthExpenseUah, positive = false, modifier = Modifier.weight(1f), note = paceNote)
                    }
                }
                if (vm.isMultiCurrency) {
                    Text(
                        text = stringResource(R.string.finance_conversion_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = RytmSpacing.xs),
                    )
                }
            }
        }

        val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Edge to edge: the row reaches the screen sides and its padding lines the
        // first chip up with the page, so a scrolled chip slides under the screen
        // edge instead of being cut 16dp inside it (read as «ТУМБ»).
        LazyRow(
            state = fadeState,
            modifier = Modifier
                .layout { measurable, constraints ->
                    val bleed = HERO_SCREEN_PADDING.roundToPx()
                    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + 2 * bleed, maxWidth = constraints.maxWidth + 2 * bleed))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
                }
                .fadingEdges(fadeState),
            contentPadding = PaddingValues(horizontal = HERO_SCREEN_PADDING),
            horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
        ) {
            items(vm.wallets, key = { it.id }) { wallet ->
                WalletChip(
                    wallet = wallet,
                    balance = vm.walletBalance(wallet.id),
                    selected = vm.historyFilter.walletId == wallet.id,
                    onLongClick = onWalletLongClick?.let { cb -> { cb(wallet.id) } },
                ) { onWalletClick(wallet.id) }
            }
        }

        val contributions = vm.monthContributions
        if (contributions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(RytmRadii.Input))
                    .padding(RytmSpacing.md),
                verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs),
            ) {
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
                            maskedAmount("+${formatMoney(c.income)} · −${formatMoney(c.expense)}\u00A0₴"),
                            style = MaterialTheme.typography.bodySmall.tabularNums(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Who pays whom so this month's expenses are split equally.
                settleEqually(contributions).forEach { st ->
                    Text(
                        stringResource(R.string.finance_settle_line, st.from, st.to, maskedAmount("${formatMoney(st.amount)}\u00A0₴")),
                        style = MaterialTheme.typography.bodySmall,
                        color = ua.rytm.app.ui.theme.RytmSemantic.link,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroInlineStat(label: String, value: Double, positive: Boolean, modifier: Modifier = Modifier, note: Pair<String, Color>? = null) {
    val valueColor = if (positive) RytmSemantic.income else MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = maskedAmount(stringResource(R.string.finance_signed_uah, if (value == 0.0) "" else if (positive) "+" else "−", formatMoney(value))),
            style = MaterialTheme.typography.titleMedium.tabularNums(),
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        note?.let { (text, color) -> Text(text, style = MaterialTheme.typography.labelSmall.tabularNums(), color = color) }
    }
}

/** Padding of the Finance list; the wallet row bleeds through it. */
private val HERO_SCREEN_PADDING = 16.dp

/** Spent against last month at this date in whole percent (−28, +5); null without a base. */
internal fun paceVsLastMonth(now: Double, before: Double): Int? {
    if (before < 1.0) return null
    return Math.round((now - before) / before * 100).toInt()
}

/**
 * «на 27 % менше, ніж за ці дні жовтня»: the same words as Analytics and the
 * month summary, green when spending is down, the expense colour when up, and
 * plain within ±5 % (the band Analytics calls «на рівні»).
 */
@Composable
private fun paceNoteText(pct: Int, previous: java.time.YearMonth): Pair<String, Color> {
    val month = monthGenitive(previous)
    return when {
        pct <= -SAME_PACE_BAND -> stringResource(R.string.finance_pace_less, formatPercent(-pct), month) to RytmSemantic.income
        pct >= SAME_PACE_BAND -> stringResource(R.string.finance_pace_more, formatPercent(pct), month) to RytmSemantic.expense
        else -> stringResource(R.string.finance_pace_same, month) to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** A change under this many percent reads as «на рівні» everywhere (the PWA's band too). */
internal const val SAME_PACE_BAND = 5

/**
 * The forecast's entry beside the avatar: a small tonal pill with its name,
 * so it can't be taken for a way to Analytics, and a warning dot with a gap.
 */
@Composable
private fun ForecastEntry(hasGap: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.cash_forecast_entry)
    val description = if (hasGap) stringResource(R.string.cash_forecast_entry_gap) else stringResource(R.string.cash_forecast_title)
    Box {
        Row(
            Modifier
                .heightIn(min = RytmDimens.TouchTarget)
                .clip(RoundedCornerShape(RytmRadii.Pill))
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(RytmRadii.Pill))
                    .padding(horizontal = RytmSpacing.md, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(RytmIcons.TrendingUp, contentDescription = null, tint = if (hasGap) RytmSemantic.warning else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        if (hasGap) Box(
            Modifier.align(Alignment.TopEnd).padding(top = RytmSpacing.xs).size(9.dp)
                .background(RytmSemantic.warning, CircleShape),
        )
    }
}

/**
 * A wallet in a picker: «ПУМБ», «Monobank ••5129», and the currency only when it
 * is not the hryvnia («Revolut (USD)») — «(UAH)» on every row was noise.
 */
@Composable
internal fun walletOptionLabel(name: String, currency: String): String =
    localizedDomainText(name) + if (currency == "UAH") "" else " ($currency)"

/** A wallet's kind as a glyph: a dot in red (PUMB's colour) read as an error status. */
internal fun walletKindIcon(kind: String): ImageVector = when (kind) {
    "cash" -> RytmIcons.Payments
    "deposit" -> RytmIcons.AccountBalance
    "receivable" -> RytmIcons.AccountBalanceWallet
    else -> RytmIcons.CreditCard
}

@Composable
internal fun WalletChip(wallet: Wallet, balance: Double, selected: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit = {}) {
    // A tap shows that wallet's operations, like a card in a bank app
    // (owner, 2026-11-03); the selected chip is outlined in the wallet colour.
    // A long press opens the wallet order sheet (2026-11-15), only for editors.
    val shape = RoundedCornerShape(RytmRadii.Pill)
    val haptics = ua.rytm.app.ui.components.rememberRytmHaptics()
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(wallet.colorHex).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(wallet.colorHex)) else null,
        modifier = Modifier
            .semantics { this.selected = selected }
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { haptics.confirm(); cb() } },
                onLongClickLabel = if (onLongClick != null) stringResource(R.string.wallet_order_open) else null,
            ),
    ) {
        Row(Modifier.padding(horizontal = RytmSpacing.md, vertical = RytmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            // The wallet's kind in its colour (outside review 2026-11-27: a bare
            // red dot beside «ПУМБ» read as an error status, not a colour tag).
            Icon(walletKindIcon(wallet.kind), contentDescription = null, tint = Color(wallet.colorHex), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(localizedDomainText(wallet.name), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(RytmSpacing.sm))
            Text(
                maskedAmount("${formatMoney(balance)}\u00A0${currencySymbol(wallet.currency)}"),
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
        QuickAction(stringResource(R.string.tools_title), RytmIcons.CurrencyExchange, primary = false, onClick = onTools),
        QuickAction(stringResource(R.string.budgets_title), RytmIcons.PieChart, primary = false, onClick = onBudgets),
        QuickAction(stringResource(R.string.goals_title), RytmIcons.Flag, primary = false, onClick = onGoals),
    ).filterIndexed { index, _ -> canEdit || index == 0 }
    val largeText = LocalDensity.current.fontScale >= 1.2f

    // Design audit 2026-11-26: three 60dp grey tiles were the heaviest band on
    // the screen after the balance. Round tonal buttons with the label under
    // them (the bank-app action row) keep the same three targets with a
    // fraction of the grey area. At a large font scale the label would not
    // fit a third of the width, so each action takes its own line instead.
    @Composable
    fun ActionCircle(action: QuickAction, size: androidx.compose.ui.unit.Dp) {
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(RytmDimens.QuickActionIcon))
        }
    }

    if (largeText) {
        Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
            actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RytmRadii.Row))
                        .clickable(role = Role.Button, onClick = action.onClick)
                        .padding(vertical = RytmSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RytmSpacing.md),
                ) {
                    ActionCircle(action, RytmDimens.TouchTarget)
                    Text(action.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            actions.forEach { action ->
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) RytmInteraction.IconPressedScale else 1f,
                    animationSpec = motionAwareSpec(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)),
                    label = "quick-action-press",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(RytmRadii.Row))
                        .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = action.onClick)
                        .padding(vertical = RytmSpacing.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { ActionCircle(action, RytmDimens.QuickActionCircle) }
                    Text(
                        action.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // A viewer sees only «Інструменти»: keep it at a third of the width, not centred alone.
            repeat(3 - actions.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/**
 * «Історія операцій» with search behind a magnifier and the filters beside it
 * (outside review 2026-11-27: title, a full-width search field and a chip row
 * left room for two and a half operations; monobank's statement does the
 * same). The record count is gone from here — database trivia while looking
 * at the balance; a search or filter says how much it found instead.
 */
@Composable
internal fun HistoryHeader(searchOpen: Boolean, showTools: Boolean, activeFilterCount: Int, onToggleSearch: () -> Unit, onOpenFilters: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.finance_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (showTools) {
            androidx.compose.material3.IconButton(onClick = onToggleSearch, modifier = Modifier.size(RytmDimens.TouchTarget)) {
                Icon(
                    if (searchOpen) RytmIcons.Close else RytmIcons.Search,
                    contentDescription = stringResource(if (searchOpen) R.string.finance_search_close else R.string.finance_search_open),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FiltersButton(activeFilterCount, onOpenFilters)
        }
    }
}

@Composable
internal fun FilteredTotals(count: Int, spent: Double, earned: Double) {
    val parts = listOfNotNull(
        pluralStringResource(R.plurals.finance_found, count, count),
        if (spent > 0) stringResource(R.string.finance_filtered_spent, maskedAmount(formatMoney(spent))) else null,
        if (earned > 0) stringResource(R.string.finance_filtered_earned, maskedAmount(formatMoney(earned))) else null,
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}

@Composable
internal fun SearchField(vm: FinanceViewModel, modifier: Modifier = Modifier) {
    ua.rytm.app.ui.components.RytmSearchField(
        modifier = modifier,
        value = vm.search,
        onValueChange = vm::onSearchChange,
        prefix = stringResource(R.string.finance_search_prefix),
        // One plain hint first, then examples of what can be typed. «за категорією»
        // and the like read as «only by category» when caught mid-rotation.
        hints = listOf(
            stringResource(R.string.finance_search_what_all),
            stringResource(R.string.finance_search_what_merchant),
            // SmartSearch examples, so people find out they can type these.
            stringResource(R.string.finance_search_what_month),
            stringResource(R.string.finance_search_what_amount),
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
    val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyRow(state = fadeState, modifier = Modifier.fadingEdges(fadeState), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        items(types, key = { "t-${it.first}" }) { (value, label, icon) ->
            ua.rytm.app.ui.components.RytmFilterChip(
                selected = vm.typeFilter == value,
                onClick = { vm.onTypeFilterChange(value) },
                label = { Text(label) },
                leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) } },
            )
        }
        item(key = "divider") {
            Box(Modifier.padding(horizontal = RytmSpacing.xs).size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        val range = vm.historyFilter
        if (range.hasRange) item(key = "range") {
            ua.rytm.app.ui.components.RytmFilterChip(
                selected = true,
                onClick = { vm.onPeriodFilterChange(PeriodFilter.ALL) },
                label = { Text(listOf(range.from, range.to).joinToString(" – ") { d -> d?.let(::shortDate) ?: "…" }) },
                leadingIcon = { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.filters_reset), modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        items(periods, key = { "p-${it.first}" }) { (value, label) ->
            ua.rytm.app.ui.components.RytmFilterChip(
                selected = !range.hasRange && vm.periodFilter == value,
                onClick = { vm.onPeriodFilterChange(value) },
                label = { Text(label) },
            )
        }
    }
}

private fun shortDate(iso: String): String =
    runCatching { java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy")) }.getOrDefault(iso)

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
        Modifier.fillMaxWidth().padding(top = RytmSpacing.xs).semantics(mergeDescendants = true) { heading() },
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
        // «ПУМБ ✕»: the word «Скинути» beside the ✕ said the same thing twice.
        label = { Text(localizedDomainText(category)) },
        trailingIcon = { Icon(RytmIcons.Close, contentDescription = stringResource(R.string.filters_reset), modifier = Modifier.size(FilterChipDefaults.IconSize)) },
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
    /** Tap opens the row even without edit rights (a viewer reads it). */
    canOpen: Boolean = canEdit,
    onClick: () -> Unit,
    onSwipeOpenChange: (Boolean) -> Unit = {},
    /** Wallet balance after this operation, shown small under the amount; null = off. */
    balanceAfter: Double? = null,
    /** Segment shape inside the day group; null = a standalone rounded row. */
    shape: androidx.compose.ui.graphics.Shape? = null,
    /** Off while the history is filtered to one wallet: every row would repeat it. */
    showWallet: Boolean = true,
) {
    val rowShape = shape ?: MaterialTheme.shapes.large
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val deleteLabel = stringResource(R.string.action_delete)
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
        // Critically damped spring: physical settle, never overshoots past 0 into the delete zone.
        animate(offsetPx, target, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { value, _ -> offsetPx = value }
    }

    LaunchedEffect(resetGeneration) {
        settleAt(0f)
    }
    // Lets the screen hide its FAB while this row's delete button is showing.
    val swipeOpen = offsetPx < 0f
    LaunchedEffect(swipeOpen) { onSwipeOpenChange(swipeOpen) }
    androidx.compose.runtime.DisposableEffect(tx.id) { onDispose { onSwipeOpenChange(false) } }

    Box(
        Modifier
            .fillMaxWidth()
            // Clip + the red reveal layer below only while the row is actually
            // swiped: drawing a full-size red layer (plus a rounded clip) under
            // EVERY row was pure GPU overdraw — the expanded 443-row history
            // was RenderThread-bound on a Galaxy A51 (Perfetto, live).
            .then(if (offsetPx < 0f) Modifier.clip(rowShape) else Modifier)
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
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        // The ripple outlives the press: it appears a moment after a quick
        // tap is already released and then fades out. Clipping only while
        // pressed let that tail draw as a square block behind the rounded
        // card (reported live), so the clip stays until the fade is over.
        var rippleClip by remember { mutableStateOf(false) }
        LaunchedEffect(interaction) {
            interaction.interactions.collectLatest { event ->
                rippleClip = true
                if (event !is androidx.compose.foundation.interaction.PressInteraction.Press) {
                    kotlinx.coroutines.delay(RippleFadeClipMillis)
                    rippleClip = false
                }
            }
        }
        Box(
            modifier = Modifier
                .then(if (offsetPx < 0f) Modifier.width(cardWidthDp) else Modifier.fillMaxWidth())
                .then(if (rippleClip) Modifier.clip(rowShape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, rowShape)
                .clickable(
                    enabled = canOpen,
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    role = Role.Button,
                ) {
                    if (offsetPx < -1f) scope.launch { settleAt(0f) } else onClick()
                }
                // Swipe is a path gesture; TalkBack/Switch Access users get
                // the same delete as an action (WCAG 2.5.1).
                .semantics {
                    if (canEdit) customActions = listOf(CustomAccessibilityAction(deleteLabel) { onDelete() })
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
            Row(Modifier.padding(RytmSpacing.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // A known merchant in the comment shows its monogram (MerchantBrands); else the category icon.
                // Comment first (bank pushes put the merchant there), then the entry's tags ("Коло").
                val brand = remember(tx.comment, tx.tags) { MerchantBrands.match(tx.comment) ?: tx.tags.firstNotNullOfOrNull { MerchantBrands.match(tagLookup(it)?.name) } }
                if (brand != null && tx.type == TxType.EXPENSE) MerchantBadge(brand) else CategoryIconBadge(tx.category, iconOverride = iconOverride)
                Spacer(Modifier.width(RytmSpacing.md))
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
                    // Design pass 2: the title is what the money was for; the wallet moves
                    // to the quiet second line ("ПУМБ · KOLO KIYEV UA") instead of repeating
                    // in bold on every row. A transfer's title is its route.
                    val catLine = if (tx.type == TxType.TRANSFER && walletLabel != null && targetWalletLabel != null) "$walletLabel → $targetWalletLabel"
                    else buildString {
                        append(categoryLabel)
                        tx.subcategory?.let { append(" · $it") }
                    }
                    Text(catLine, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // The date lives in the day header above.
                    // The wallet is left out when the title already names it
                    // («Комісії · Поповнення ПУМБ» over «ПУМБ») or the list shows one wallet.
                    val secondLine = listOfNotNull(
                        walletLabel?.takeIf { tx.type != TxType.TRANSFER && showWallet && !catLine.contains(it, ignoreCase = true) },
                        // The statement line without gateway, city and country, and
                        // left out when the title already says it («Мобільний зв'язок ·
                        // Vodafone» over «RPAY *VODAFONE KYIV»).
                        tx.comment?.takeIf { it.isNotBlank() }?.let(StatementText::clean)
                            ?.takeIf { !catLine.contains(it, ignoreCase = true) },
                    ).joinToString(" · ")
                    if (secondLine.isNotEmpty()) {
                        Text(secondLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val rowTags = tx.tags.mapNotNull(tagLookup)
                    if (rowTags.isNotEmpty()) {
                        Row(Modifier.padding(top = RytmSpacing.xs), horizontalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
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
                Spacer(Modifier.width(RytmSpacing.sm))
                val (amountText, amountColor) = when (tx.type) {
                    TxType.INCOME -> maskedAmount("+${formatMoney(tx.amount)}\u00A0${currencySymbol(tx.currency)}") to RytmSemantic.income
                    TxType.EXPENSE -> maskedAmount("−${formatMoney(tx.amount)}\u00A0${currencySymbol(tx.currency)}") to MaterialTheme.colorScheme.onSurface
                    TxType.TRANSFER -> maskedAmount("${formatMoney(tx.amount)}\u00A0${currencySymbol(tx.currency)}") to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(amountText, style = MaterialTheme.typography.bodyMedium.tabularNums(), fontWeight = FontWeight.Bold, color = amountColor)
                    balanceAfter?.let { b ->
                        // Quieter than the amount so it never reads as a second sum
                        // (a converted amount, say); TalkBack says what it is.
                        val balanceText = maskedAmount("${if (b < 0) "−" else ""}${formatMoney(kotlin.math.abs(b))}\u00A0${currencySymbol(tx.currency)}")
                        val balanceLabel = stringResource(R.string.transaction_balance_after, balanceText)
                        // «зал. 209,90 ₴»: a bare second sum under «+200 ₴» read as
                        // the same amount twice (outside review, 2026-11-27).
                        Text(
                            stringResource(R.string.transaction_balance_after_short, balanceText),
                            style = MaterialTheme.typography.labelSmall.tabularNums(),
                            color = if (b < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            modifier = Modifier.semantics { contentDescription = balanceLabel },
                        )
                    }
                }
            }
        }
    }
}

/** Seam between the rows of one day group. */
internal val GroupedRowGap = 2.dp
private val GroupedInnerRadius = 6.dp

/** Outer corners round on the group's first and last row, small ones on the seams. */
internal fun groupedRowShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) RytmRadii.Card else GroupedInnerRadius
    val bottom = if (index == count - 1) RytmRadii.Card else GroupedInnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** How long a row keeps its rounded clip after a press ends, so the fading ripple stays inside the card. */
private const val RippleFadeClipMillis = 450L

/** Read-only view of one operation, for a viewer of a shared profile. */
@Composable
internal fun TransactionDetailsDialog(tx: Transaction, walletName: String?, tagNames: List<String>, onDismiss: () -> Unit) {
    val sign = when (tx.type) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; TxType.TRANSFER -> "" }
    val locale = LocalConfiguration.current.locales[0]
    val dateText = remember(tx.date, locale) {
        runCatching { java.time.LocalDate.parse(tx.date).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", locale)) }.getOrDefault(tx.date)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(maskedAmount("$sign${formatMoney(tx.amount)}\u00A0${currencySymbol(tx.currency)}"), style = MaterialTheme.typography.titleLarge.tabularNums()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs)) {
                @Composable fun line(label: Int, value: String?) {
                    if (value.isNullOrBlank()) return
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
                line(R.string.tx_details_category, listOfNotNull(ua.rytm.app.ui.localizedDomainText(tx.category).takeIf { tx.type != TxType.TRANSFER }, tx.subcategory).joinToString(" › "))
                line(R.string.tx_details_wallet, walletName)
                line(R.string.date_label, dateText)
                line(R.string.tx_details_comment, tx.comment)
                line(R.string.tx_details_tags, tagNames.joinToString(", ").ifEmpty { null })
                line(R.string.tx_details_author, tx.createdByName)
            }
        },
        confirmButton = { ua.rytm.app.ui.components.RytmTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

/** The decimal separator and two digits at the end of an amount: «,90» in «2 409,90 ₴». */
private val KOPECKS = Regex("""[,.]\d{2}(?=\D*$)""")

/**
 * The hero balance with a lighter, smaller currency sign: at Black weight the
 * two strokes of «₴» merge into one blot, and the sign should not outweigh the
 * digits. [muted] tints the sign (and kopecks); [smallKopecks] shrinks them too.
 */
internal fun heroAmountText(text: String, muted: Color = Color.Unspecified, smallKopecks: Boolean = false): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        var from = text.indexOf('₴')
        while (from >= 0) {
            addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Medium, fontSize = 0.72.em, color = muted), from, from + 1)
            from = text.indexOf('₴', from + 1)
        }
        // Kopecks smaller and quieter, so the whole hryvnias read first (the
        // Revolut/monobank balance; outside review, 2026-11-27).
        if (smallKopecks) {
            KOPECKS.find(text)?.let { m ->
                addStyle(androidx.compose.ui.text.SpanStyle(fontSize = 0.72.em, color = muted), m.range.first, m.range.last + 1)
            }
        }
    }
