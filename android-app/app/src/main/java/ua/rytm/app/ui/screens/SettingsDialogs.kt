package ua.rytm.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.rytm.app.R
import ua.rytm.app.data.CsvImportPreview
import ua.rytm.app.ui.icons.CheckCircle
import ua.rytm.app.ui.icons.CloudDone
import ua.rytm.app.ui.icons.GridView
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.Star
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.icons.UploadFile
import ua.rytm.app.ui.rytmApp
import ua.rytm.app.ui.screens.finance.AutoRulesManagerSheet
import ua.rytm.app.ui.screens.finance.BudgetsManagerSheet
import ua.rytm.app.ui.screens.finance.CategoriesManagerSheet
import ua.rytm.app.ui.screens.finance.GoalsManagerSheet
import ua.rytm.app.ui.screens.finance.RatesManagerSheet
import ua.rytm.app.ui.screens.finance.RecurringManagerSheet
import ua.rytm.app.ui.screens.finance.TagsManagerSheet
import ua.rytm.app.ui.screens.finance.WalletsManagerSheet
import ua.rytm.app.ui.screens.finance.WidgetsManagerSheet
import ua.rytm.app.ui.screens.pin.PinSettingsSheet
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.screens.shifts.ShiftTypesManagerSheet
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Pink
import ua.rytm.app.ui.theme.RytmRadii

/** The manager sheets Settings can open; only one is ever open at a time. */
enum class SettingsSheet {
    Wallets, Categories, Budgets, Tags, Recurring, AutoRules, Goals, Widgets, Rates,
    ShiftTypes, NotificationTypes, Backups, Profiles, Pin,
}

/**
 * [uid] is the signed-in account, [dataUid] whose tree holds the active
 * profile's data (the owner's, for a shared profile).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsManagerSheets(
    open: SettingsSheet?,
    onClose: () -> Unit,
    uid: String,
    dataUid: String,
    profileId: String,
    isSharedProfile: Boolean,
    settingsViewModel: SettingsViewModel,
    onProfileSwitched: () -> Unit,
) {
    val app = rytmApp()
    when (open) {
        null -> Unit
        SettingsSheet.Wallets -> WalletsManagerSheet(app.financeRepository, app.financeSyncRepository, dataUid, profileId, onDismiss = onClose)
        SettingsSheet.Categories -> CategoriesManagerSheet(app.financeRepository, app.categoriesSyncRepository, dataUid, profileId, onDismiss = onClose)
        SettingsSheet.Budgets -> BudgetsManagerSheet(repository = app.financeRepository, syncRepository = app.budgetsSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.Tags -> TagsManagerSheet(repository = app.financeRepository, syncRepository = app.tagsSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.Recurring -> RecurringManagerSheet(repository = app.financeRepository, syncRepository = app.recurringSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.AutoRules -> AutoRulesManagerSheet(repository = app.financeRepository, sync = app.autoRulesSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.Goals -> GoalsManagerSheet(repository = app.financeRepository, syncRepository = app.goalsSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.Widgets -> WidgetsManagerSheet(settingsStore = app.settingsStore, syncRepository = app.widgetSettingsSyncRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.Rates -> RatesManagerSheet(
            uid = dataUid,
            profileId = profileId,
            financeRepository = app.financeRepository,
            syncRepository = app.currencyRatesSyncRepository,
            settingsStore = app.settingsStore,
            onDismiss = onClose,
        )
        SettingsSheet.ShiftTypes -> ShiftTypesManagerSheet(repository = app.shiftsRepository, uid = dataUid, profileId = profileId, onDismiss = onClose)
        SettingsSheet.NotificationTypes -> NotificationSettingsSheet(uid = dataUid, repository = app.pushRepository, profileId = profileId, onDismiss = onClose)
        // Backups are only ever of the account's own profiles.
        SettingsSheet.Backups -> if (!isSharedProfile) BackupsSheet(uid = uid, profileId = profileId, viewModel = settingsViewModel, onDismiss = onClose)
        SettingsSheet.Profiles -> ProfilesManagerSheet(uid = uid, onDismiss = onClose, onSwitched = { onClose(); onProfileSwitched() })
        SettingsSheet.Pin -> {
            // Same Activity-scoped viewModelStoreOwner as MainActivity's own PinViewModel
            // — see that call site's comment for why they must resolve to one instance.
            val activity = androidx.activity.compose.LocalActivity.current as androidx.fragment.app.FragmentActivity
            val pinViewModel: PinViewModel = viewModel(factory = PinViewModel.factory(app.pinStore, uid), viewModelStoreOwner = activity)
            PinSettingsSheet(pinViewModel, onDismiss = onClose)
        }
    }
}

@Composable
fun CsvImportPreviewDialog(preview: CsvImportPreview, busy: Boolean, canImport: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val importCount = pluralStringResource(R.plurals.settings_csv_operations, preview.transactions.size, preview.transactions.size)
    val skippedCount = pluralStringResource(R.plurals.settings_csv_errors, preview.errors.size, preview.errors.size)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(RytmRadii.Sheet),
        icon = {
            Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(RytmIcons.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        title = { Text(stringResource(R.string.settings_csv_import), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (preview.errors.isEmpty()) stringResource(R.string.settings_csv_confirm, importCount) else stringResource(R.string.settings_csv_confirm_with_errors, importCount, skippedCount))
                preview.errors.take(10).forEach { error ->
                    Text(csvImportErrorText(error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (preview.errors.size > 10) Text(stringResource(R.string.settings_csv_more_errors, preview.errors.size - 10), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy && canImport, onClick = onConfirm, shape = RoundedCornerShape(RytmRadii.Control)) { Text(stringResource(R.string.settings_csv_import_action)) }
        },
        dismissButton = {
            OutlinedButton(enabled = !busy, onClick = onDismiss, shape = RoundedCornerShape(RytmRadii.Control)) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun PremiumDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(OrangeDark, Pink))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(RytmIcons.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        },
        title = { Text(stringResource(R.string.settings_premium_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_premium_body))
                PremiumPerkRow(RytmIcons.CheckCircle, GreenDark, stringResource(R.string.settings_premium_free_title), stringResource(R.string.settings_premium_free_body))
                PremiumPerkRow(RytmIcons.TrendingUp, GreenDark, stringResource(R.string.settings_premium_forecast_title), stringResource(R.string.settings_premium_forecast_body))
                PremiumPerkRow(RytmIcons.CloudDone, GreenDark, stringResource(R.string.settings_premium_backup_title), stringResource(R.string.settings_premium_backup_body))
                PremiumPerkRow(RytmIcons.GridView, GreenDark, stringResource(R.string.settings_premium_widget_title), stringResource(R.string.settings_premium_widget_body))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(RytmRadii.Row)) { Text(stringResource(R.string.action_done)) }
        },
    )
}
