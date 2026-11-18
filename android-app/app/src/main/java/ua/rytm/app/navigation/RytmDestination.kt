package ua.rytm.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import ua.rytm.app.R
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.CalendarMonth
import ua.rytm.app.ui.icons.RequestQuote
import ua.rytm.app.ui.icons.Settings

// One entry per PWA bottom-nav tab (see ANDROID_MIGRATION.md §1.1) — Settings
// is included here even though the PWA hides it from its own tab bar,
// because Android's NavigationBar has room for a 5th destination and this
// keeps navigation flat/predictable instead of hiding it behind a gear icon.
enum class RytmDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    Finance(route = "finance", labelRes = R.string.nav_finance, icon = RytmIcons.AccountBalanceWallet),
    Shifts(route = "shifts", labelRes = R.string.nav_shifts, icon = RytmIcons.CalendarMonth),
    Debt(route = "debt", labelRes = R.string.nav_debt, icon = RytmIcons.RequestQuote),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = RytmIcons.Settings),
}
