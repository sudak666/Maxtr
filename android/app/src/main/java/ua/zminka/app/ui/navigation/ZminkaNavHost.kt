package ua.zminka.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.zminka.app.ui.auth.AuthScreen
import ua.zminka.app.ui.finance.FinanceScreen

private object Routes {
    const val AUTH = "auth"
    const val FINANCE = "finance"
}

/**
 * MVP scope only covers Auth -> Finance (see CLAUDE.md's bottom nav: the
 * web app has 5 tabs — Фінанси/Графік змін/Розрахунки/Покупки/Налаштування
 * — the other 4 aren't ported yet).
 */
@Composable
fun ZminkaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                navController.navigate(Routes.FINANCE) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.FINANCE) {
            FinanceScreen()
        }
    }
}
