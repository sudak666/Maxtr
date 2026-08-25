package ua.rytm.app.ui.screens
import androidx.core.net.toUri

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Upload
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
import kotlinx.coroutines.withTimeout
import ua.rytm.app.R
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.data.DEFAULT_PROFILE_ID
import ua.rytm.app.data.CsvImportPreview
import ua.rytm.app.data.CsvImportError
import ua.rytm.app.data.CsvImportErrorReason
import ua.rytm.app.data.TransactionsCsvRepository
import ua.rytm.app.data.local.ThemePreference
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
import ua.rytm.app.ui.theme.Cyan
import ua.rytm.app.ui.theme.Slate
import ua.rytm.app.ui.theme.Teal
import ua.rytm.app.ui.theme.BlueDark
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.MonobankBrand
import ua.rytm.app.ui.theme.MonobankBrandDark
import ua.rytm.app.ui.theme.RytmSemantic
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Pink
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf

// "Гаманці"/"Категорії"/"Типи змін"/"Бюджети"/"Теги"/"Регулярні платежі"/
// "Push-сповіщення" (+ granular "Типи сповіщень")/"Профілі" (own+shared,
// invite/join/leave/roles — steps 30/32/33)/"Вигляд" (тема)/"Акаунт" (вихід
// + видалення, step 35)/"Безпека" (PIN+біометрія) are real so far — the
// rest of the PWA's Settings IA is deliberately not built yet, disclosed
// honestly rather than faked:
//   - Мова (uk/en toggle): blocked on a real prerequisite, not just
//     unstarted — every screen in this app hardcodes Ukrainian text
//     directly rather than going through string resources (see strings.xml,
//     which only covers nav labels). A real language switch needs that
//     whole strings.xml migration first (CLAUDE.md §3 improvement #12),
//     which is its own multi-session effort, not a corner of this step.
// See ANDROID_MIGRATION.md's "Chesno not done" convention.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(authViewModel: AuthViewModel = viewModel()) {
    val canEdit = LocalCanEditProfile.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as RytmApplication
    val scope = rememberCoroutineScope()
    var walletsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var categoriesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var budgetsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var tagsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var recurringSheetOpen by rememberSaveable { mutableStateOf(false) }
    var autoRulesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var goalsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var ratesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var monobankSheetOpen by rememberSaveable { mutableStateOf(false) }
    var widgetsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var shiftTypesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pinSheetOpen by rememberSaveable { mutableStateOf(false) }
    var notifTypesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var profilesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pendingSignOut by rememberSaveable { mutableStateOf(false) }
    var premiumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var pendingResetProfile by rememberSaveable { mutableStateOf(false) }
    var resetProfileBusy by rememberSaveable { mutableStateOf(false) }
    var settingsSearch by rememberSaveable { mutableStateOf("") }
    var settingsGroup by remember { mutableStateOf("all") }
    val csvRepository = remember { TransactionsCsvRepository(app.database, com.google.firebase.firestore.FirebaseFirestore.getInstance()) }
    var csvImportPreview by remember { mutableStateOf<CsvImportPreview?>(null) }
    var csvBusy by rememberSaveable { mutableStateOf(false) }
    val themePreference by app.settingsStore.themePreference.collectAsState(initial = ThemePreference.DARK)
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

    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbarHostState = LocalSnackbarHost.current ?: ownHost
    var pendingMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { snackbarHostState.showSnackbar(it); pendingMessage = null }
    }
    LaunchedEffect(authViewModel.errorMessageRes) {
        authViewModel.errorMessageRes?.let { snackbarHostState.showSnackbar(resources.getString(it)); authViewModel.consumeError() }
    }

    var pushBusy by rememberSaveable { mutableStateOf(false) }
    val pushEnabled by (if (uid != null) app.settingsStore.isPushEnabled(uid) else flowOf(false)).collectAsState(initial = false)
    var pendingPushEnabled by remember { mutableStateOf<Boolean?>(null) }
    val displayedPushEnabled = pendingPushEnabled ?: pushEnabled
    LaunchedEffect(pushEnabled, pendingPushEnabled) {
        if (pendingPushEnabled != null && pushEnabled == pendingPushEnabled) pendingPushEnabled = null
    }

    // Mirrors js/notifications.js's enablePushNotifications()'s own
    // permission-then-register sequence. Only relevant on API 33+ — earlier
    // versions never require a runtime notification permission at all.
    fun applyPushEnabled(target: Boolean) {
        val accountUid = uid ?: return
        if (pushBusy) return
        pendingPushEnabled = target
        pushBusy = true
        scope.launch {
            var preferenceSaved = false
            try {
                // Persist the user's intent first. FCM token registration can fail
                // transiently; that must not make the switch look unresponsive.
                app.settingsStore.setPushEnabled(accountUid, target)
                preferenceSaved = true
                val dataOwnerUid = activeProfileOwnerUid ?: accountUid
                withTimeout(10_000) {
                    if (target) app.pushRepository.enable(accountUid, dataOwnerUid, activeProfileId)
                    else app.pushRepository.disable(accountUid, dataOwnerUid, activeProfileId)
                }
                pendingMessage = if (target) pushEnabledMessage else pushDisabledMessage
            } catch (e: Exception) {
                Log.e("RytmPush", "Push registration failed; keeping user preference=$target", e)
                pendingMessage = pushChangeFailedMessage
                if (!preferenceSaved) pendingPushEnabled = null
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

    /** [keywords] are already lowercased — see rememberSettingsKeywords. */
    fun sectionVisible(group: String, keywords: List<String>): Boolean {
        if (settingsGroup != "all" && settingsGroup != group) return false
        val query = settingsSearch.trim().lowercase()
        return query.isEmpty() || keywords.any { it.contains(query) }
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

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        // Real bug found during step 39's visual-parity pass: this Column had
        // no scroll modifier at all, so on a real device everything past
        // "Категорії" (Бюджети/Теги/Регулярні платежі/Типи змін) was
        // permanently unreachable — not a styling gap, a genuine dead end.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = RytmDimens.ContentHorizontal)
                .padding(bottom = RytmDimens.BottomContentClearance),
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

            // Search index, built once per locale instead of on every
            // keystroke.
            //
            // This used to re-run six localizedSettingsStrings() calls on
            // every recomposition — ~80 stringResource lookups — and then
            // lowercase all of them again inside sectionVisible(), i.e. every
            // single character typed into the search box paid for the whole
            // set. The strings only change when the locale does.
            val accountKeywords = rememberSettingsKeywords(
                R.string.settings_account, R.string.settings_sign_out, R.string.settings_sign_out_subtitle,
                R.string.profiles_title, R.string.settings_profiles_subtitle, R.string.settings_premium,
                R.string.settings_free_plan, R.string.settings_reset_profile, R.string.settings_reset_profile_subtitle,
                R.string.settings_delete_account, R.string.settings_delete_account_subtitle,
            )
            val securityKeywords = rememberSettingsKeywords(R.string.settings_security, R.string.pin_settings_title, R.string.settings_pin_subtitle)
            val notificationsKeywords = rememberSettingsKeywords(R.string.settings_notifications, R.string.settings_push, R.string.settings_push_subtitle, R.string.settings_notification_types, R.string.settings_notification_types_subtitle)
            val appearanceKeywords = rememberSettingsKeywords(R.string.settings_appearance, R.string.settings_theme, R.string.settings_theme_light, R.string.settings_theme_dark, R.string.settings_theme_system)
            val aboutKeywords = rememberSettingsKeywords(R.string.settings_about, R.string.settings_web, R.string.settings_web_subtitle, R.string.terms_title, R.string.settings_terms_subtitle, R.string.privacy_title, R.string.settings_privacy_subtitle, R.string.settings_about_summary)
            val financeKeywords = rememberSettingsKeywords(
                R.string.settings_finance, R.string.wallets_title, R.string.settings_wallets_subtitle,
                R.string.settings_monobank, R.string.settings_monobank_subtitle, R.string.rates_title,
                R.string.settings_rates_subtitle, R.string.categories_title, R.string.settings_categories_subtitle,
                R.string.budgets_title, R.string.settings_budgets_subtitle, R.string.tags_title,
                R.string.settings_tags_subtitle, R.string.goals_title, R.string.settings_goals_subtitle,
                R.string.widgets_title, R.string.settings_widgets_subtitle, R.string.recurring_title,
                R.string.settings_recurring_subtitle, R.string.shift_types_title, R.string.settings_shift_types_subtitle,
            )
            val accountEmail = authViewModel.currentUser?.email.orEmpty().lowercase()

            // derivedStateOf: only re-evaluates when the query or the group
            // actually changes, not on every unrelated recomposition of this
            // screen (of which there are many — 14+ sheet-open flags live here).
            val accountVisible by remember(accountKeywords, accountEmail) {
                derivedStateOf { sectionVisible("account", accountKeywords + accountEmail) }
            }
            val securityVisible by remember(securityKeywords) { derivedStateOf { sectionVisible("security", securityKeywords) } }
            val notificationsVisible by remember(notificationsKeywords) { derivedStateOf { sectionVisible("security", notificationsKeywords) } }
            val appearanceVisible by remember(appearanceKeywords) { derivedStateOf { sectionVisible("app", appearanceKeywords) } }
            val aboutVisible by remember(aboutKeywords) { derivedStateOf { sectionVisible("app", aboutKeywords) } }
            val financeVisible by remember(financeKeywords) { derivedStateOf { sectionVisible("finance", financeKeywords) } }

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
                        icon = Icons.AutoMirrored.Filled.Logout,
                        badgeColor = SettingsGroupColors.Account,
                        title = stringResource(R.string.settings_sign_out),
                        subtitle = stringResource(R.string.settings_sign_out_subtitle),
                        onClick = { pendingSignOut = true },
                    )
                    if (uid != null) {
                        SettingsRow(
                            icon = Icons.Filled.Groups,
                            badgeColor = SettingsGroupColors.Account,
                            title = stringResource(R.string.profiles_title),
                            subtitle = stringResource(R.string.settings_profiles_subtitle),
                            onClick = { profilesSheetOpen = true },
                        )
                        SettingsRow(
                            icon = Icons.Filled.Star,
                            badgeColor = SettingsGroupColors.Account,
                            title = stringResource(R.string.settings_premium),
                            subtitle = stringResource(R.string.settings_free_plan),
                            onClick = { premiumDialogOpen = true },
                        )
                        SettingsRow(
                            icon = Icons.Filled.RestartAlt,
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

            if (uid != null && securityVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_security))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Filled.Lock,
                        badgeColor = SettingsGroupColors.Security,
                        title = stringResource(R.string.pin_settings_title),
                        subtitle = stringResource(R.string.settings_pin_subtitle),
                        onClick = { pinSheetOpen = true },
                    )
                }

            }

            if (uid != null && notificationsVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_notifications))
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.Notifications,
                        badgeColor = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_push),
                        subtitle = stringResource(R.string.settings_push_subtitle),
                        checked = displayedPushEnabled,
                        enabled = true,
                        onCheckedChange = ::onTogglePush,
                    )
                    // Only reachable once push is actually on — configuring
                    // *which* alerts to send is meaningless before the device
                    // has even registered to receive any (see
                    // NotificationSettingsSheet's own doc comment).
                    if (displayedPushEnabled) {
                        SettingsRow(
                            icon = Icons.Filled.NotificationsActive,
                            badgeColor = SettingsGroupColors.Notifications,
                            title = stringResource(R.string.settings_notification_types),
                            subtitle = stringResource(R.string.settings_notification_types_subtitle),
                            onClick = { notifTypesSheetOpen = true },
                        )
                    }
                }
            }

            if (appearanceVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_appearance))
                RoundedChoiceSelector(
                    labels = listOf(
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                        stringResource(R.string.settings_theme_system),
                    ),
                    icons = listOf(Icons.Filled.LightMode, Icons.Filled.DarkMode, Icons.Filled.BrightnessAuto),
                    selectedIndex = when (themePreference) {
                        ThemePreference.LIGHT -> 0
                        ThemePreference.DARK -> 1
                        ThemePreference.SYSTEM -> 2
                    },
                    onSelect = { index ->
                        scope.launch {
                            app.settingsStore.setThemePreference(
                                when (index) {
                                    0 -> ThemePreference.LIGHT
                                    1 -> ThemePreference.DARK
                                    else -> ThemePreference.SYSTEM
                                }
                            )
                        }
                    },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                RoundedChoiceSelector(
                    labels = listOf(stringResource(R.string.settings_language_uk), stringResource(R.string.settings_language_en)),
                    selectedIndex = if (language == "en") 1 else 0,
                    onSelect = { scope.launch { app.settingsStore.setLanguage(if (it == 0) "uk" else "en") } },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = Icons.Filled.VisibilityOff,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_hide_amounts),
                        subtitle = stringResource(R.string.settings_hide_amounts_subtitle),
                        checked = hideAmounts,
                        enabled = true,
                        onCheckedChange = { scope.launch { app.settingsStore.setHideAmounts(it) } },
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.CloudDone,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_offline_cache),
                        subtitle = stringResource(if (privacyCacheEnabled) R.string.settings_offline_cache_on else R.string.settings_offline_cache_off),
                        checked = privacyCacheEnabled,
                        enabled = true,
                        onCheckedChange = { scope.launch { app.settingsStore.setPrivacyCacheEnabled(it) } },
                    )
                }
            }

            if (aboutVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_about))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Filled.Language,
                        badgeColor = SettingsGroupColors.About,
                        title = stringResource(R.string.settings_web),
                        subtitle = stringResource(R.string.settings_web_subtitle),
                        onClick = { openExternalUrl("https://maxtr-c238f.web.app") },
                    )
                    SettingsRow(
                        icon = Icons.Filled.Description,
                        badgeColor = SettingsGroupColors.About,
                        title = stringResource(R.string.terms_title),
                        subtitle = stringResource(R.string.settings_terms_subtitle),
                        onClick = { openExternalUrl("https://maxtr-c238f.web.app/terms.html") },
                    )
                    SettingsRow(
                        icon = Icons.Filled.PrivacyTip,
                        badgeColor = SettingsGroupColors.About,
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

            if (canEdit && financeVisible) {
                SettingsSectionLabel(stringResource(R.string.settings_finance))
                SettingsGroupCard {
                SettingsRow(
                    icon = Icons.Filled.AccountBalanceWallet,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.wallets_title),
                    subtitle = stringResource(R.string.settings_wallets_subtitle),
                    onClick = { walletsSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.AccountBalance,
                    badgeColor = monobankBadge(),
                    title = stringResource(R.string.settings_monobank),
                    subtitle = stringResource(R.string.settings_monobank_subtitle),
                    onClick = { monobankSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Category,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.categories_title),
                    subtitle = stringResource(R.string.settings_categories_subtitle),
                    onClick = { categoriesSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.CurrencyExchange,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.rates_title),
                    subtitle = stringResource(R.string.settings_rates_subtitle),
                    onClick = { ratesSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.PieChart,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.budgets_title),
                    subtitle = stringResource(R.string.settings_budgets_subtitle),
                    onClick = { budgetsSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Sell,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.tags_title),
                    subtitle = stringResource(R.string.settings_tags_subtitle),
                    onClick = { tagsSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Flag,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.goals_title),
                    subtitle = stringResource(R.string.settings_goals_subtitle),
                    onClick = { goalsSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.GridView,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.widgets_title),
                    subtitle = stringResource(R.string.settings_widgets_subtitle),
                    onClick = { widgetsSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Repeat,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.recurring_title),
                    subtitle = stringResource(R.string.settings_recurring_subtitle),
                    onClick = { recurringSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Tune,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.auto_rules_title),
                    subtitle = stringResource(R.string.settings_auto_rules_subtitle),
                    onClick = { autoRulesSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Style,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.shift_types_title),
                    subtitle = stringResource(R.string.settings_shift_types_subtitle),
                    onClick = { shiftTypesSheetOpen = true },
                )
                SettingsRow(
                    icon = Icons.Filled.Download,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.settings_csv_export),
                    subtitle = stringResource(R.string.settings_csv_export_subtitle),
                    onClick = { if (!csvBusy) csvExportLauncher.launch("rytm-finansy-${java.time.LocalDate.now()}.csv") },
                )
                SettingsRow(
                    icon = Icons.Filled.Upload,
                    badgeColor = SettingsGroupColors.Finance,
                    title = stringResource(R.string.settings_csv_import),
                    subtitle = stringResource(R.string.settings_csv_import_subtitle),
                    onClick = { if (!csvBusy) csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                )
                }
            }
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
            shape = RoundedCornerShape(RytmRadii.Sheet),
            icon = {
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                androidx.compose.material3.Button(enabled = !csvBusy && uid != null, onClick = {
                    val accountUid = activeProfileOwnerUid ?: uid ?: return@Button
                    scope.launch {
                        csvBusy = true
                        try {
                            csvRepository.import(accountUid, activeProfileId, preview.transactions)
                            pendingMessage = importSuccess
                            csvImportPreview = null
                        } catch (_: Exception) { pendingMessage = importSaveFailed }
                        finally { csvBusy = false }
                    }
                }, shape = RoundedCornerShape(RytmRadii.Control)) { Text(stringResource(R.string.settings_csv_import_action)) }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(enabled = !csvBusy, onClick = { csvImportPreview = null }, shape = RoundedCornerShape(RytmRadii.Control)) {
                    Text(stringResource(R.string.action_cancel))
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
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    onClick = {
                    pendingSignOut = false
                    scope.launch {
                        if (!privacyCacheEnabled) app.database.clearAllProfileScopedTables()
                        authViewModel.signOut()
                    }
                }) { Text(stringResource(R.string.settings_sign_out_action)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingSignOut = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (premiumDialogOpen) {
        AlertDialog(
            onDismissRequest = { premiumDialogOpen = false },
            icon = {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape).background(
                        Brush.linearGradient(listOf(OrangeDark, Pink)),
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
                        color = GreenDark,
                        title = stringResource(R.string.settings_premium_free_title),
                        subtitle = stringResource(R.string.settings_premium_free_body),
                    )
                    PremiumPerkRow(
                        icon = Icons.Filled.AccountBalanceWallet,
                        color = OrangeDark,
                        title = stringResource(R.string.settings_premium_banks_title),
                        subtitle = stringResource(R.string.settings_premium_banks_body),
                        badge = stringResource(R.string.settings_soon),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { premiumDialogOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(RytmRadii.Row),
                ) { Text(stringResource(R.string.action_done)) }
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
                Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)),
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
                            modifier = Modifier.clip(RoundedCornerShape(RytmRadii.Pill)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

/**
 * Lowercased search keywords for one settings section, resolved once per
 * locale. Keyed on the resolved strings themselves, so a locale change (which
 * re-runs stringResource) produces a new list and a config change is handled
 * without an explicit configuration key.
 */
@Composable
private fun rememberSettingsKeywords(vararg @StringRes ids: Int): List<String> {
    val resolved = ids.map { stringResource(it) }
    return remember(resolved) { resolved.map { it.lowercase() } }
}

@Composable
private fun SettingsSectionLabel(text: String) {
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
private fun SettingsGroupCard(content: @Composable SettingsRowScope.() -> Unit) {
    Card(shape = RoundedCornerShape(RytmRadii.Chart)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            SettingsRowScope.content()
        }
    }
}

private object SettingsRowScope

// Matches the PWA's .icon-badge: a circular badge tinted at ~16% of its own
// color, with the icon drawn in that full color — not a generic outline icon.
@Composable
private fun SettingsIconBadge(icon: ImageVector, color: Color) {
    Box(
        Modifier
            .size(RytmDimens.IconBadge)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(RytmDimens.IconBadgeIcon))
    }
}

/**
 * Badge colors are per GROUP, not per row.
 *
 * They used to be assigned per row from a grab-bag of hex literals, which
 * made them read as decoration rather than encoding: the same green marked
 * "Goals", "Recurring", "CSV export" and "Offline cache", and the same blue
 * marked "Rates", "Website", "CSV import" and "Shift types". Now the color
 * tells you which group a row belongs to, which is information the reader can
 * actually use while scanning.
 */
private object SettingsGroupColors {
    val Account = Cyan
    val Security = Slate
    val Notifications = PurpleDark
    val Appearance = Teal
    val About = BlueDark
    val Finance = GreenDark
}

/** Monobank's own brand black needs a lighter counterpart on dark surfaces:
 *  #111111 on surfaceContainer measured ~1.5:1, i.e. an invisible badge. */
@Composable
private fun monobankBadge(): Color =
    if (RytmSemantic.isDark) MonobankBrandDark else MonobankBrand

@Composable
private fun RoundedChoiceSelector(
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
                else if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
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
private fun SettingsRowScope.SettingsRow(
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
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsRowScope.SettingsToggleRow(
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
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
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
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.72f),
                    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    disabledUncheckedThumbColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
                    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
            ),
        )
    }
}
