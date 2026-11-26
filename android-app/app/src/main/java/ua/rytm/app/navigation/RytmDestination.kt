package ua.rytm.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import ua.rytm.app.R
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.BarChart
import ua.rytm.app.ui.icons.CalendarMonth
import ua.rytm.app.ui.icons.RequestQuote
import ua.rytm.app.ui.icons.Settings

/**
 * Top-level destinations. [inBar] ones are the bottom capsule's tabs; Settings
 * is not (design audit 2026-11-26): it is opened rarely and is reached from the
 * profile avatar, as in monobank/Revolut, so its slot went to Analytics.
 */
enum class RytmDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val inBar: Boolean = true,
) {
    Finance(route = "finance", labelRes = R.string.nav_finance, icon = RytmIcons.AccountBalanceWallet),
    Analytics(route = "analytics", labelRes = R.string.nav_analytics, icon = RytmIcons.BarChart),
    Shifts(route = "shifts", labelRes = R.string.nav_shifts, icon = RytmIcons.CalendarMonth),
    Debt(route = "debt", labelRes = R.string.nav_debt, icon = RytmIcons.RequestQuote),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = RytmIcons.Settings, inBar = false),
    ;

    companion object {
        val bar: List<RytmDestination> = entries.filter { it.inBar }
    }
}
