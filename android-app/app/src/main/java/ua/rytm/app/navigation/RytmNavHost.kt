package ua.rytm.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.rytm.app.ui.screens.DebtScreen
import ua.rytm.app.ui.screens.SettingsScreen
import ua.rytm.app.ui.screens.finance.FinanceScreen
import ua.rytm.app.ui.screens.shifts.ShiftsScreen
import ua.rytm.app.ui.screens.shopping.ShoppingScreen

@Composable
fun RytmNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { RytmBottomBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RytmDestination.Finance.route,
            modifier = androidx.compose.ui.Modifier.padding(innerPadding),
        ) {
            composable(RytmDestination.Finance.route) { FinanceScreen() }
            composable(RytmDestination.Shifts.route) { ShiftsScreen() }
            composable(RytmDestination.Debt.route) { DebtScreen() }
            composable(RytmDestination.Shopping.route) { ShoppingScreen() }
            composable(RytmDestination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun RytmBottomBar(navController: androidx.navigation.NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        RytmDestination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = stringResource(destination.labelRes)) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
