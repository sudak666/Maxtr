package ua.rytm.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ua.rytm.app.R

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
    Finance(route = "finance", labelRes = R.string.nav_finance, icon = Icons.Filled.AccountBalanceWallet, activeGradient = listOf(Color(0xFF10B981), Color(0xFF059669))),
    Shifts(route = "shifts", labelRes = R.string.nav_shifts, icon = Icons.Filled.CalendarMonth, activeGradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9))),
    Debt(route = "debt", labelRes = R.string.nav_debt, icon = Icons.Filled.RequestQuote, activeGradient = listOf(Color(0xFFF59E0B), Color(0xFFB45309))),
    Shopping(route = "shopping", labelRes = R.string.nav_shopping, icon = Icons.Filled.ShoppingCart, activeGradient = listOf(Color(0xFFEC4899), Color(0xFFDB2777))),
    Settings(route = "settings", labelRes = R.string.nav_settings, icon = Icons.Filled.Settings, activeGradient = listOf(Color(0xFF64748B), Color(0xFF334155))),
}
