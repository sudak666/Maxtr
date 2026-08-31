package ua.rytm.app.navigation

import ua.rytm.app.ui.theme.GreenLight2
import ua.rytm.app.ui.theme.Purple3
import ua.rytm.app.ui.theme.AmberDeep
import ua.rytm.app.ui.theme.SlateDeep
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Slate
import androidx.compose.ui.graphics.Color
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
// activeGradient mirrors index.html's per-tab .tab-btn.tab-c-*.active
// .tab-icon-wrap overrides (Settings falls back to no override there, i.e.
// the base purple gradient — but since this app's Settings destination has
// no PWA counterpart, a distinct gray is used instead of reusing purple,
// which Shifts already owns).
enum class RytmDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val activeGradient: List<Color>,
) {
    Finance(route = "finance", labelRes = R.string.nav_finance, icon = RytmIcons.AccountBalanceWallet, activeGradient = listOf(GreenDark, GreenLight2)),
    Shifts(route = "shifts", labelRes = R.string.nav_shifts, icon = RytmIcons.CalendarMonth, activeGradient = listOf(PurpleDark, Purple3)),
    Debt(route = "debt", labelRes = R.string.nav_debt, icon = RytmIcons.RequestQuote, activeGradient = listOf(OrangeDark, AmberDeep)),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = RytmIcons.Settings, activeGradient = listOf(Slate, SlateDeep)),
}
