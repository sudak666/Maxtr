package ua.rytm.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import ua.rytm.app.R

// One entry per PWA bottom-nav tab (see ANDROID_MIGRATION.md §1.1) — Settings
// is included here even though the PWA hides it from its own tab bar,
// because Android's NavigationBar has room for a 5th destination and this
// keeps navigation flat/predictable instead of hiding it behind a gear icon.
enum class RytmDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Finance(route = "finance", labelRes = R.string.nav_finance, icon = Icons.Filled.AccountBalanceWallet),
    Shifts(route = "shifts", labelRes = R.string.nav_shifts, icon = Icons.Filled.CalendarMonth),
    Debt(route = "debt", labelRes = R.string.nav_debt, icon = Icons.Filled.RequestQuote),
    Shopping(route = "shopping", labelRes = R.string.nav_shopping, icon = Icons.Filled.ShoppingCart),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = Icons.Filled.Settings),
}
