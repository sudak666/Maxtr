package ua.rytm.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ua.rytm.app.RytmApplication
import ua.rytm.app.ui.screens.auth.AuthViewModel
import ua.rytm.app.ui.screens.finance.BudgetsManagerSheet
import ua.rytm.app.ui.screens.finance.CategoriesManagerSheet
import ua.rytm.app.ui.screens.finance.RecurringManagerSheet
import ua.rytm.app.ui.screens.finance.TagsManagerSheet
import ua.rytm.app.ui.screens.finance.WalletsManagerSheet
import ua.rytm.app.ui.screens.pin.PinSettingsSheet
import ua.rytm.app.ui.screens.pin.PinViewModel
import ua.rytm.app.ui.screens.shifts.ShiftTypesManagerSheet

// "Гаманці"/"Категорії"/"Типи змін"/"Бюджети"/"Теги"/"Регулярні платежі"/
// "Push-сповіщення"/"Вигляд" (тема)/"Акаунт" (вихід)/"Безпека" (PIN+біометрія)
// are real so far — the rest of the PWA's Settings IA is deliberately not
// built yet, disclosed honestly rather than faked:
//   - Мова (uk/en toggle): blocked on a real prerequisite, not just
//     unstarted — every screen in this app hardcodes Ukrainian text
//     directly rather than going through string resources (see strings.xml,
//     which only covers nav labels). A real language switch needs that
//     whole strings.xml migration first (CLAUDE.md §3 improvement #12),
//     which is its own multi-session effort, not a corner of this step.
//   - Profiles, account deletion: Firebase SDK is wired (step 12), sign-in
//     is real (step 13), Firestore cold-sync covers 9 domains (steps 14-24,
//     26), and Push now has a real client (step 27) — the remaining
//     blocker for these two rows is net-new feature work (multi-profile
//     switching UI, an account-deletion flow), not a missing prerequisite.
//   - Push is a single on/off toggle, not the PWA's 3 separate granular
//     alert-type rows with a reminder-time picker — see PushRepository's
//     own doc comment for why this is a disclosed, honest scope decision
//     for this step rather than a corner cut silently.
// See ANDROID_MIGRATION.md's "Chesno not done" convention.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(authViewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as RytmApplication
    val scope = rememberCoroutineScope()
    var walletsSheetOpen by remember { mutableStateOf(false) }
    var categoriesSheetOpen by remember { mutableStateOf(false) }
    var budgetsSheetOpen by remember { mutableStateOf(false) }
    var tagsSheetOpen by remember { mutableStateOf(false) }
    var recurringSheetOpen by remember { mutableStateOf(false) }
    var shiftTypesSheetOpen by remember { mutableStateOf(false) }
    var pinSheetOpen by remember { mutableStateOf(false) }
    val darkTheme by app.settingsStore.isDarkTheme.collectAsState(initial = true)
    val uid = authViewModel.currentUser?.uid

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingMessage) {
        pendingMessage?.let { snackbarHostState.showSnackbar(it); pendingMessage = null }
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
                if (target) app.pushRepository.enable(accountUid) else app.pushRepository.disable(accountUid)
                app.settingsStore.setPushEnabled(accountUid, target)
                pendingMessage = if (target) "Push-сповіщення увімкнено" else "Push-сповіщення вимкнено"
            } catch (e: Exception) {
                pendingMessage = "Не вдалося змінити налаштування сповіщень"
            } finally {
                pushBusy = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) applyPushEnabled(true) else pendingMessage = "Дозвіл на сповіщення не надано"
    }

    fun onTogglePush(target: Boolean) {
        if (uid == null || pushBusy) return
        val needsRuntimePermission = target && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else applyPushEnabled(target)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp)) {
            Text("Налаштування", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SettingsSectionLabel("Акаунт")
            SettingsRow(
                title = authViewModel.currentUser?.email ?: authViewModel.currentUser?.displayName ?: "Ваш акаунт",
                subtitle = "Натисніть, щоб вийти",
                onClick = authViewModel::signOut,
            )

            if (uid != null) {
                SettingsSectionLabel("Безпека")
                SettingsRow(
                    title = "PIN-код",
                    subtitle = "Захист застосунку кодом і біометрією",
                    onClick = { pinSheetOpen = true },
                )

                SettingsSectionLabel("Сповіщення")
                SettingsToggleRow(
                    title = "Push-сповіщення",
                    subtitle = "Нагадування, бюджет, регулярні платежі та розрахунки на цьому пристрої",
                    checked = pushEnabled,
                    enabled = !pushBusy,
                    onCheckedChange = ::onTogglePush,
                )
            }

            SettingsSectionLabel("Вигляд")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SegmentedButton(
                    selected = !darkTheme,
                    onClick = { scope.launch { app.settingsStore.setDarkTheme(false) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Filled.LightMode, contentDescription = null) },
                ) { Text("Світла") }
                SegmentedButton(
                    selected = darkTheme,
                    onClick = { scope.launch { app.settingsStore.setDarkTheme(true) } },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                ) { Text("Темна") }
            }

            SettingsSectionLabel("Фінанси")
            SettingsRow(
                title = "Гаманці",
                subtitle = "Картки, готівка та інші рахунки",
                onClick = { walletsSheetOpen = true },
            )
            SettingsRow(
                title = "Категорії",
                subtitle = "Власні категорії доходів і витрат",
                onClick = { categoriesSheetOpen = true },
            )
            SettingsRow(
                title = "Бюджети",
                subtitle = "Місячні ліміти для категорій витрат",
                onClick = { budgetsSheetOpen = true },
            )
            SettingsRow(
                title = "Теги",
                subtitle = "Мітки для операцій",
                onClick = { tagsSheetOpen = true },
            )
            SettingsRow(
                title = "Регулярні платежі",
                subtitle = "Автоматичне створення операцій за розкладом",
                onClick = { recurringSheetOpen = true },
            )
            SettingsRow(
                title = "Типи змін",
                subtitle = "Оплата, години та кольори для графіка змін",
                onClick = { shiftTypesSheetOpen = true },
            )
        }
    }

    if (walletsSheetOpen) {
        WalletsManagerSheet(repository = app.financeRepository, onDismiss = { walletsSheetOpen = false })
    }
    if (categoriesSheetOpen) {
        CategoriesManagerSheet(repository = app.financeRepository, onDismiss = { categoriesSheetOpen = false })
    }
    if (budgetsSheetOpen) {
        BudgetsManagerSheet(repository = app.financeRepository, onDismiss = { budgetsSheetOpen = false })
    }
    if (tagsSheetOpen) {
        TagsManagerSheet(repository = app.financeRepository, onDismiss = { tagsSheetOpen = false })
    }
    if (recurringSheetOpen) {
        RecurringManagerSheet(repository = app.financeRepository, onDismiss = { recurringSheetOpen = false })
    }
    if (shiftTypesSheetOpen) {
        ShiftTypesManagerSheet(repository = app.shiftsRepository, onDismiss = { shiftTypesSheetOpen = false })
    }
    if (pinSheetOpen && uid != null) {
        // Same Activity-scoped viewModelStoreOwner as MainActivity's own PinViewModel
        // — see that call site's comment for why they must resolve to one instance.
        val activity = context as androidx.fragment.app.FragmentActivity
        val pinViewModel: PinViewModel = viewModel(factory = PinViewModel.factory(app.pinStore, uid), viewModelStoreOwner = activity)
        PinSettingsSheet(pinViewModel, onDismiss = { pinSheetOpen = false })
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
