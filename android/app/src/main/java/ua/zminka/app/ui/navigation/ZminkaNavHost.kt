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

/** Auth -> Main, and back to Auth on sign-out (see MainScreen.kt/SettingsScreen.kt). */
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
            MainScreen(onSignedOut = {
                navController.navigate(Routes.AUTH) {
                    popUpTo(Routes.MAIN) { inclusive = true }
                }
            })
        }
    }
}
