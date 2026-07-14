package ua.zminka.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ua.zminka.app.ui.auth.AuthScreen

private object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
}

/**
 * MVP scope only covers Auth -> Main (Фінанси + Зміни tabs — see
 * MainScreen.kt's own doc comment for which of the web app's 5 bottom-nav
 * tabs aren't ported yet).
 */
@Composable
fun ZminkaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(onAuthenticated = {
                navController.navigate(Routes.MAIN) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }
        composable(Routes.MAIN) {
            MainScreen()
        }
    }
}
