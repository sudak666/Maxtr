package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import ua.rytm.app.ui.icons.RytmIcons
import ua.rytm.app.ui.icons.AccountBalance
import ua.rytm.app.ui.icons.AccountBalanceWallet
import ua.rytm.app.ui.icons.AttachMoney
import ua.rytm.app.ui.icons.AutoAwesome
import ua.rytm.app.ui.icons.Badge
import ua.rytm.app.ui.icons.BarChart
import ua.rytm.app.ui.icons.Bento
import ua.rytm.app.ui.icons.Bolt
import ua.rytm.app.ui.icons.Calculate
import ua.rytm.app.ui.icons.CalendarMonth
import ua.rytm.app.ui.icons.CardGiftcard
import ua.rytm.app.ui.icons.CreditCard
import ua.rytm.app.ui.icons.Description
import ua.rytm.app.ui.icons.DirectionsCar
import ua.rytm.app.ui.icons.Fastfood
import ua.rytm.app.ui.icons.Flag
import ua.rytm.app.ui.icons.GpsFixed
import ua.rytm.app.ui.icons.Groups
import ua.rytm.app.ui.icons.Home
import ua.rytm.app.ui.icons.Inbox
import ua.rytm.app.ui.icons.LocalCafe
import ua.rytm.app.ui.icons.LocalFireDepartment
import ua.rytm.app.ui.icons.LocalPharmacy
import ua.rytm.app.ui.icons.MonetizationOn
import ua.rytm.app.ui.icons.MonitorHeart
import ua.rytm.app.ui.icons.Notifications
import ua.rytm.app.ui.icons.Payments
import ua.rytm.app.ui.icons.Person
import ua.rytm.app.ui.icons.Phone
import ua.rytm.app.ui.icons.PhotoCamera
import ua.rytm.app.ui.icons.PieChart
import ua.rytm.app.ui.icons.Public
import ua.rytm.app.ui.icons.Repeat
import ua.rytm.app.ui.icons.Schedule
import ua.rytm.app.ui.icons.Sell
import ua.rytm.app.ui.icons.ShoppingBag
import ua.rytm.app.ui.icons.ShoppingCart
import ua.rytm.app.ui.icons.SmokingRooms
import ua.rytm.app.ui.icons.Star
import ua.rytm.app.ui.icons.SwapHoriz
import ua.rytm.app.ui.icons.TrendingDown
import ua.rytm.app.ui.icons.TrendingUp
import ua.rytm.app.ui.icons.Work

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
        "calendar" to RytmIcons.CalendarMonth,
        "wallet" to RytmIcons.AccountBalanceWallet,
        "clock" to RytmIcons.Schedule,
        "trendUp" to RytmIcons.TrendingUp,
        "trendDown" to RytmIcons.TrendingDown,
        "barChart" to RytmIcons.BarChart,
        "bolt" to RytmIcons.Bolt,
        "coin" to RytmIcons.MonetizationOn,
        "card" to RytmIcons.CreditCard,
        "banknote" to RytmIcons.AttachMoney,
        "inbox" to RytmIcons.Inbox,
        "sparkle" to RytmIcons.AutoAwesome,
        "swap" to RytmIcons.SwapHoriz,
        "bank" to RytmIcons.AccountBalance,
        "cart" to RytmIcons.ShoppingCart,
        "car" to RytmIcons.DirectionsCar,
        "house" to RytmIcons.Home,
        "bag" to RytmIcons.ShoppingBag,
        "coffee" to RytmIcons.LocalCafe,
        "burger" to RytmIcons.Fastfood,
        "cigarette" to RytmIcons.SmokingRooms,
        "gift" to RytmIcons.CardGiftcard,
        "briefcase" to RytmIcons.Work,
        "person" to RytmIcons.Person,
        "box" to RytmIcons.Bento,
        "tag" to RytmIcons.Sell,
        "target" to RytmIcons.GpsFixed,
        "repeat" to RytmIcons.Repeat,
        "star" to RytmIcons.Star,
        "bell" to RytmIcons.Notifications,
        "camera" to RytmIcons.PhotoCamera,
        "globe" to RytmIcons.Public,
        "pie" to RytmIcons.PieChart,
        "doc" to RytmIcons.Description,
        "flag" to RytmIcons.Flag,
        "phone" to RytmIcons.Phone,
        "calculator" to RytmIcons.Calculate,
        "idCard" to RytmIcons.Badge,
        "people" to RytmIcons.Groups,
        "flame" to RytmIcons.LocalFireDepartment,
        "handCoin" to RytmIcons.Payments,
        "pharmacy" to RytmIcons.LocalPharmacy,
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
        "Зарплата" to RytmIcons.Work,
        "Аванс" to RytmIcons.CreditCard,
        "Підробіток" to RytmIcons.CardGiftcard,
        "Повернення боргу" to RytmIcons.Payments,
        "Інше" to RytmIcons.Bento,
        "Кава" to RytmIcons.LocalCafe,
        "Кафе" to RytmIcons.LocalCafe,
        "Фастфуд" to RytmIcons.Fastfood,
        "Розваги" to RytmIcons.CardGiftcard,
        "Кредит" to RytmIcons.AccountBalance,
        "Борг" to RytmIcons.Payments,
        "Продукти" to RytmIcons.ShoppingCart,
        "Транспорт" to RytmIcons.DirectionsCar,
        "Комуналка" to RytmIcons.Home,
        "Покупки" to RytmIcons.ShoppingBag,
        "Здоров'я" to RytmIcons.MonitorHeart,
        "Аптека" to RytmIcons.LocalPharmacy,
        "Цигарки" to RytmIcons.SmokingRooms,
        "Внутрішній переказ" to RytmIcons.SwapHoriz,
    )

