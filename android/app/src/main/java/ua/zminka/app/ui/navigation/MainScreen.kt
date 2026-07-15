package ua.zminka.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import ua.zminka.app.data.repository.PushRepository
import ua.zminka.app.ui.debt.DebtScreen
import ua.zminka.app.ui.finance.FinanceScreen
import ua.zminka.app.ui.settings.SettingsScreen
import ua.zminka.app.ui.shifts.ShiftsScreen
import ua.zminka.app.ui.shopping.ShoppingScreen

// Same 4 primary tabs as the web app's bottom nav (see CLAUDE.md's "Mobile
// UI redesign" section: Settings was dropped from the bottom bar there too
// and moved to a topbar gear icon — kept in sync here so the two clients'
// navigation model matches, not just their per-tab feature set). SETTINGS
// stays a real enum case (still one of the screens `when` switches over),
// it's just not one of the bottomBar's NavigationBarItems below.
private enum class MainTab(val label: String) {
    FINANCE("Фінанси"),
    SHIFTS("Зміни"),
    DEBT("Розрахунки"),
    SHOPPING("Покупки"),
    SETTINGS("Налаштування"),
}

/** All 5 of the web app's tabs are represented (Settings via the topbar gear icon, not the bottom nav — see MainTab's doc comment), though Settings itself is still MVP-thin (see SettingsScreen's own doc comment). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onSignedOut: () -> Unit, pushRepo: PushRepository = PushRepository()) {
    var selectedTab by remember { mutableStateOf(MainTab.FINANCE) }

    // Registers this device's FCM token once per MainScreen entry - i.e.
    // right after a successful sign-in, and again on every cold start
    // while already signed in (POST_NOTIFICATIONS may only just have been
    // granted, or the token may have rotated) - see PushRepository's own
    // doc comment for why this has no separate in-app toggle the way the
    // web client's enablePushNotifications() does. Silently ignores
    // failure (e.g. permission denied, offline) rather than surfacing an
    // error - push is a background enhancement, not a blocking flow.
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    LaunchedEffect(uid) {
        if (uid != null) runCatching { pushRepo.registerCurrentToken(uid) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { selectedTab = MainTab.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = MainTab.SETTINGS.label)
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.FINANCE,
                    onClick = { selectedTab = MainTab.FINANCE },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text(MainTab.FINANCE.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SHIFTS,
                    onClick = { selectedTab = MainTab.SHIFTS },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    label = { Text(MainTab.SHIFTS.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.DEBT,
                    onClick = { selectedTab = MainTab.DEBT },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    label = { Text(MainTab.DEBT.label) },
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.SHOPPING,
                    onClick = { selectedTab = MainTab.SHOPPING },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text(MainTab.SHOPPING.label) },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                MainTab.FINANCE -> FinanceScreen()
                MainTab.SHIFTS -> ShiftsScreen()
                MainTab.DEBT -> DebtScreen()
                MainTab.SHOPPING -> ShoppingScreen()
                MainTab.SETTINGS -> SettingsScreen(onSignedOut = onSignedOut)
            }
        }
    }
}
