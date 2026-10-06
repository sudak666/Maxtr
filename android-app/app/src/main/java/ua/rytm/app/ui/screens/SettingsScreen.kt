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

// "Гаманці"/"Категорії"/"Типи змін"/"Бюджети"/"Теги"/"Регулярні платежі"/
// "Push-сповіщення" (+ granular "Типи сповіщень")/"Профілі" (own+shared,
// invite/join/leave/roles — steps 30/32/33)/"Вигляд" (тема)/"Акаунт" (вихід
// + видалення, step 35)/"Безпека" (PIN+біометрія)/"Мова" (uk/en toggle) are
// real. The "Мова" entry below used to carry a note claiming this was
// blocked on a full strings.xml migration — stale as of this comment: the
// whole app is on Android string resources (values/ + values-en/, near
// full parity) and MainActivity observes the language setting and calls
// AppCompatDelegate.setApplicationLocales() (see ui/AppLanguage.kt) — the
// toggle genuinely switches the whole app's language, confirmed live on an
// emulator with an English system locale. See ANDROID_MIGRATION.md's
// "Chesno not done" convention for what's still genuinely unbuilt here.
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
    var widgetsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var shiftTypesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pinSheetOpen by rememberSaveable { mutableStateOf(false) }
    var notifTypesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var profilesSheetOpen by rememberSaveable { mutableStateOf(false) }
    var backupsSheetOpen by rememberSaveable { mutableStateOf(false) }
    var pendingSignOut by rememberSaveable { mutableStateOf(false) }
    var premiumDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var pendingResetProfile by rememberSaveable { mutableStateOf(false) }
    var settingsSearch by rememberSaveable { mutableStateOf("") }
    var settingsGroup by remember { mutableStateOf("all") }
    val settingsViewModel: SettingsViewModel = viewModel()
    val csvImportPreview = settingsViewModel.csvImportPreview
    val csvBusy = settingsViewModel.csvBusy
    val themePreference by app.settingsStore.themePreference.collectAsState(initial = ThemePreference.DARK)
    val hideAmounts by app.settingsStore.hideAmounts.collectAsState(initial = false)
    val privacyCacheEnabled by app.settingsStore.privacyCacheEnabled.collectAsState(initial = true)
    val language by app.settingsStore.language.collectAsState(initial = "uk")
    val uid = authViewModel.currentUser?.uid
    val activeProfileId by (if (uid != null) app.activeProfileStore.activeProfileId(uid) else flowOf(DEFAULT_PROFILE_ID)).collectAsState(initial = DEFAULT_PROFILE_ID)
    val activeProfileOwnerUid by (if (uid != null) app.activeProfileStore.activeProfileOwnerUid(uid) else flowOf(null)).collectAsState(initial = null)
    val pushPermissionDeniedMessage = stringResource(R.string.settings_push_permission_denied)
    val linkOpenFailedMessage = stringResource(R.string.settings_link_open_failed)

    // Falls back to a local host only outside the nav graph (previews/tests).
    val ownHost = remember { SnackbarHostState() }
    val snackbarHostState = LocalSnackbarHost.current ?: ownHost
    var pendingMessage by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { snackbarHostState.showSnackbar(it); pendingMessage = null }
    }
    LaunchedEffect(settingsViewModel.message) {
        when (val m = settingsViewModel.message) {
            is SettingsMessage.Text -> pendingMessage = if (m.arg != null) resources.getString(m.res, m.arg) else resources.getString(m.res)
            is SettingsMessage.Plural -> pendingMessage = resources.getQuantityString(m.res, m.count, m.count)
            null -> return@LaunchedEffect
        }
        settingsViewModel.consumeMessage()
    }
    LaunchedEffect(settingsViewModel.resetProfileSucceeded) {
        if (settingsViewModel.resetProfileSucceeded) { pendingResetProfile = false; settingsViewModel.consumeResetSucceeded() }
    }
    LaunchedEffect(authViewModel.errorMessageRes) {
        authViewModel.errorMessageRes?.let { snackbarHostState.showSnackbar(resources.getString(it)); authViewModel.consumeError() }
    }

    val pushBusy = settingsViewModel.pushBusy
    val pushEnabled by (if (uid != null) app.settingsStore.isPushEnabled(uid) else flowOf(false)).collectAsState(initial = false)
    val displayedPushEnabled = settingsViewModel.pendingPushEnabled ?: pushEnabled
    LaunchedEffect(pushEnabled) { settingsViewModel.settlePendingPush(pushEnabled) }

    // Only relevant on API 33+ — earlier versions never require a runtime
    // notification permission at all.
    fun applyPushEnabled(target: Boolean) {
        val accountUid = uid ?: return
        settingsViewModel.setPushEnabled(accountUid, activeProfileOwnerUid ?: accountUid, activeProfileId, target)
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
        if (uri != null) settingsViewModel.exportCsv(uri, language)
    }
    val csvImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) settingsViewModel.readImport(uri)
    }

    Scaffold(
        containerColor = Color.Transparent, // background comes from MainActivity's Surface (overdraw)
        snackbarHost = { if (LocalSnackbarHost.current == null) SnackbarHost(ownHost, Modifier.padding(bottom = RytmDimens.BottomContentClearance)) },
    ) { innerPadding ->
        // Real bug found during step 39's visual-parity pass: this Column had
        // no scroll modifier at all, so on a real device everything past
        // "Категорії" (Бюджети/Теги/Регулярні платежі/Типи змін) was
        // permanently unreachable — not a styling gap, a genuine dead end.
        //
        // LazyColumn since the design-audit stage-4 follow-up: this screen
        // is ~40 rows across 6 groups, previously all composed eagerly via
        // Column+verticalScroll regardless of scroll position or the
        // search/group filter above. Wrapping each section in its own
        // item{} lets Compose skip composing/measuring whatever's off-screen
        // or filtered out, instead of paying for the whole screen on every
        // recomposition (see CategoryColor.kt's stage-4 fix for the same
        // class of problem on the Finance list). Purely mechanical — same
        // content, same order, same conditionals, just wrapped.
        // Search index, built once per locale instead of on every keystroke.
        //
        // This used to re-run six localizedSettingsStrings() calls on every
        // recomposition — ~80 stringResource lookups — and then lowercase
        // all of them again inside sectionVisible(), i.e. every single
        // character typed into the search box paid for the whole set. The
        // strings only change when the locale does. Hoisted above the
        // LazyColumn (rather than left inline, as when this was a plain
        // Column) because LazyListScope's item{}-building lambda isn't
        // itself a @Composable context — only the bodies of item{} blocks
        // are, so remember()/derivedStateOf()/stringResource() calls that
        // sit directly between item{} calls don't compile.
        val accountKeywords = rememberSettingsKeywords(
            R.string.settings_account, R.string.settings_sign_out, R.string.settings_sign_out_subtitle,
            R.string.profiles_title, R.string.settings_profiles_subtitle, R.string.settings_premium,
            R.string.settings_backups_title, R.string.settings_backups_subtitle,
            R.string.settings_free_plan, R.string.settings_reset_profile, R.string.settings_reset_profile_subtitle,
            R.string.settings_delete_account, R.string.settings_delete_account_subtitle,
        )
        val securityKeywords = rememberSettingsKeywords(R.string.settings_security, R.string.pin_settings_title, R.string.settings_pin_subtitle)
        val notificationsKeywords = rememberSettingsKeywords(R.string.settings_notifications, R.string.settings_push, R.string.settings_push_subtitle, R.string.settings_notification_types, R.string.settings_notification_types_subtitle)
        val appearanceKeywords = rememberSettingsKeywords(R.string.settings_appearance, R.string.settings_theme, R.string.settings_theme_light, R.string.settings_theme_dark, R.string.settings_theme_system)
        val aboutKeywords = rememberSettingsKeywords(R.string.settings_about, R.string.settings_web, R.string.settings_web_subtitle, R.string.terms_title, R.string.settings_terms_subtitle, R.string.privacy_title, R.string.settings_privacy_subtitle, R.string.settings_about_summary)
        val financeKeywords = rememberSettingsKeywords(
            R.string.settings_finance, R.string.wallets_title, R.string.settings_wallets_subtitle,
            R.string.rates_title,
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

        LazyColumn(
            // Was Modifier.padding(bottom = BottomContentClearance) — that
            // shrinks the LazyColumn's own box/clip bounds by 112dp instead
            // of reserving scrollable space past the last item the way
            // FinanceScreen's contentPadding does. The visible effect:
            // Settings' list content sat snug against its own (smaller)
            // box edge rather than genuinely scrolling clear of the floating
            // nav bar, reading as "attached to" rather than "floating over"
            // content — reported live, the one screen that didn't feel like
            // the others. Top clearance stays a plain Modifier padding
            // (nothing overlays the top edge the way the nav bar overlays
            // the bottom, so there's no equivalent need to keep that area
            // in the scrollable/clip region).
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = RytmDimens.ContentHorizontal,
                end = RytmDimens.ContentHorizontal,
                bottom = innerPadding.calculateBottomPadding() + RytmDimens.BottomContentClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "settings-title") {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }

            item(key = "settings-search") {
            ua.rytm.app.ui.components.RytmSearchField(
                value = settingsSearch,
                onValueChange = { settingsSearch = it },
                prefix = stringResource(R.string.finance_search_prefix),
                hints = listOf(
                    stringResource(R.string.settings_search_what_wallets),
                    stringResource(R.string.settings_search_what_categories),
                    stringResource(R.string.settings_search_what_notifications),
                    stringResource(R.string.settings_search_what_pin),
                    stringResource(R.string.settings_search_what_theme),
                ),
                clearDescription = stringResource(R.string.settings_clear_search),
            )
            }
            item(key = "settings-group-filter") {
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
            }

            if (accountVisible) { item(key = "settings-account") {
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
                        icon = RytmIcons.Logout,
                        badgeColor = SettingsGroupColors.Account,
                        title = stringResource(R.string.settings_sign_out),
                        subtitle = stringResource(R.string.settings_sign_out_subtitle),
                        onClick = { pendingSignOut = true },
                    )
                    if (uid != null) {
                        SettingsRow(
                            icon = RytmIcons.Groups,
                            badgeColor = SettingsGroupColors.Account,
                            title = stringResource(R.string.profiles_title),
                            subtitle = stringResource(R.string.settings_profiles_subtitle),
                            onClick = { profilesSheetOpen = true },
                        )
                        SettingsRow(
                            icon = RytmIcons.CloudDone,
                            badgeColor = SettingsGroupColors.Account,
                            title = stringResource(R.string.settings_backups_title),
                            subtitle = stringResource(R.string.settings_backups_subtitle),
                            onClick = {
                                if (activeProfileOwnerUid != null) {
                                    pendingMessage = resources.getString(R.string.backups_shared_unavailable)
                                } else {
                                    backupsSheetOpen = true
                                }
                            },
                        )
                        SettingsRow(
                            icon = RytmIcons.Star,
                            badgeColor = SettingsGroupColors.Account,
                            title = stringResource(R.string.settings_premium),
                            subtitle = stringResource(R.string.settings_free_plan),
                            onClick = { premiumDialogOpen = true },
                        )
                        SettingsRow(
                            icon = RytmIcons.RestartAlt,
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
                            icon = RytmIcons.DeleteForever,
                            badgeColor = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.settings_delete_account),
                            subtitle = stringResource(R.string.settings_delete_account_subtitle),
                            onClick = { pendingDeleteAccount = true },
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } }

            if (uid != null && securityVisible) { item(key = "settings-security") {
                SettingsSectionLabel(stringResource(R.string.settings_security))
                SettingsGroupCard {
                    SettingsRow(
                        icon = RytmIcons.Lock,
                        badgeColor = SettingsGroupColors.Security,
                        title = stringResource(R.string.pin_settings_title),
                        subtitle = stringResource(R.string.settings_pin_subtitle),
                        onClick = { pinSheetOpen = true },
                    )
                }

            } }

            if (uid != null && notificationsVisible) { item(key = "settings-notifications") {
                SettingsSectionLabel(stringResource(R.string.settings_notifications))
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = RytmIcons.Notifications,
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
                            icon = RytmIcons.NotificationsActive,
                            badgeColor = SettingsGroupColors.Notifications,
                            title = stringResource(R.string.settings_notification_types),
                            subtitle = stringResource(R.string.settings_notification_types_subtitle),
                            onClick = { notifTypesSheetOpen = true },
                        )
                    }
                }
            } }

            if (appearanceVisible) { item(key = "settings-appearance") {
                SettingsSectionLabel(stringResource(R.string.settings_appearance))
                // A 3-way horizontal segmented control with icon+text (the
                // previous RoundedChoiceSelector here) squeezes "Системна"
                // into an equal third of the row width, which truncates to
                // "Систе..." at normal font scale on real devices — reported
                // live. Google's own Settings app (and most Android system
                // pickers) solves exactly this by giving each choice a full-
                // width row instead of splitting the row three ways: a radio
                // list. Every label always gets the whole card width, so it
                // can never truncate regardless of translation length or the
                // user's font-scale setting.
                ThemePreferenceSelector(
                    selected = themePreference,
                    onSelect = { pref -> scope.launch { app.settingsStore.setThemePreference(pref) } },
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
                        icon = RytmIcons.VisibilityOff,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_hide_amounts),
                        subtitle = stringResource(R.string.settings_hide_amounts_subtitle),
                        checked = hideAmounts,
                        enabled = true,
                        onCheckedChange = { scope.launch { app.settingsStore.setHideAmounts(it) } },
                    )
                    SettingsToggleRow(
                        icon = RytmIcons.CloudDone,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_offline_cache),
                        subtitle = stringResource(if (privacyCacheEnabled) R.string.settings_offline_cache_on else R.string.settings_offline_cache_off),
                        checked = privacyCacheEnabled,
                        enabled = true,
                        onCheckedChange = { scope.launch { app.settingsStore.setPrivacyCacheEnabled(it) } },
                    )
                }
            } }

            if (aboutVisible) { item(key = "settings-about") {
                SettingsSectionLabel(stringResource(R.string.settings_about))
                SettingsGroupCard {
                    SettingsRow(
                        icon = RytmIcons.Language,
                        badgeColor = SettingsGroupColors.About,
                        title = stringResource(R.string.settings_web),
                        subtitle = stringResource(R.string.settings_web_subtitle),
                        onClick = { openExternalUrl("https://maxtr-c238f.web.app") },
                    )
                    // privacy.html/terms.html have no i18n of their own (standalone
                    // static pages, not part of the app's string-resource system) —
                    // legal_docs_en picks the -en.html sibling instead of silently
                    // always opening the Ukrainian original regardless of app language.
                    val legalDocsEn = androidx.compose.ui.res.booleanResource(R.bool.legal_docs_en)
                    SettingsRow(
                        icon = RytmIcons.Description,
                        badgeColor = SettingsGroupColors.About,
                        title = stringResource(R.string.terms_title),
                        subtitle = stringResource(R.string.settings_terms_subtitle),
                        onClick = { openExternalUrl(if (legalDocsEn) "https://maxtr-c238f.web.app/terms-en.html" else "https://maxtr-c238f.web.app/terms.html") },
                    )
                    SettingsRow(
                        icon = RytmIcons.PrivacyTip,
                        badgeColor = SettingsGroupColors.About,
                        title = stringResource(R.string.privacy_title),
                        subtitle = stringResource(R.string.settings_privacy_subtitle),
                        onClick = { openExternalUrl(if (legalDocsEn) "https://maxtr-c238f.web.app/privacy-en.html" else "https://maxtr-c238f.web.app/privacy.html") },
                    )
                }
                Text(
                    stringResource(R.string.settings_about_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            } }

            // One item per row (not one item for the whole 13-row card): composing
            // the whole group in a single frame cost up to ~40ms on a Galaxy A51
            // (Perfetto, live). SettingsCardSegment redraws the same rounded card
            // piecewise so it looks identical.
            if (canEdit && financeVisible) {
                item(key = "settings-finance-label") { SettingsSectionLabel(stringResource(R.string.settings_finance)) }
                item(key = "settings-finance-0") { SettingsCardSegment(SegmentPosition.First) {
                    SettingsRow(
                        icon = RytmIcons.AccountBalanceWallet,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.wallets_title),
                        subtitle = stringResource(R.string.settings_wallets_subtitle),
                        onClick = { walletsSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-2") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Category,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.categories_title),
                        subtitle = stringResource(R.string.settings_categories_subtitle),
                        onClick = { categoriesSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-3") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.CurrencyExchange,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.rates_title),
                        subtitle = stringResource(R.string.settings_rates_subtitle),
                        onClick = { ratesSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-4") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.PieChart,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.budgets_title),
                        subtitle = stringResource(R.string.settings_budgets_subtitle),
                        onClick = { budgetsSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-5") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Sell,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.tags_title),
                        subtitle = stringResource(R.string.settings_tags_subtitle),
                        onClick = { tagsSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-6") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Flag,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.goals_title),
                        subtitle = stringResource(R.string.settings_goals_subtitle),
                        onClick = { goalsSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-7") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.GridView,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.widgets_title),
                        subtitle = stringResource(R.string.settings_widgets_subtitle),
                        onClick = { widgetsSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-8") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Repeat,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.recurring_title),
                        subtitle = stringResource(R.string.settings_recurring_subtitle),
                        onClick = { recurringSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-9") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Tune,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.auto_rules_title),
                        subtitle = stringResource(R.string.settings_auto_rules_subtitle),
                        onClick = { autoRulesSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-10") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Style,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.shift_types_title),
                        subtitle = stringResource(R.string.settings_shift_types_subtitle),
                        onClick = { shiftTypesSheetOpen = true },
                    )
                } }
                item(key = "settings-finance-11") { SettingsCardSegment(SegmentPosition.Middle) {
                    SettingsRow(
                        icon = RytmIcons.Download,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.settings_csv_export),
                        subtitle = stringResource(R.string.settings_csv_export_subtitle),
                        onClick = { if (!csvBusy) csvExportLauncher.launch("rytm-finansy-${java.time.LocalDate.now()}.csv") },
                    )
                } }
                item(key = "settings-finance-12") { SettingsCardSegment(SegmentPosition.Last) {
                    SettingsRow(
                        icon = RytmIcons.Upload,
                        badgeColor = SettingsGroupColors.Neutral,
                        title = stringResource(R.string.settings_csv_import),
                        subtitle = stringResource(R.string.settings_csv_import_subtitle),
                        onClick = { if (!csvBusy) csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain")) },
                    )
                } }
            }
            if (!accountVisible && !securityVisible && !notificationsVisible && !appearanceVisible && !aboutVisible && !financeVisible) {
                item(key = "settings-search-empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(RytmIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.settings_search_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_search_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
    if (shiftTypesSheetOpen && uid != null) {
        ShiftTypesManagerSheet(repository = app.shiftsRepository, uid = activeProfileOwnerUid ?: uid, profileId = activeProfileId, onDismiss = { shiftTypesSheetOpen = false })
    }
    if (notifTypesSheetOpen && uid != null) {
        NotificationSettingsSheet(uid = activeProfileOwnerUid ?: uid, repository = app.pushRepository, profileId = activeProfileId, onDismiss = { notifTypesSheetOpen = false })
    }
    if (backupsSheetOpen && uid != null && activeProfileOwnerUid == null) {
        BackupsSheet(uid = uid, profileId = activeProfileId, viewModel = settingsViewModel, onDismiss = { backupsSheetOpen = false })
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
        AlertDialog(
            onDismissRequest = settingsViewModel::dismissImportPreview,
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
                androidx.compose.material3.Button(enabled = !csvBusy && uid != null, onClick = {
                    val accountUid = activeProfileOwnerUid ?: uid ?: return@Button
                    settingsViewModel.confirmImport(accountUid, activeProfileId)
                }, shape = RoundedCornerShape(RytmRadii.Control)) { Text(stringResource(R.string.settings_csv_import_action)) }
            },
            dismissButton = {
                androidx.compose.material3.OutlinedButton(enabled = !csvBusy, onClick = settingsViewModel::dismissImportPreview, shape = RoundedCornerShape(RytmRadii.Control)) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (pendingDeleteAccount) {
        ua.rytm.app.ui.components.RytmDestructiveConfirm(
            title = stringResource(R.string.settings_delete_account),
            body = stringResource(R.string.settings_delete_account_body),
            confirmLabel = stringResource(R.string.action_delete),
            busy = authViewModel.isDeletingAccount,
            busyLabel = stringResource(R.string.settings_deleting_account),
            onConfirm = { authViewModel.deleteAccount(context) },
            onDismiss = { pendingDeleteAccount = false },
        )
    }
    if (pendingSignOut) {
        ua.rytm.app.ui.components.RytmDestructiveConfirm(
            title = stringResource(R.string.settings_sign_out_title),
            body = stringResource(R.string.settings_sign_out_body),
            confirmLabel = stringResource(R.string.settings_sign_out_action),
            onConfirm = {
                pendingSignOut = false
                scope.launch {
                    if (!privacyCacheEnabled) app.database.clearAllProfileScopedTables()
                    authViewModel.signOut(context)
                }
            },
            onDismiss = { pendingSignOut = false },
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
                    Icon(RytmIcons.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            },
            title = { Text(stringResource(R.string.settings_premium_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_premium_body))
                    PremiumPerkRow(
                        icon = RytmIcons.CheckCircle,
                        color = GreenDark,
                        title = stringResource(R.string.settings_premium_free_title),
                        subtitle = stringResource(R.string.settings_premium_free_body),
                    )
                    PremiumPerkRow(
                        icon = RytmIcons.TrendingUp,
                        color = GreenDark,
                        title = stringResource(R.string.settings_premium_forecast_title),
                        subtitle = stringResource(R.string.settings_premium_forecast_body),
                    )
                    PremiumPerkRow(
                        icon = RytmIcons.CloudDone,
                        color = GreenDark,
                        title = stringResource(R.string.settings_premium_backup_title),
                        subtitle = stringResource(R.string.settings_premium_backup_body),
                    )
                    PremiumPerkRow(
                        icon = RytmIcons.GridView,
                        color = GreenDark,
                        title = stringResource(R.string.settings_premium_widget_title),
                        subtitle = stringResource(R.string.settings_premium_widget_body),
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
        ua.rytm.app.ui.components.RytmDestructiveConfirm(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_body),
            confirmLabel = stringResource(R.string.settings_reset_action),
            busy = settingsViewModel.resetProfileBusy,
            busyLabel = stringResource(R.string.settings_resetting),
            onConfirm = {
                settingsViewModel.resetProfile(uid, activeProfileId, activeProfileOwnerUid)
            },
            onDismiss = { pendingResetProfile = false },
        )
    }
    // The dialog above closes itself once the account is actually gone
    // (authViewModel.currentUser flips to null via the AuthStateListener,
    // which unmounts this whole screen behind the login screen) — no
    // explicit onSuccess callback needed.
}
