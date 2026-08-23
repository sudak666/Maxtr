package ua.rytm.app.ui.screens

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.CsvImportPreview
import ua.rytm.app.data.CsvImportError
import ua.rytm.app.data.CsvImportErrorReason
import ua.rytm.app.data.TransactionsCsvRepository
import ua.rytm.app.data.ProfileBackupRepository
import ua.rytm.app.data.local.clearAllProfileScopedTables
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.screens.finance.BudgetsManagerSheet
import ua.rytm.app.ui.screens.finance.AutoRulesManagerSheet
import ua.rytm.app.ui.screens.finance.CategoriesManagerSheet
import ua.rytm.app.ui.screens.finance.GoalsManagerSheet
import ua.rytm.app.ui.screens.finance.MonobankManagerSheet
import ua.rytm.app.ui.screens.finance.RecurringManagerSheet
import ua.rytm.app.ui.screens.finance.TagsManagerSheet
import ua.rytm.app.ui.screens.finance.RatesManagerSheet
import ua.rytm.app.ui.screens.finance.WalletsManagerSheet
import ua.rytm.app.ui.screens.finance.WidgetsManagerSheet
import ua.rytm.app.ui.screens.pin.PinSettingsSheet
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.screens.shifts.ShiftTypesManagerSheet
import ua.rytm.app.ui.theme.RytmDimens

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(authViewModel: AuthViewModel = viewModel()) {
    val canEdit = LocalCanEditProfile.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as RytmApplication
    val scope = rememberCoroutineScope()
    var walletsSheetOpen by remember { mutableStateOf(false) }
    var categoriesSheetOpen by remember { mutableStateOf(false) }
    var budgetsSheetOpen by remember { mutableStateOf(false) }
    var tagsSheetOpen by remember { mutableStateOf(false) }
    var recurringSheetOpen by remember { mutableStateOf(false) }
    var autoRulesSheetOpen by remember { mutableStateOf(false) }
    var goalsSheetOpen by remember { mutableStateOf(false) }
    var ratesSheetOpen by remember { mutableStateOf(false) }
    var monobankSheetOpen by remember { mutableStateOf(false) }
    var widgetsSheetOpen by remember { mutableStateOf(false) }
    var shiftTypesSheetOpen by remember { mutableStateOf(false) }
    var pinSheetOpen by remember { mutableStateOf(false) }
    var notifTypesSheetOpen by remember { mutableStateOf(false) }
    var profilesSheetOpen by remember { mutableStateOf(false) }
    var pendingSignOut by remember { mutableStateOf(false) }
    var premiumDialogOpen by remember { mutableStateOf(false) }
    var pendingDeleteAccount by remember { mutableStateOf(false) }
    var pendingResetProfile by remember { mutableStateOf(false) }
    var resetProfileBusy by remember { mutableStateOf(false) }
    var settingsSearch by remember { mutableStateOf("") }
    var settingsGroup by remember { mutableStateOf("all") }
    val csvRepository = remember { TransactionsCsvRepository(app.database, app.transactionsSyncRepository) }
    val backupRepository = remember { ProfileBackupRepository(app.database) }
    var csvImportPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var csvBusy by remember { mutableStateOf(false) }
    var backupPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingBackupPassword by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingRestorePayload by remember { mutableStateOf<ByteArray?>(null) }
    var pendingRestorePassword by remember { mutableStateOf<CharArray?>(null) }
    var backupRestorePreview by remember { mutableStateOf<ua.rytm.app.data.BackupPreview?>(null) }
    val darkTheme by app.settingsStore.isDarkTheme.collectAsState(initial = true)
    val hideAmounts by app.settingsStore.hideAmounts.collectAsState(initial = false)
    val privacyCacheEnabled by app.settingsStore.privacyCacheEnabled.collectAsState(initial = true)
    val language by app.settingsStore.language.collectAsState(initial = "uk")
    val uid = authViewModel.currentUser?.uid
    val activeProfileId by (if (uid != null) app.activeProfileStore.activeProfileId(uid) else flowOf(DEFAULT_PROFILE_ID)).collectAsState(initial = DEFAULT_PROFILE_ID)
    val activeProfileOwnerUid by (if (uid != null) app.activeProfileStore.activeProfileOwnerUid(uid) else flowOf(null)).collectAsState(initial = null)
    val pushEnabledMessage = stringResource(R.string.settings_push_enabled)
    val pushDisabledMessage = stringResource(R.string.settings_push_disabled)
    val pushChangeFailedMessage = stringResource(R.string.settings_push_change_failed)
    val pushPermissionDeniedMessage = stringResource(R.string.settings_push_permission_denied)
    val linkOpenFailedMessage = stringResource(R.string.settings_link_open_failed)
    val csvExportedMessage = stringResource(R.string.settings_csv_exported)
    val csvExportFailedMessage = stringResource(R.string.settings_csv_export_failed)
    val csvReadFailedMessage = stringResource(R.string.settings_csv_read_failed)
    val csvImportFailedMessage = stringResource(R.string.settings_csv_import_failed)
    val backupExportedMessage = stringResource(R.string.settings_backup_exported)
    val backupExportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val backupRestoredMessage = stringResource(R.string.settings_backup_restored)
    val backupRestoreFailedMessage = stringResource(R.string.settings_backup_restore_failed)

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { snackbarHostState.showSnackbar(it); pendingMessage = null }
    }
    LaunchedEffect(authViewModel.errorMessageRes) {
        authViewModel.errorMessageRes?.let { snackbarHostState.showSnackbar(resources.getString(it)); authViewModel.consumeError() }
    }

    var pushBusy by remember { mutableStateOf(false) }
    val pushEnabled by (if (uid != null) app.settingsStore.isPushEnabled(uid) else flowOf(false)).collectAsState(initial = false)

    // Mirrors js/notifications.js's enablePushNotifications()'s own
    // permission-then-register sequence. Only relevant on API 33+ — earlier
    // versions never require a runtime notification permission at all.
    fun applyPushEnabled(target: Boolean) {
        val accountUid = uid ?: return
        scope.launch {
            pushBusy = true
            try {
                val dataOwnerUid = activeProfileOwnerUid ?: accountUid
                if (target) app.pushRepository.enable(accountUid, dataOwnerUid, activeProfileId)
                else app.pushRepository.disable(accountUid, dataOwnerUid, activeProfileId)
                app.settingsStore.setPushEnabled(accountUid, target)
                pendingMessage = if (target) pushEnabledMessage else pushDisabledMessage
            } catch (e: Exception) {
                pendingMessage = pushChangeFailedMessage
            } finally {
                pushBusy = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) applyPushEnabled(true) else pendingMessage = pushPermissionDeniedMessage
    }

    fun onTogglePush(target: Boolean) {
        if (uid == null || pushBusy) return
        val needsRuntimePermission = target && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else applyPushEnabled(target)
    }

    fun sectionVisible(group: String, visibleTexts: List<String>): Boolean {
        if (settingsGroup != "all" && settingsGroup != group) return false
        val query = settingsSearch.trim().lowercase()
        return query.isEmpty() || visibleTexts.any { it.lowercase().contains(query) }
    }

    fun openExternalUrl(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { pendingMessage = linkOpenFailedMessage }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            csvBusy = true
            try {
                val csv = csvRepository.export(language)
                context.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    ?: error(csvExportFailedMessage)
                pendingMessage = csvExportedMessage
            } catch (_: Exception) { pendingMessage = csvExportFailedMessage }
            finally { csvBusy = false }
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            csvBusy = true
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error(csvReadFailedMessage)
                val preview = csvRepository.parse(text)
                if (preview.transactions.isEmpty()) pendingMessage = if (preview.errors.isEmpty()) {
                    resources.getString(R.string.settings_csv_empty)
                } else {
                    resources.getQuantityString(R.plurals.settings_csv_no_valid_rows, preview.errors.size, preview.errors.size)
                }
                else csvImportPreview = preview
            } catch (_: Exception) { pendingMessage = csvImportFailedMessage }
            finally { csvBusy = false }
        }
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestorePayload?.fill(0)
        pendingRestorePayload = null
        if (uri != null) scope.launch {
            backupBusy = true
            try {
                pendingRestorePayload = context.contentResolver.openInputStream(uri)?.use { it.readBoundedBackup() }
                    ?: error(backupRestoreFailedMessage)
                backupPassword = ""
                backupRestorePasswordDialog = true
            } catch (_: Exception) {
                pendingRestorePayload?.fill(0)
                pendingRestorePayload = null
                pendingMessage = backupRestoreFailedMessage
            } finally {
                backupBusy = false
            }
        }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingBackupPassword
        pendingBackupPassword = null
        if (uri != null && password != null) scope.launch {
            backupBusy = true
            var payload: ByteArray? = null
            try {
                val generated = backupRepository.export(password.toCharArray())
                payload = generated
                context.contentResolver.openOutputStream(uri)?.use { it.write(generated) }
                    ?: error(backupExportFailedMessage)
                pendingMessage = backupExportedMessage
            } catch (_: Exception) {
                pendingMessage = backupExportFailedMessage
            } finally {
                payload?.fill(0)
                backupBusy = false
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(innerPadding).padding(RytmDimens.ContentHorizontal),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

            OutlinedTextField(
                value = settingsSearch,
                onValueChange = { settingsSearch = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (settingsSearch.isNotEmpty()) {
                    {
                        androidx.compose.material3.IconButton(onClick = { settingsSearch = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.settings_clear_search))
                        }
                    }
                } else null,
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    "all" to stringResource(R.string.settings_group_all),
                    "account" to stringResource(R.string.settings_account),
                    "finance" to stringResource(R.string.settings_finance),
                    "security" to stringResource(R.string.settings_security),
                    "app" to stringResource(R.string.settings_appearance),
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = settingsGroup == key,
                        onClick = { settingsGroup = key },
                        label = { Text(label) },
                    )
                }
            }

            val accountVisible = sectionVisible("account", localizedSettingsStrings(
                R.string.settings_account, R.string.settings_sign_out, R.string.settings_sign_out_subtitle,
                R.string.profiles_title, R.string.settings_profiles_subtitle, R.string.settings_premium,
                R.string.settings_free_plan, R.string.settings_reset_profile, R.string.settings_reset_profile_subtitle,
                R.string.settings_delete_account, R.string.settings_delete_account_subtitle,
            ) + authViewModel.currentUser?.email.orEmpty())
            val securityVisible = uid != null && sectionVisible("security", localizedSettingsStrings(R.string.settings_security, R.string.pin_settings_title, R.string.settings_pin_subtitle))
            val notificationsVisible = uid != null && sectionVisible("security", localizedSettingsStrings(R.string.settings_notifications, R.string.settings_push, R.string.settings_push_subtitle, R.string.settings_notification_types, R.string.settings_notification_types_subtitle))
            val appearanceVisible = sectionVisible("app", localizedSettingsStrings(R.string.settings_appearance, R.string.settings_theme, R.string.settings_theme_light, R.string.settings_theme_dark))
            val aboutVisible = sectionVisible("app", localizedSettingsStrings(R.string.settings_about, R.string.settings_web, R.string.settings_web_subtitle, R.string.terms_title, R.string.settings_terms_subtitle, R.string.privacy_title, R.string.settings_privacy_subtitle, R.string.settings_about_summary))
            val financeVisible = sectionVisible("finance", localizedSettingsStrings(
                R.string.settings_finance, R.string.wallets_title, R.string.settings_wallets_subtitle,
                R.string.settings_monobank, R.string.settings_monobank_subtitle, R.string.rates_title,
                R.string.settings_rates_subtitle, R.string.categories_title, R.string.settings_categories_subtitle,
                R.string.budgets_title, R.string.settings_budgets_subtitle, R.string.tags_title,
                R.string.settings_tags_subtitle, R.string.goals_title, R.string.settings_goals_subtitle,
                R.string.widgets_title, R.string.settings_widgets_subtitle, R.string.recurring_title,
                R.string.settings_recurring_subtitle, R.string.shift_types_title, R.string.settings_shift_types_subtitle,
                R.string.settings_backup_export, R.string.settings_backup_export_subtitle,
            ))

            if (accountVisible) {
                if (uid != null) {
                    ProfileAppearanceCard(
                        uid = uid,
                        dataOwnerUid = activeProfileOwnerUid ?: uid,
                        profileId = activeProfileId,
                        email = authViewModel.currentUser?.email.orEmpty(),
                        repository = app.profileAppearanceRepository,
                        onMessage = { pendingMessage = it },
                    )
                }
                SettingsSectionLabel(stringResource(R.string.settings_account))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Filled.Groups,
                        badgeColor = Color(0xFF525158),
                        title = stringResource(R.string.settings_sign_out),
                        subtitle = stringResource(R.string.settings_sign_out_subtitle),
                        onClick = { pendingSignOut = true },
                    )
                    if (uid != null) {
                        SettingsRow(
                            icon = Icons.Filled.Groups,
                            badgeColor = Color(0xFF06B6D4),
                            title = stringResource(R.string.profiles_title),
                            subtitle = stringResource(R.string.settings_profiles_subtitle),
                            onClick = { profilesSheetOpen = true },
                        )
                        SettingsRow(
                            icon = Icons.Filled.Star,
                            badgeColor = Color(0xFFF59E0B),
                            title = stringResource(R.string.settings_premium),
                            subtitle = stringResource(R.string.settings_free_plan),
                            onClick = { premiumDialogOpen = true },
                        )
                        SettingsRow(
                            icon = Icons.Filled.DeleteForever,
                            badgeColor = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.settings_reset_profile),
                            subtitle = stringResource(R.string.settings_reset_profile_subtitle),
                            onClick = {
                                if (activeProfileOwnerUid != null) {
                                    pendingMessage = resources.getString(R.string.settings_reset_shared_error)
                                } else {
                                    pendingResetProfile = true
                                }
                            },
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                        SettingsRow(
                            icon = Icons.Filled.DeleteForever,
                            badgeColor = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.settings_delete_account),
                            subtitle = stringResource(R.string.settings_delete_account_subtitle),
                            onClick = { pendingDeleteAccount = true },
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            if (securityVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_security))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        badgeColor = Color(0xFF525158),
                        title = stringResource(R.string.pin_settings_title),
                        subtitle = stringResource(R.string.settings_pin_subtitle),
                        onClick = { pinSheetOpen = true },
                    )
                }

            }

            if (notificationsVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_notifications))
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Notifications,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_push),
                        subtitle = stringResource(R.string.settings_push_subtitle),
                        checked = pushEnabled,
                        enabled = !pushBusy,
                        onCheckedChange = ::onTogglePush,
                    )
                    // Only reachable once push is actually on — configuring
                    // *which* alerts to send is meaningless before the device
                    // has even registered to receive any (see
                    // NotificationSettingsSheet's own doc comment).
                    if (pushEnabled) {
                        SettingsRow(
                            icon = Icons.Filled.Tune,
                            badgeColor = Color(0xFFF59E0B),
                            title = stringResource(R.string.settings_notification_types),
                            subtitle = stringResource(R.string.settings_notification_types_subtitle),
                            onClick = { notifTypesSheetOpen = true },
                        )
                    }
                }
            }

            SettingsAppearanceSection(
                visible = appearanceVisible,
                darkTheme = darkTheme,
                language = language,
                hideAmounts = hideAmounts,
                privacyCacheEnabled = privacyCacheEnabled,
                onDarkThemeChange = { scope.launch { app.settingsStore.setDarkTheme(it) } },
                onLanguageChange = { scope.launch { app.settingsStore.setLanguage(it) } },
                onHideAmountsChange = { scope.launch { app.settingsStore.setHideAmounts(it) } },
                onPrivacyCacheChange = { scope.launch { app.settingsStore.setPrivacyCacheEnabled(it) } },
            )
            SettingsAboutSection(visible = aboutVisible, openExternalUrl = ::openExternalUrl)

            SettingsFinanceSection(
                visible = canEdit && financeVisible,
                actions = SettingsFinanceActions(
                    wallets = { walletsSheetOpen = true },
                    monobank = { monobankSheetOpen = true },
                    categories = { categoriesSheetOpen = true },
                    rates = { ratesSheetOpen = true },
                    budgets = { budgetsSheetOpen = true },
                    tags = { tagsSheetOpen = true },
                    goals = { goalsSheetOpen = true },
                    widgets = { widgetsSheetOpen = true },
                    recurring = { recurringSheetOpen = true },
                    autoRules = { autoRulesSheetOpen = true },
                    shiftTypes = { shiftTypesSheetOpen = true },
                    csvExport = { if (!csvBusy) csvExportLauncher.launch("rytm-finansy-${java.time.LocalDate.now()}.csv") },
                    csvImport = { if (!csvBusy) csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                    backupExport = { if (!backupBusy) { backupPassword = ""; backupPasswordDialog = true } },
                    backupRestore = { if (!backupBusy) backupRestoreLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                    backupRestoreAvailable = activeProfileOwnerUid == null,
                ),
            )
            if (!accountVisible && !securityVisible && !notificationsVisible && !appearanceVisible && !aboutVisible && !financeVisible) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.settings_search_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_search_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (walletsSheetOpen && uid != null) {
        WalletsManagerSheet(
            repository = app.financeRepository,
            syncRepository = app.financeSyncRepository,
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            onDismiss = { walletsSheetOpen = false },
        )
    }
    if (categoriesSheetOpen && uid != null) {
        CategoriesManagerSheet(
            repository = app.financeRepository,
            syncRepository = app.categoriesSyncRepository,
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            onDismiss = { categoriesSheetOpen = false },
        )
    }
    if (budgetsSheetOpen && uid != null) {
        BudgetsManagerSheet(repository = app.financeRepository, syncRepository = app.budgetsSyncRepository, uid = activeProfileOwnerUid ?: uid, profileId = activeProfileId, onDismiss = { budgetsSheetOpen = false })
    }
    if (tagsSheetOpen && uid != null) {
        TagsManagerSheet(repository = app.financeRepository, syncRepository = app.tagsSyncRepository, uid = activeProfileOwnerUid ?: uid, profileId = activeProfileId, onDismiss = { tagsSheetOpen = false })
    }
    if (recurringSheetOpen) {
        val accountUid = uid
        if (accountUid != null) RecurringManagerSheet(
            repository = app.financeRepository,
            syncRepository = app.recurringSyncRepository,
            uid = activeProfileOwnerUid ?: accountUid,
            profileId = activeProfileId,
            onDismiss = { recurringSheetOpen = false },
        )
    }
    if (autoRulesSheetOpen && uid != null) {
        AutoRulesManagerSheet(
            repository = app.financeRepository,
            sync = app.autoRulesSyncRepository,
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            onDismiss = { autoRulesSheetOpen = false },
        )
    }
    if (goalsSheetOpen) {
        val accountUid = uid
        if (accountUid != null) GoalsManagerSheet(
            repository = app.financeRepository,
            syncRepository = app.goalsSyncRepository,
            uid = activeProfileOwnerUid ?: accountUid,
            profileId = activeProfileId,
            onDismiss = { goalsSheetOpen = false },
        )
    }
    if (widgetsSheetOpen && uid != null) {
        WidgetsManagerSheet(
            settingsStore = app.settingsStore,
            syncRepository = app.widgetSettingsSyncRepository,
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            onDismiss = { widgetsSheetOpen = false },
        )
    }
    if (ratesSheetOpen && uid != null) {
        RatesManagerSheet(
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            financeRepository = app.financeRepository,
            syncRepository = app.currencyRatesSyncRepository,
            settingsStore = app.settingsStore,
            onDismiss = { ratesSheetOpen = false },
        )
    }
    if (monobankSheetOpen && uid != null) {
        MonobankManagerSheet(
            uid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            repository = app.monobankRepository,
            financeRepository = app.financeRepository,
            onDismiss = { monobankSheetOpen = false },
        )
    }
    if (shiftTypesSheetOpen && uid != null) {
        ShiftTypesManagerSheet(repository = app.shiftsRepository, uid = activeProfileOwnerUid ?: uid, profileId = activeProfileId, onDismiss = { shiftTypesSheetOpen = false })
    }
    if (notifTypesSheetOpen && uid != null) {
        NotificationSettingsSheet(uid = activeProfileOwnerUid ?: uid, repository = app.pushRepository, profileId = activeProfileId, onDismiss = { notifTypesSheetOpen = false })
    }
    if (profilesSheetOpen && uid != null) {
        ProfilesManagerSheet(
            uid = uid,
            onDismiss = { profilesSheetOpen = false },
            onSwitched = {
                profilesSheetOpen = false
                pendingMessage = resources.getString(R.string.settings_profile_switched)
            },
        )
    }
    if (pinSheetOpen && uid != null) {
        // Same Activity-scoped viewModelStoreOwner as MainActivity's own PinViewModel
        // — see that call site's comment for why they must resolve to one instance.
        val activity = context as androidx.fragment.app.FragmentActivity
        val pinViewModel: PinViewModel = viewModel(factory = PinViewModel.factory(app.pinStore, uid), viewModelStoreOwner = activity)
        PinSettingsSheet(pinViewModel, onDismiss = { pinSheetOpen = false })
    }

    csvImportPreview?.let { preview ->
        val importCount = pluralStringResource(R.plurals.settings_csv_operations, preview.transactions.size, preview.transactions.size)
        val skippedCount = pluralStringResource(R.plurals.settings_csv_errors, preview.errors.size, preview.errors.size)
        val importSuccess = stringResource(R.string.settings_csv_imported, preview.transactions.size)
        val importSaveFailed = stringResource(R.string.settings_csv_import_save_failed)
        AlertDialog(
            onDismissRequest = { if (!csvBusy) csvImportPreview = null },
            title = { Text(stringResource(R.string.settings_csv_import)) },
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
                TextButton(enabled = !csvBusy && uid != null, onClick = {
                    val accountUid = activeProfileOwnerUid ?: uid ?: return@TextButton
                    scope.launch {
                        csvBusy = true
                        try {
                            csvRepository.import(accountUid, activeProfileId, preview.transactions)
                            pendingMessage = importSuccess
                            csvImportPreview = null
                        } catch (_: Exception) { pendingMessage = importSaveFailed }
                        finally { csvBusy = false }
                    }
                }) { Text(stringResource(R.string.settings_csv_import_action)) }
            },
            dismissButton = { TextButton(enabled = !csvBusy, onClick = { csvImportPreview = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (backupPasswordDialog) {
        BackupPasswordDialog(
            restore = false,
            password = backupPassword,
            busy = backupBusy,
            onPasswordChange = { backupPassword = it },
            onDismiss = { backupPasswordDialog = false; backupPassword = "" },
            onConfirm = {
                pendingBackupPassword = backupPassword
                backupPassword = ""
                backupPasswordDialog = false
                backupExportLauncher.launch("rytm-profile-${java.time.LocalDate.now()}.rytmbackup")
            },
        )
    }

    if (backupRestorePasswordDialog) {
        BackupPasswordDialog(
            restore = true,
            password = backupPassword,
            busy = backupBusy,
            onPasswordChange = { backupPassword = it },
            onDismiss = {
                backupRestorePasswordDialog = false
                backupPassword = ""
                pendingRestorePayload?.fill(0)
                pendingRestorePayload = null
            },
            onConfirm = {
                val payload = pendingRestorePayload ?: return@BackupPasswordDialog
                val previewPassword = backupPassword.toCharArray()
                val restorePassword = backupPassword.toCharArray()
                backupPassword = ""
                scope.launch {
                    backupBusy = true
                    try {
                        backupRestorePreview = backupRepository.inspect(payload, previewPassword)
                        pendingRestorePassword?.fill('\u0000')
                        pendingRestorePassword = restorePassword
                        backupRestorePasswordDialog = false
                    } catch (_: Exception) {
                        restorePassword.fill('\u0000')
                        pendingRestorePayload?.fill(0)
                        pendingRestorePayload = null
                        pendingMessage = backupRestoreFailedMessage
                    } finally {
                        previewPassword.fill('\u0000')
                        backupBusy = false
                    }
                }
            },
        )
    }

    backupRestorePreview?.let { preview ->
        BackupRestorePreviewDialog(
            preview = preview,
            busy = backupBusy,
            onDismiss = {
                backupRestorePreview = null
                pendingRestorePassword?.fill('\u0000')
                pendingRestorePassword = null
                pendingRestorePayload?.fill(0)
                pendingRestorePayload = null
            },
            onConfirm = {
                val accountUid = uid ?: return@BackupRestorePreviewDialog
                val payload = pendingRestorePayload ?: return@BackupRestorePreviewDialog
                val password = pendingRestorePassword ?: return@BackupRestorePreviewDialog
                scope.launch {
                    backupBusy = true
                    try {
                        app.profileSyncCoordinator.restoreOwnProfile(
                            accountUid, activeProfileId, activeProfileOwnerUid, payload, password,
                        )
                        pendingMessage = backupRestoredMessage
                        backupRestorePreview = null
                    } catch (_: Exception) {
                        pendingMessage = backupRestoreFailedMessage
                    } finally {
                        password.fill('\u0000')
                        pendingRestorePassword = null
                        payload.fill(0)
                        pendingRestorePayload = null
                        backupBusy = false
                    }
                }
            },
        )
    }
    if (pendingDeleteAccount) {
        AlertDialog(
            onDismissRequest = { if (!authViewModel.isDeletingAccount) pendingDeleteAccount = false },
            title = { Text(stringResource(R.string.settings_delete_account)) },
            text = {
                if (authViewModel.isDeletingAccount) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.settings_deleting_account))
                    }
                } else {
                    Text(stringResource(R.string.settings_delete_account_body))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !authViewModel.isDeletingAccount,
                    onClick = { authViewModel.deleteAccount(context) },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !authViewModel.isDeletingAccount, onClick = { pendingDeleteAccount = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (pendingSignOut) {
        AlertDialog(
            onDismissRequest = { pendingSignOut = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingSignOut = false
                    scope.launch {
                        if (!privacyCacheEnabled) app.database.clearAllProfileScopedTables()
                        authViewModel.signOut()
                    }
                }) { Text(stringResource(R.string.settings_sign_out_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (premiumDialogOpen) {
        AlertDialog(
            onDismissRequest = { premiumDialogOpen = false },
            icon = {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(
                        Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899))),
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.settings_premium_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_premium_body))
                    PremiumPerkRow(
                        icon = Icons.Filled.CheckCircle,
                        color = Color(0xFF10B981),
                        title = stringResource(R.string.settings_premium_free_title),
                        subtitle = stringResource(R.string.settings_premium_free_body),
                    )
                    PremiumPerkRow(
                        icon = Icons.Filled.AccountBalanceWallet,
                        color = Color(0xFFF59E0B),
                        title = stringResource(R.string.settings_premium_banks_title),
                        subtitle = stringResource(R.string.settings_premium_banks_body),
                        badge = stringResource(R.string.settings_soon),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { premiumDialogOpen = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }
    if (pendingResetProfile && uid != null) {
        AlertDialog(
            onDismissRequest = { if (!resetProfileBusy) pendingResetProfile = false },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = {
                if (resetProfileBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.settings_resetting))
                    }
                } else {
                    Text(stringResource(R.string.settings_reset_body))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !resetProfileBusy,
                    onClick = {
                        resetProfileBusy = true
                        scope.launch {
                            try {
                                app.profileSyncCoordinator.resetOwnProfile(uid, activeProfileId, activeProfileOwnerUid)
                                pendingResetProfile = false
                                pendingMessage = resources.getString(R.string.settings_reset_success)
                            } catch (_: Exception) {
                                pendingMessage = resources.getString(R.string.settings_reset_failed)
                            } finally {
                                resetProfileBusy = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.settings_reset_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !resetProfileBusy, onClick = { pendingResetProfile = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    // The dialog above closes itself once the account is actually gone
    // (authViewModel.currentUser flips to null via the AuthStateListener,
    // which unmounts this whole screen behind the login screen) — no
    // explicit onSuccess callback needed.
}

@Composable
private fun PremiumPerkRow(icon: ImageVector, color: Color, title: String, subtitle: String, badge: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsIconBadge(icon, color)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        badge?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun csvImportErrorText(error: CsvImportError): String {
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

@Composable
private fun localizedSettingsStrings(vararg @StringRes ids: Int): List<String> = ids.map { stringResource(it) }
