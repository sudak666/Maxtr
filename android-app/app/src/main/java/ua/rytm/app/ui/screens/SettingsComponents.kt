package ua.rytm.app.ui.screens
import androidx.compose.ui.layout.layout
import androidx.core.net.toUri
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.CsvImportError
import ua.rytm.app.data.CsvImportErrorReason
import ua.rytm.app.data.local.ThemePreference
import ua.rytm.app.data.local.clearAllProfileScopedTables
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.screens.finance.BudgetsManagerSheet
import ua.rytm.app.ui.screens.finance.AutoRulesManagerSheet
import ua.rytm.app.ui.screens.finance.CategoriesManagerSheet
import ua.rytm.app.ui.screens.finance.GoalsManagerSheet
import ua.rytm.app.ui.screens.finance.RecurringManagerSheet
import ua.rytm.app.ui.screens.finance.TagsManagerSheet
import ua.rytm.app.ui.screens.finance.RatesManagerSheet
import ua.rytm.app.ui.screens.finance.WalletsManagerSheet
import ua.rytm.app.ui.screens.finance.WidgetsManagerSheet
import ua.rytm.app.ui.screens.pin.PinSettingsSheet
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.screens.shifts.ShiftTypesManagerSheet
import ua.rytm.app.ui.theme.Cyan
import ua.rytm.app.ui.theme.Slate
import ua.rytm.app.ui.theme.Teal
import ua.rytm.app.ui.theme.BlueDark
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.Gray
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Pink
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.BrightnessAuto
import ua.rytm.app.ui.icons.Category
import ua.rytm.app.ui.icons.Check
import ua.rytm.app.ui.icons.CheckCircle
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.CloudDone
import ua.rytm.app.ui.icons.CurrencyExchange
import ua.rytm.app.ui.icons.DarkMode
import ua.rytm.app.ui.icons.DeleteForever
import ua.rytm.app.ui.icons.Description
import ua.rytm.app.ui.icons.Download
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.GridView
import ua.rytm.app.ui.icons.Groups
import ua.rytm.app.ui.icons.Language
import ua.rytm.app.ui.icons.LightMode
import ua.rytm.app.ui.icons.Lock
import ua.rytm.app.ui.icons.Logout
import ua.rytm.app.ui.icons.Notifications
import ua.rytm.app.ui.icons.NotificationsActive
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.PrivacyTip
import ua.rytm.app.ui.icons.Repeat
import ua.rytm.app.ui.icons.RestartAlt
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.Sell
import ua.rytm.app.ui.icons.Star
import ua.rytm.app.ui.icons.Style
import ua.rytm.app.ui.icons.Tune
import ua.rytm.app.ui.icons.Upload
import ua.rytm.app.ui.icons.UploadFile
import ua.rytm.app.ui.icons.VisibilityOff

// Building blocks of SettingsScreen (rows, group cards, badges, selectors),
// split out so the screen file holds only the screen itself.

@Composable
internal fun PremiumPerkRow(icon: ImageVector, color: Color, title: String, subtitle: String, badge: String? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(RytmRadii.Row),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.09f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(42.dp).background(color.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp)) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    badge?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = color,
                            modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(RytmRadii.Pill)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun csvImportErrorText(error: CsvImportError): String {
    val reason = when (error.reason) {
        CsvImportErrorReason.TOO_FEW_COLUMNS -> stringResource(R.string.settings_csv_error_columns)
        CsvImportErrorReason.UNKNOWN_TYPE -> stringResource(R.string.settings_csv_error_type, error.detail.orEmpty())
        CsvImportErrorReason.UNKNOWN_WALLET -> stringResource(R.string.settings_csv_error_wallet, error.detail.orEmpty())
        CsvImportErrorReason.INVALID_AMOUNT -> stringResource(R.string.settings_csv_error_amount)
        CsvImportErrorReason.INVALID_DATE -> stringResource(R.string.settings_csv_error_date)
        CsvImportErrorReason.SAME_WALLETS -> stringResource(R.string.settings_csv_error_same_wallets)
        CsvImportErrorReason.INVALID_TRANSFER_AMOUNT -> stringResource(R.string.settings_csv_error_transfer_amount)
    }
    return stringResource(R.string.settings_csv_error_row, error.row, reason)
}

/**
 * Lowercased search keywords for one settings section, resolved once per
 * locale. Keyed on the resolved strings themselves, so a locale change (which
 * re-runs stringResource) produces a new list and a config change is handled
 * without an explicit configuration key.
 */
@Composable
internal fun rememberSettingsKeywords(vararg @StringRes ids: Int): List<String> {
    val resolved = ids.map { stringResource(it) }
    return remember(resolved) { resolved.map { it.lowercase() } }
}

@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

// Matches the PWA's .chart-section.settings-section — a rounded card
// grouping related rows, with a dashed divider between rows inside it
// (.settings-row+.settings-row{border-top:1px dashed}), not a flat list.
// Each row is collected into `rows` via SettingsRowScope.row {} instead of
// being placed directly, so this composable can insert a divider between
// every pair of rows without relying on any stateful/order-sensitive trick.
@Composable
internal fun SettingsGroupCard(content: @Composable SettingsRowScope.() -> Unit) {
    Card(shape = RoundedCornerShape(RytmRadii.Chart)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            SettingsRowScope.content()
        }
    }
}

