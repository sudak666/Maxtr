package ua.rytm.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.rytm.app.R

@Composable
internal fun SettingsAppearanceSection(
    visible: Boolean,
    darkTheme: Boolean,
    language: String,
    hideAmounts: Boolean,
    privacyCacheEnabled: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onHideAmountsChange: (Boolean) -> Unit,
    onPrivacyCacheChange: (Boolean) -> Unit,
) {
    if (!visible) return
    SettingsSectionLabel(stringResource(R.string.settings_appearance))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SegmentedButton(
            selected = !darkTheme,
            onClick = { onDarkThemeChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
        ) { Text(stringResource(R.string.settings_theme_light)) }
        SegmentedButton(
            selected = darkTheme,
            onClick = { onDarkThemeChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
        ) { Text(stringResource(R.string.settings_theme_dark)) }
    }
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        listOf("uk" to R.string.settings_language_uk, "en" to R.string.settings_language_en).forEachIndexed { index, option ->
            SegmentedButton(
                selected = language == option.first,
                onClick = { onLanguageChange(option.first) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
            ) { Text(stringResource(option.second)) }
        }
    }
    SettingsGroupCard {
        SettingsToggleRow(
            icon = Icons.Filled.VisibilityOff,
            badgeColor = Color(0xFF64748B),
            title = stringResource(R.string.settings_hide_amounts),
            subtitle = stringResource(R.string.settings_hide_amounts_subtitle),
            checked = hideAmounts,
            enabled = true,
            onCheckedChange = onHideAmountsChange,
        )
        SettingsToggleRow(
            icon = Icons.Filled.PrivacyTip,
            badgeColor = Color(0xFF10B981),
            title = stringResource(R.string.settings_offline_cache),
            subtitle = stringResource(if (privacyCacheEnabled) R.string.settings_offline_cache_on else R.string.settings_offline_cache_off),
            checked = privacyCacheEnabled,
            enabled = true,
            onCheckedChange = onPrivacyCacheChange,
        )
    }
}

@Composable
internal fun SettingsAboutSection(visible: Boolean, openExternalUrl: (String) -> Unit) {
    if (!visible) return
    SettingsSectionLabel(stringResource(R.string.settings_about))
    SettingsGroupCard {
        SettingsRow(
            icon = Icons.Filled.Language,
            badgeColor = Color(0xFF3B82F6),
            title = stringResource(R.string.settings_web),
            subtitle = stringResource(R.string.settings_web_subtitle),
            onClick = { openExternalUrl("https://maxtr-c238f.web.app") },
        )
        SettingsRow(
            icon = Icons.Filled.Description,
            badgeColor = Color(0xFF8B5CF6),
            title = stringResource(R.string.terms_title),
            subtitle = stringResource(R.string.settings_terms_subtitle),
            onClick = { openExternalUrl("https://maxtr-c238f.web.app/terms.html") },
        )
        SettingsRow(
            icon = Icons.Filled.PrivacyTip,
            badgeColor = Color(0xFF10B981),
            title = stringResource(R.string.privacy_title),
            subtitle = stringResource(R.string.settings_privacy_subtitle),
            onClick = { openExternalUrl("https://maxtr-c238f.web.app/privacy.html") },
        )
    }
    Text(
        stringResource(R.string.settings_about_summary),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

internal data class SettingsFinanceActions(
    val wallets: () -> Unit,
    val monobank: () -> Unit,
    val categories: () -> Unit,
    val rates: () -> Unit,
    val budgets: () -> Unit,
    val tags: () -> Unit,
    val goals: () -> Unit,
    val widgets: () -> Unit,
    val recurring: () -> Unit,
    val autoRules: () -> Unit,
    val shiftTypes: () -> Unit,
    val csvExport: () -> Unit,
    val csvImport: () -> Unit,
    val backupExport: () -> Unit,
)

private data class FinanceSettingsRow(
    val icon: ImageVector,
    val color: Color,
    val title: Int,
    val subtitle: Int,
    val onClick: () -> Unit,
)

@Composable
internal fun SettingsFinanceSection(visible: Boolean, actions: SettingsFinanceActions) {
    if (!visible) return
    SettingsSectionLabel(stringResource(R.string.settings_finance))
    val rows = listOf(
        FinanceSettingsRow(Icons.Filled.AccountBalanceWallet, Color(0xFF8B5CF6), R.string.wallets_title, R.string.settings_wallets_subtitle, actions.wallets),
        FinanceSettingsRow(Icons.Filled.AccountBalance, Color(0xFF111111), R.string.settings_monobank, R.string.settings_monobank_subtitle, actions.monobank),
        FinanceSettingsRow(Icons.Filled.Category, Color(0xFFEC4899), R.string.categories_title, R.string.settings_categories_subtitle, actions.categories),
        FinanceSettingsRow(Icons.Filled.CurrencyExchange, Color(0xFF3B82F6), R.string.rates_title, R.string.settings_rates_subtitle, actions.rates),
        FinanceSettingsRow(Icons.Filled.PieChart, Color(0xFFF59E0B), R.string.budgets_title, R.string.settings_budgets_subtitle, actions.budgets),
        FinanceSettingsRow(Icons.Filled.Sell, Color(0xFF06B6D4), R.string.tags_title, R.string.settings_tags_subtitle, actions.tags),
        FinanceSettingsRow(Icons.Filled.Flag, Color(0xFF10B981), R.string.goals_title, R.string.settings_goals_subtitle, actions.goals),
        FinanceSettingsRow(Icons.Filled.GridView, Color(0xFF14B8A6), R.string.widgets_title, R.string.settings_widgets_subtitle, actions.widgets),
        FinanceSettingsRow(Icons.Filled.Repeat, Color(0xFF10B981), R.string.recurring_title, R.string.settings_recurring_subtitle, actions.recurring),
        FinanceSettingsRow(Icons.Filled.Tune, Color(0xFFA78BFA), R.string.auto_rules_title, R.string.settings_auto_rules_subtitle, actions.autoRules),
        FinanceSettingsRow(Icons.Filled.Style, Color(0xFF3B82F6), R.string.shift_types_title, R.string.settings_shift_types_subtitle, actions.shiftTypes),
        FinanceSettingsRow(Icons.Filled.Download, Color(0xFF10B981), R.string.settings_csv_export, R.string.settings_csv_export_subtitle, actions.csvExport),
        FinanceSettingsRow(Icons.Filled.Upload, Color(0xFF3B82F6), R.string.settings_csv_import, R.string.settings_csv_import_subtitle, actions.csvImport),
        FinanceSettingsRow(Icons.Filled.Lock, Color(0xFF8B5CF6), R.string.settings_backup_export, R.string.settings_backup_export_subtitle, actions.backupExport),
    )
    SettingsGroupCard {
        rows.forEach { row ->
            SettingsRow(row.icon, row.color, stringResource(row.title), stringResource(row.subtitle), row.onClick)
        }
    }
}
