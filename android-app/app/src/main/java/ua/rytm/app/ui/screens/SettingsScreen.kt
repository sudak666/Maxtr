package ua.rytm.app.ui.screens
import ua.rytm.app.ui.theme.RytmSpacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.layout
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import ua.rytm.app.data.CsvImportError
import ua.rytm.app.data.CsvImportErrorReason
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
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.icons.Public
import ua.rytm.app.ui.icons.AccountBalance
import ua.rytm.app.ui.icons.BrightnessAuto
import ua.rytm.app.ui.icons.Check
import ua.rytm.app.ui.icons.ChevronRight
import ua.rytm.app.ui.icons.Clear
import ua.rytm.app.ui.icons.DarkMode
import ua.rytm.app.ui.icons.Description
import ua.rytm.app.ui.icons.Language
import ua.rytm.app.ui.icons.LightMode
import ua.rytm.app.ui.icons.PrivacyTip
import androidx.compose.foundation.lazy.items
import ua.rytm.app.ui.components.fadingEdges
import androidx.core.net.toUri

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.R
import ua.rytm.app.ui.LocalCanEditProfile
import ua.rytm.app.data.local.ThemePreference
import ua.rytm.app.data.local.clearAllProfileScopedTables
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.LocalSnackbarHost
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.Category
import ua.rytm.app.ui.icons.CloudDone
import ua.rytm.app.ui.icons.CurrencyExchange
import ua.rytm.app.ui.icons.DeleteForever
import ua.rytm.app.ui.icons.Download
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.GridView
import ua.rytm.app.ui.icons.Groups
import ua.rytm.app.ui.icons.Lock
import ua.rytm.app.ui.icons.Logout
import ua.rytm.app.ui.icons.Notifications
import ua.rytm.app.ui.icons.NotificationsActive
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Repeat
import ua.rytm.app.ui.icons.RestartAlt
import ua.rytm.app.ui.icons.Search
import ua.rytm.app.ui.icons.Sell
import ua.rytm.app.ui.icons.Star
import ua.rytm.app.ui.icons.Style
import ua.rytm.app.ui.icons.Tune
import ua.rytm.app.ui.icons.Upload
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
    val app = ua.rytm.app.ui.rytmApp()
    val scope = rememberCoroutineScope()
    var openSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    var pendingSignOut by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteAccount by rememberSaveable { mutableStateOf(false) }
    var pendingResetProfile by rememberSaveable { mutableStateOf(false) }
    var settingsSearch by rememberSaveable { mutableStateOf("") }
    var settingsGroup by remember { mutableStateOf("all") }
    val settingsViewModel: SettingsViewModel = viewModel()
    val csvImportPreview = settingsViewModel.csvImportPreview
    val csvBusy = settingsViewModel.csvBusy
    val themePreference by app.settingsStore.themePreference.collectAsStateWithLifecycle(initialValue = ThemePreference.DARK)
    val showBalanceAfter by app.settingsStore.showBalanceAfter.collectAsStateWithLifecycle(initialValue = false)
    val privacyCacheEnabled by app.settingsStore.privacyCacheEnabled.collectAsStateWithLifecycle(initialValue = true)
    val language by app.settingsStore.language.collectAsStateWithLifecycle(initialValue = "uk")
    val uid = authViewModel.currentUser?.uid
    val session = ua.rytm.app.ui.LocalSession.current
    val activeProfileId = session.profileId
    val activeProfileOwnerUid = session.ownerUid
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
    val pushEnabled by (if (uid != null) app.settingsStore.isPushEnabled(uid) else flowOf(false)).collectAsStateWithLifecycle(initialValue = false)
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
        val aboutKeywords = rememberSettingsKeywords(R.string.settings_about, R.string.settings_web, R.string.settings_web_subtitle, R.string.settings_terms_row, R.string.settings_terms_subtitle, R.string.settings_privacy_row, R.string.settings_privacy_subtitle, R.string.settings_about_summary)
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
            verticalArrangement = Arrangement.spacedBy(RytmSpacing.xs),
        ) {
            item(key = "settings-title") {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = RytmSpacing.lg, bottom = RytmSpacing.sm))
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
            // One scrollable row like Finance's filters: a FlowRow left «Вигляд» alone on a second line.
            val groups = listOf(
                    "all" to stringResource(R.string.settings_group_all),
                    "account" to stringResource(R.string.settings_account),
                    "finance" to stringResource(R.string.settings_finance),
                    "security" to stringResource(R.string.settings_security),
                    "app" to stringResource(R.string.settings_appearance),
            )
            val fadeState = androidx.compose.foundation.lazy.rememberLazyListState()
            androidx.compose.foundation.lazy.LazyRow(
                state = fadeState,
                modifier = Modifier.fillMaxWidth().padding(vertical = RytmSpacing.sm).fadingEdges(fadeState),
                horizontalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
            ) {
                items(groups) { (key, label) ->
                    ua.rytm.app.ui.components.RytmFilterChip(
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
                    BankSuggestionsRow()
                    if (displayedPushEnabled) {
                        SettingsRow(
                            icon = RytmIcons.NotificationsActive,
                            badgeColor = SettingsGroupColors.Notifications,
                            title = stringResource(R.string.settings_notification_types),
                            subtitle = stringResource(R.string.settings_notification_types_subtitle),
                            onClick = { openSheet = SettingsSheet.NotificationTypes },
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
                    onSelect = settingsViewModel::setThemePreference,
                    modifier = Modifier.padding(vertical = RytmSpacing.sm),
                )
                RoundedChoiceSelector(
                    labels = listOf(stringResource(R.string.settings_language_uk), stringResource(R.string.settings_language_en)),
                    selectedIndex = if (language == "en") 1 else 0,
                    onSelect = { settingsViewModel.setLanguage(if (it == 0) "uk" else "en") },
                    modifier = Modifier.padding(bottom = RytmSpacing.sm),
                )
                SettingsGroupCard {
                    SettingsToggleRow(
                        icon = RytmIcons.AccountBalanceWallet,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_balance_after),
                        subtitle = stringResource(R.string.settings_balance_after_subtitle),
                        checked = showBalanceAfter,
                        enabled = true,
                        onCheckedChange = settingsViewModel::setShowBalanceAfter,
                    )
                    SettingsToggleRow(
                        icon = RytmIcons.CloudDone,
                        badgeColor = SettingsGroupColors.Appearance,
                        title = stringResource(R.string.settings_offline_cache),
                        subtitle = stringResource(if (privacyCacheEnabled) R.string.settings_offline_cache_on else R.string.settings_offline_cache_off),
                        checked = privacyCacheEnabled,
                        enabled = true,
                        onCheckedChange = settingsViewModel::setPrivacyCacheEnabled,
                    )
                }
            } }


            // One item per row (not one item for the whole 13-row card): composing
            // the whole group in a single frame cost up to ~40ms on a Galaxy A51
            // (Perfetto, live). SettingsCardSegment redraws the same rounded card
            // piecewise so it looks identical.
            if (canEdit && financeVisible) {
                item(key = "settings-finance-label") { SettingsSectionLabel(stringResource(R.string.settings_finance)) }
                val financeRows = listOf(
                    SettingsLink(RytmIcons.AccountBalanceWallet, R.string.wallets_title, R.string.settings_wallets_subtitle) { openSheet = SettingsSheet.Wallets },
                    SettingsLink(RytmIcons.Category, R.string.categories_title, R.string.settings_categories_subtitle) { openSheet = SettingsSheet.Categories },
                    SettingsLink(RytmIcons.CurrencyExchange, R.string.rates_title, R.string.settings_rates_subtitle) { openSheet = SettingsSheet.Rates },
                    SettingsLink(RytmIcons.PieChart, R.string.budgets_title, R.string.settings_budgets_subtitle) { openSheet = SettingsSheet.Budgets },
                    SettingsLink(RytmIcons.Sell, R.string.tags_title, R.string.settings_tags_subtitle) { openSheet = SettingsSheet.Tags },
                    SettingsLink(RytmIcons.Flag, R.string.goals_title, R.string.settings_goals_subtitle) { openSheet = SettingsSheet.Goals },
                    SettingsLink(RytmIcons.GridView, R.string.widgets_title, R.string.settings_widgets_subtitle) { openSheet = SettingsSheet.Widgets },
                    SettingsLink(RytmIcons.Repeat, R.string.recurring_title, R.string.settings_recurring_subtitle) { openSheet = SettingsSheet.Recurring },
                    SettingsLink(RytmIcons.Tune, R.string.auto_rules_title, R.string.settings_auto_rules_subtitle) { openSheet = SettingsSheet.AutoRules },
                    SettingsLink(RytmIcons.Style, R.string.shift_types_title, R.string.settings_shift_types_subtitle) { openSheet = SettingsSheet.ShiftTypes },
                    SettingsLink(RytmIcons.Download, R.string.settings_csv_export, R.string.settings_csv_export_subtitle) {
                        if (!csvBusy) csvExportLauncher.launch("rytm-finansy-${java.time.LocalDate.now()}.csv")
                    },
                    SettingsLink(RytmIcons.Upload, R.string.settings_csv_import, R.string.settings_csv_import_subtitle) {
                        if (!csvBusy) csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain"))
                    },
                )
                financeRows.forEachIndexed { i, row ->
                    val position = when (i) { 0 -> SegmentPosition.First; financeRows.lastIndex -> SegmentPosition.Last; else -> SegmentPosition.Middle }
                    item(key = "settings-finance-${row.title}") { SettingsCardSegment(position) {
                        SettingsRow(
                            icon = row.icon,
                            badgeColor = SettingsGroupColors.Neutral,
                            title = stringResource(row.title),
                            subtitle = stringResource(row.subtitle),
                            onClick = row.onClick,
                        )
                    } }
                }
            }

            // "Про застосунок" goes last, after every functional group.
            // P5 #8: destructive actions sit in their own group at the bottom
            // instead of between Profiles and Backups in "Акаунт".
            if (uid != null && accountVisible) { item(key = "settings-danger") {
                SettingsSectionLabel(stringResource(R.string.settings_danger_zone))
                SettingsGroupCard {
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
            } }

            if (aboutVisible) { item(key = "settings-about") {
                AboutSection(onOpenUrl = ::openExternalUrl)
            } }
            // Small version line at the very end, like most apps — "which version do you
            // have?" is the first support question.
            if (aboutVisible) { item(key = "settings-version") {
                Text(
                    stringResource(R.string.settings_version, ua.rytm.app.BuildConfig.VERSION_NAME, ua.rytm.app.BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = RytmSpacing.sm),
                )
            } }
            if (!accountVisible && !securityVisible && !notificationsVisible && !appearanceVisible && !aboutVisible && !financeVisible) {
                item(key = "settings-search-empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(RytmSpacing.sm),
                ) {
                    Icon(RytmIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.settings_search_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_search_empty_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                }
            }
        }
    }

    if (uid != null) {
        SettingsManagerSheets(
            open = openSheet,
            onClose = { openSheet = null },
            uid = uid,
            dataUid = activeProfileOwnerUid ?: uid,
            profileId = activeProfileId,
            isSharedProfile = activeProfileOwnerUid != null,
            settingsViewModel = settingsViewModel,
            onProfileSwitched = { pendingMessage = resources.getString(R.string.settings_profile_switched) },
        )
    }

    csvImportPreview?.let { preview ->
        CsvImportPreviewDialog(
            preview = preview,
            busy = csvBusy,
            canImport = uid != null,
            onConfirm = { uid?.let { settingsViewModel.confirmImport(activeProfileOwnerUid ?: it, activeProfileId) } },
            onDismiss = settingsViewModel::dismissImportPreview,
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