internal object SettingsRowScope

internal enum class SegmentPosition { First, Middle, Last }

// A slice of SettingsGroupCard for groups rendered one LazyColumn item per
// row. Only the first/last slice rounds its outer corners, and non-last
// slices report 4dp less height than they draw so the LazyColumn's own 4dp
// item spacing is covered and the slices join into one seamless card.
@Composable
internal fun SettingsCardSegment(position: SegmentPosition, content: @Composable SettingsRowScope.() -> Unit) {
    val r = RytmRadii.Chart
    val shape = when (position) {
        SegmentPosition.First -> RoundedCornerShape(topStart = r, topEnd = r)
        SegmentPosition.Middle -> RoundedCornerShape(0.dp)
        SegmentPosition.Last -> RoundedCornerShape(bottomStart = r, bottomEnd = r)
    }
    val gap = 4.dp
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (position == SegmentPosition.Last) Modifier
                else Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height - gap.roundToPx()) { placeable.place(0, 0) }
                }
            )
            .background(CardDefaults.cardColors().containerColor, shape)
            .padding(start = 4.dp, end = 4.dp, bottom = if (position == SegmentPosition.Last) 0.dp else gap),
    ) {
        SettingsRowScope.content()
    }
}

// Matches the PWA's .icon-badge: a circular badge tinted at ~16% of its own
// color, with the icon drawn in that full color — not a generic outline icon.
@Composable
internal fun SettingsIconBadge(icon: ImageVector, color: Color) {
    Box(
        Modifier
            .size(RytmDimens.IconBadge)
            .background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(RytmDimens.IconBadgeIcon))
    }
}

/**
 * Badge colors are per GROUP, not per row — but only where that's still a
 * useful signal.
 *
 * They used to be assigned per row from a grab-bag of hex literals, which
 * made them read as decoration rather than encoding: the same green marked
 * "Goals", "Recurring", "CSV export" and "Offline cache", and the same blue
 * marked "Rates", "Website", "CSV import" and "Shift types". Per-GROUP color
 * fixed that for small groups (2-3 rows: Account, Security, Notifications,
 * Appearance, About) — a glance at the color tells you which section you're
 * in. It stopped being a useful signal for "Фінанси" specifically: reported
 * live (screenshot) as "майже всі іконки однакового кольору" — that group
 * alone has 11 rows, so its one shared green just repeats 11 times with zero
 * differentiation, unlike every other (small) group here. Real fintech apps
 * (Monobank, Revolut, N26) don't color-code dense settings lists at all —
 * every row gets one neutral badge, and color is reserved for the few
 * genuinely distinct things on screen (an active toggle, a brand mark like
 * Monobank's own badge below). `Neutral` is that same real-competitor
 * pattern applied to this one oversized group, not a removal of the whole
 * per-group-color idea — the small groups keep their own colors.
 */
internal object SettingsGroupColors {
    val Account = Cyan
    val Security = Slate
    val Notifications = PurpleDark
    val Appearance = Teal
    val About = BlueDark
    val Neutral = Gray
}


// Google Settings-style radio list: one full-width row per choice, an
// icon badge for quick scanning, and a trailing RadioButton — the row
// itself is also clickable (larger touch target than the radio glyph
// alone). Selected row gets a tinted background, same convention as
// SettingsToggleRow's own selected state elsewhere in this file.
@Composable
internal fun ThemePreferenceSelector(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        Triple(ThemePreference.LIGHT, RytmIcons.LightMode, stringResource(R.string.settings_theme_light)),
        Triple(ThemePreference.DARK, RytmIcons.DarkMode, stringResource(R.string.settings_theme_dark)),
        Triple(ThemePreference.SYSTEM, RytmIcons.BrightnessAuto, stringResource(R.string.settings_theme_system)),
    )
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(RytmRadii.Chart)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            options.forEachIndexed { index, (pref, icon, label) ->
                val isSelected = pref == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RytmRadii.Control))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(pref) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsIconBadge(icon, SettingsGroupColors.Appearance)
                    Text(
                        label,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                    )
                }
                if (index != options.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
internal fun RoundedChoiceSelector(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    val shape = RoundedCornerShape(RytmRadii.Pill)
    Row(
        modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Row(
                Modifier.weight(1f).clip(shape)
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = icons?.getOrNull(index)
                if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                else if (selected) Icon(RytmIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    label,
                    modifier = Modifier.padding(start = if (icon != null || selected) 7.dp else 0.dp),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SettingsRowScope.SettingsRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Control)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(icon, badgeColor)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(RytmIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun SettingsRowScope.SettingsToggleRow(
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(RytmRadii.Control)).toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SettingsIconBadge(icon, badgeColor)
        Column(Modifier.padding(start = 12.dp, end = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    // Was colorScheme.outline (#E4E4E9 in light) on
                    // surfaceContainerHigh (#E2E0DD) — two near-identical pale
                    // grays with ~1:1 contrast, so the thumb visually
                    // vanished into the track (reported live, screenshot:
                    // toggling push notifications off left the switch reading
                    // as blank). onSurfaceVariant is the same muted-gray
                    // family but dark enough to actually separate from the
                    // track in both themes.
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.72f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
            ),
        )
    }
}
