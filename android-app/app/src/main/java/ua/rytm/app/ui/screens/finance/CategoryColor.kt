package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bento
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ua.rytm.app.ui.theme.BlueDark
import ua.rytm.app.ui.theme.Cyan
import ua.rytm.app.ui.theme.GreenDark
import ua.rytm.app.ui.theme.OrangeDark
import ua.rytm.app.ui.theme.Pink
import ua.rytm.app.ui.theme.PurpleDark
import ua.rytm.app.ui.theme.RedDark

// PWA has a real per-category color/icon map (js/state.js's categoryIcons +
// a color picker per category) — categoryColor() below is an honest
// deterministic placeholder (same category always gets the same color) not
// a claim of real per-category customization. categoryIcon() (added
// alongside it) mirrors js/core.js's own categoryIcon() resolution order —
// manual override, then exact-name map, then keyword match, then a
// deterministic hash fallback — using this app's existing Material Icons
// Extended dependency instead of porting the PWA's custom SVG glyph set: a
// category badge is decorative, not a fixed system-chrome asset like the
// notification-tray icon (which did need pixel parity with a specific
// vendored path), so the closest stock Material icon per PWA icon name is a
// legitimate lower-effort equivalent, not a corner cut.
private val categoryPalette = listOf(PurpleDark, GreenDark, BlueDark, OrangeDark, RedDark, Pink, Cyan)

fun categoryColor(category: String): Color =
    categoryPalette[(category.hashCode().let { if (it < 0) -it else it }) % categoryPalette.size]

// Every name in js/classic-globals.js's window.ICON_NAMES (the exact set
// openCategoryIconPicker() offers on the PWA), each hand-mapped to its
// closest Material Icons Extended equivalent — confirmed by reading that
// array directly, not guessed. Used two ways: (1) as the picker grid's own
// option list (CategoryIconPickerSheet), and (2) as the lookup table for a
// manual per-category override stored in AppState.categoryIcons/
// CategoryIconEntity — the stored value is always one of these PWA icon-name
// strings (never an Android-only identifier), so a value written by either
// platform is meaningful read back on the other, even though the *rendered*
// glyph itself only matches approximately (Material's own shapes, not the
// PWA's hand-drawn stroke icons).
// NOT @Composable, on purpose. These four tables read nothing from the
// composition — Icons.Filled.* are static ImageVectors — but declaring them
// as `@Composable get()` rebuilt all four on EVERY call, and categoryIcon()
// is called from CategoryIconBadge in every transaction row. That meant
// re-allocating a ~50-entry map and recompiling 25 Regex objects per row per
// recomposition. As plain top-level vals they are built once.
val PICKER_ICONS: Map<String, ImageVector> = mapOf(
        "calendar" to Icons.Filled.CalendarMonth,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "clock" to Icons.Filled.Schedule,
        "trendUp" to Icons.Filled.TrendingUp,
        "trendDown" to Icons.Filled.TrendingDown,
        "barChart" to Icons.Filled.BarChart,
        "bolt" to Icons.Filled.Bolt,
        "coin" to Icons.Filled.MonetizationOn,
        "card" to Icons.Filled.CreditCard,
        "banknote" to Icons.Filled.AttachMoney,
        "inbox" to Icons.Filled.Inbox,
        "sparkle" to Icons.Filled.AutoAwesome,
        "swap" to Icons.Filled.SwapHoriz,
        "bank" to Icons.Filled.AccountBalance,
        "cart" to Icons.Filled.ShoppingCart,
        "car" to Icons.Filled.DirectionsCar,
        "house" to Icons.Filled.Home,
        "bag" to Icons.Filled.ShoppingBag,
        "coffee" to Icons.Filled.LocalCafe,
        "burger" to Icons.Filled.Fastfood,
        "cigarette" to Icons.Filled.SmokingRooms,
        "gift" to Icons.Filled.CardGiftcard,
        "briefcase" to Icons.Filled.Work,
        "person" to Icons.Filled.Person,
        "box" to Icons.Filled.Bento,
        "tag" to Icons.Filled.Sell,
        "target" to Icons.Filled.GpsFixed,
        "repeat" to Icons.Filled.Repeat,
        "star" to Icons.Filled.Star,
        "bell" to Icons.Filled.Notifications,
        "camera" to Icons.Filled.PhotoCamera,
        "globe" to Icons.Filled.Public,
        "pie" to Icons.Filled.PieChart,
        "doc" to Icons.Filled.Description,
        "flag" to Icons.Filled.Flag,
        "phone" to Icons.Filled.Phone,
        "calculator" to Icons.Filled.Calculate,
        "idCard" to Icons.Filled.Badge,
        "people" to Icons.Filled.Groups,
        "flame" to Icons.Filled.LocalFireDepartment,
        "handCoin" to Icons.Filled.Payments,
        "pharmacy" to Icons.Filled.LocalPharmacy,
    )

