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
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ua.zminka.app.ui.debt.DebtScreen
import ua.zminka.app.ui.finance.FinanceScreen
import ua.zminka.app.ui.settings.SettingsScreen
import ua.zminka.app.ui.shifts.ShiftsScreen
import ua.zminka.app.ui.shopping.ShoppingScreen

// Same order as the web app's .tabs bottom nav (see CLAUDE.md:
// Фінанси/Графік змін/Розрахунки/Покупки/Налаштування).
private enum class MainTab(val label: String) {
    FINANCE("Фінанси"),
    SHIFTS("Зміни"),
    DEBT("Розрахунки"),
    SHOPPING("Покупки"),
    SETTINGS("Налаштування"),
}

/** All 5 of the web app's bottom-nav tabs are now represented, though Settings is MVP-thin (see SettingsScreen's own doc comment). */
@Composable
fun MainScreen(onSignedOut: () -> Unit) {
    var selectedTab by remember { mutableStateOf(MainTab.FINANCE) }

    Scaffold(
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
                NavigationBarItem(
                    selected = selectedTab == MainTab.SETTINGS,
                    onClick = { selectedTab = MainTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(MainTab.SETTINGS.label) },
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