// Keyword/substring match, tried after CAT_ICON's exact-name lookup and
// before the hash-based fallback below — mirrors js/core.js's
// CAT_ICON_KEYWORDS exactly (same regexes, same order), so a category the
// user typed themselves (e.g. "Оренда квартири") still gets a thematically
// sensible icon instead of falling all the way to the random-looking
// fallback pool.
private val CAT_ICON_KEYWORDS: List<Pair<Regex, ImageVector>> = listOf(
        Regex("телефон|мобільн|зв'язок", RegexOption.IGNORE_CASE) to RytmIcons.Phone,
        Regex("оренда|квартир|житло", RegexOption.IGNORE_CASE) to RytmIcons.Home,
        Regex("кредит", RegexOption.IGNORE_CASE) to RytmIcons.AccountBalance,
        Regex("борг", RegexOption.IGNORE_CASE) to RytmIcons.Payments,
        Regex("продукт|харч", RegexOption.IGNORE_CASE) to RytmIcons.ShoppingCart,
        Regex("транспорт|таксі|бензин|паливо", RegexOption.IGNORE_CASE) to RytmIcons.DirectionsCar,
        Regex("розваг|кіно|хобі", RegexOption.IGNORE_CASE) to RytmIcons.CardGiftcard,
        Regex("кава", RegexOption.IGNORE_CASE) to RytmIcons.LocalCafe,
        Regex("кафе|ресторан|фастфуд", RegexOption.IGNORE_CASE) to RytmIcons.Fastfood,
        Regex("подар", RegexOption.IGNORE_CASE) to RytmIcons.CardGiftcard,
        Regex("зарплат|аванс|дохід", RegexOption.IGNORE_CASE) to RytmIcons.Work,
        Regex("переказ", RegexOption.IGNORE_CASE) to RytmIcons.SwapHoriz,
        Regex("цигарк|сигарет", RegexOption.IGNORE_CASE) to RytmIcons.SmokingRooms,
    )

// Mirrors js/core.js's CAT_ICON_FALLBACK_POOL — 'umbrella' excluded there
// for the same reason (no thematic connection as a blind guess); this app
// has no umbrella-equivalent glyph in play anyway, so nothing to exclude.
private val CAT_ICON_FALLBACK_POOL: List<ImageVector> = listOf(RytmIcons.Sell, RytmIcons.Person, RytmIcons.Star, RytmIcons.Flag, RytmIcons.Notifications, RytmIcons.Public, RytmIcons.PhotoCamera, RytmIcons.Bento, RytmIcons.CardGiftcard)

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