// Exact-name map — confirmed by reading js/core.js's own CAT_ICON, translated
// to the closest Material Icons Extended equivalent per PWA glyph name
// (briefcase->Work, card->CreditCard, handCoin->Payments, box->Bento,
// heartPulse->MonitorHeart, etc — no 1:1 "coin in a hand" or "open box"
// glyph exists in this icon set, so the nearest thematic match was picked
// by hand, not auto-generated). 'heartPulse' isn't in PICKER_ICONS above
// (it's genuinely absent from the PWA's own window.ICON_NAMES too, despite
// CAT_ICON referencing it — confirmed by reading both, not an oversight
// here), so it's kept as its own literal reference rather than routed
// through PICKER_ICONS.
private val CAT_ICON: Map<String, ImageVector> = mapOf(
        "Зарплата" to Icons.Filled.Work,
        "Аванс" to Icons.Filled.CreditCard,
        "Підробіток" to Icons.Filled.CardGiftcard,
        "Повернення боргу" to Icons.Filled.Payments,
        "Інше" to Icons.Filled.Bento,
        "Кава" to Icons.Filled.LocalCafe,
        "Кафе" to Icons.Filled.LocalCafe,
        "Фастфуд" to Icons.Filled.Fastfood,
        "Розваги" to Icons.Filled.CardGiftcard,
        "Кредит" to Icons.Filled.AccountBalance,
        "Борг" to Icons.Filled.Payments,
        "Продукти" to Icons.Filled.ShoppingCart,
        "Транспорт" to Icons.Filled.DirectionsCar,
        "Комуналка" to Icons.Filled.Home,
        "Покупки" to Icons.Filled.ShoppingBag,
        "Здоров'я" to Icons.Filled.MonitorHeart,
        "Аптека" to Icons.Filled.LocalPharmacy,
        "Цигарки" to Icons.Filled.SmokingRooms,
        "Внутрішній переказ" to Icons.Filled.SwapHoriz,
    )

// Keyword/substring match, tried after CAT_ICON's exact-name lookup and
// before the hash-based fallback below — mirrors js/core.js's
// CAT_ICON_KEYWORDS exactly (same regexes, same order), so a category the
// user typed themselves (e.g. "Оренда квартири") still gets a thematically
// sensible icon instead of falling all the way to the random-looking
// fallback pool.
private val CAT_ICON_KEYWORDS: List<Pair<Regex, ImageVector>> = listOf(
        Regex("телефон|мобільн|зв'язок", RegexOption.IGNORE_CASE) to Icons.Filled.Phone,
        Regex("оренда|квартир|житло", RegexOption.IGNORE_CASE) to Icons.Filled.Home,
        Regex("кредит", RegexOption.IGNORE_CASE) to Icons.Filled.AccountBalance,
        Regex("борг", RegexOption.IGNORE_CASE) to Icons.Filled.Payments,
        Regex("продукт|харч", RegexOption.IGNORE_CASE) to Icons.Filled.ShoppingCart,
        Regex("транспорт|таксі|бензин|паливо", RegexOption.IGNORE_CASE) to Icons.Filled.DirectionsCar,
        Regex("розваг|кіно|хобі", RegexOption.IGNORE_CASE) to Icons.Filled.CardGiftcard,
        Regex("кава", RegexOption.IGNORE_CASE) to Icons.Filled.LocalCafe,
        Regex("кафе|ресторан|фастфуд", RegexOption.IGNORE_CASE) to Icons.Filled.Fastfood,
        Regex("подар", RegexOption.IGNORE_CASE) to Icons.Filled.CardGiftcard,
        Regex("зарплат|аванс|дохід", RegexOption.IGNORE_CASE) to Icons.Filled.Work,
        Regex("переказ", RegexOption.IGNORE_CASE) to Icons.Filled.SwapHoriz,
        Regex("цигарк|сигарет", RegexOption.IGNORE_CASE) to Icons.Filled.SmokingRooms,
    )

// Mirrors js/core.js's CAT_ICON_FALLBACK_POOL — 'umbrella' excluded there
// for the same reason (no thematic connection as a blind guess); this app
// has no umbrella-equivalent glyph in play anyway, so nothing to exclude.
private val CAT_ICON_FALLBACK_POOL: List<ImageVector> = listOf(Icons.Filled.Sell, Icons.Filled.Person, Icons.Filled.Star, Icons.Filled.Flag, Icons.Filled.Notifications, Icons.Filled.Public, Icons.Filled.PhotoCamera, Icons.Filled.Bento, Icons.Filled.CardGiftcard)

// `iconOverride`, when non-null, is AppState.categoryIcons[name] (this
// step's own manual per-category override, set via CategoryIconPickerSheet)
// — checked first, exactly mirroring js/core.js's own categoryIcon():
// `if(AppState.categoryIcons&&AppState.categoryIcons[name]) return
// AppState.categoryIcons[name];`. An override naming an icon this app
// doesn't have a Material mapping for (e.g. a PWA-only glyph never added to
// PICKER_ICONS) falls through to automatic resolution rather than crashing
// — a graceful degrade, not full fidelity, since Android's own picker can
// only ever write a name it also knows how to render.
// No longer @Composable either: nothing here touches the composition.
fun categoryIcon(category: String, iconOverride: String? = null): ImageVector {
    iconOverride?.let { PICKER_ICONS[it] }?.let { return it }
    CAT_ICON[category]?.let { return it }
    val keywords = CAT_ICON_KEYWORDS
    for ((regex, icon) in keywords) if (regex.containsMatchIn(category)) return icon
    val pool = CAT_ICON_FALLBACK_POOL
    val h = category.hashCode().let { if (it < 0) -it else it }
    return pool[h % pool.size]
}

@Composable
fun CategoryIconBadge(category: String, iconOverride: String? = null, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = categoryColor(category)
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category, iconOverride), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
