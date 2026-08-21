package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bento
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
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
// exact-name map, then keyword match, then a deterministic hash fallback —
// using this app's existing Material Icons Extended dependency instead of
// porting the PWA's custom SVG glyph set: a category badge is decorative,
// not a fixed system-chrome asset like the notification-tray icon (which
// did need pixel parity with a specific vendored path), so the closest
// stock Material icon per PWA icon name is a legitimate lower-effort
// equivalent, not a corner cut.
private val categoryPalette = listOf(PurpleDark, GreenDark, BlueDark, OrangeDark, RedDark, Pink, Cyan)

fun categoryColor(category: String): Color =
    categoryPalette[(category.hashCode().let { if (it < 0) -it else it }) % categoryPalette.size]

// Exact-name map — confirmed by reading js/core.js's own CAT_ICON, translated
// to the closest Material Icons Extended equivalent per PWA glyph name
// (briefcase->Work, card->CreditCard, handCoin->Payments, box->Bento,
// heartPulse->MonitorHeart, etc — no 1:1 "coin in a hand" or "open box"
// glyph exists in this icon set, so the nearest thematic match was picked
// by hand, not auto-generated).
private val CAT_ICON: Map<String, ImageVector>
    @Composable get() = mapOf(
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
private val CAT_ICON_KEYWORDS: List<Pair<Regex, ImageVector>>
    @Composable get() = listOf(
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
private val CAT_ICON_FALLBACK_POOL: List<ImageVector>
    @Composable get() = listOf(Icons.Filled.Sell, Icons.Filled.Person, Icons.Filled.Star, Icons.Filled.Flag, Icons.Filled.Notifications, Icons.Filled.Public, Icons.Filled.PhotoCamera, Icons.Filled.Bento, Icons.Filled.CardGiftcard)

// No manual per-category override yet (js/state.js's AppState.categoryIcons,
// set via openCategoryIconPicker() — the icon *picker* UI itself is a
// disclosed follow-up, not part of this step) — this always falls through
// to the exact-name map, then the keyword match, then the hash fallback.
@Composable
fun categoryIcon(category: String): ImageVector {
    CAT_ICON[category]?.let { return it }
    val keywords = CAT_ICON_KEYWORDS
    for ((regex, icon) in keywords) if (regex.containsMatchIn(category)) return icon
    val pool = CAT_ICON_FALLBACK_POOL
    val h = category.hashCode().let { if (it < 0) -it else it }
    return pool[h % pool.size]
}

@Composable
fun CategoryIconBadge(category: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = categoryColor(category)
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(categoryIcon(category), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}
