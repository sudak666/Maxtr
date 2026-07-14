package ua.zminka.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ua.zminka.app.ui.finance.FinanceScreen
import ua.zminka.app.ui.shifts.ShiftsScreen

private enum class MainTab(val label: String) {
    FINANCE("Фінанси"),
    SHIFTS("Зміни"),
}

/**
 * MVP scope covers 2 of the web app's 5 bottom-nav tabs (see CLAUDE.md's
 * ".tabs" section — Фінанси/Графік змін/Розрахунки/Покупки/Налаштування).
 * Розрахунки (Debt), Покупки (Shopping), and Налаштування aren't ported
 * yet; add them here as their own MainTab entries once they exist.
 */
@Composable
fun MainScreen() {
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
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                MainTab.FINANCE -> FinanceScreen()
                MainTab.SHIFTS -> ShiftsScreen()
            }
        }
    }
}
