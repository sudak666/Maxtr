package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Known Ukrainian merchants, recognised from a transaction's comment (bank
 * pushes put the merchant there: "KOLO KIYEV UA", "Сільпо"). A row then shows
 * the merchant's own monogram in its brand color instead of the generic
 * category icon, so a long history scans by eye. Deliberately monograms, not
 * the companies' logos: no third-party artwork is bundled and it works offline.
 */
object MerchantBrands {
    data class Brand(val name: String, val monogram: String, val color: Long, val keywords: List<String>)

    val all = listOf(
        Brand("АТБ", "АТБ", 0xFFE30613, listOf("атб", "atb")),
        Brand("Сільпо", "С", 0xFFFF6A13, listOf("сільпо", "silpo")),
        Brand("Novus", "N", 0xFF00A651, listOf("novus", "новус")),
        Brand("Фора", "Ф", 0xFFE4002B, listOf("фора", "fora")),
        Brand("Varus", "V", 0xFF009639, listOf("varus", "варус")),
        Brand("Metro", "M", 0xFF003D7C, listOf("metro cash", "метро кеш", "metro ")),
        Brand("Auchan", "A", 0xFFE2001A, listOf("auchan", "ашан")),
        Brand("Коло", "К", 0xFF2E7D32, listOf("kolo ", "коло ", "kolo kiyev")),
        Brand("Велмарт", "В", 0xFF0072BC, listOf("velmart", "велмарт")),
        Brand("Таврія В", "ТВ", 0xFFD71920, listOf("tavria", "таврія")),
        Brand("Епіцентр", "Е", 0xFFF39200, listOf("epicentr", "епіцентр")),
        Brand("Rozetka", "R", 0xFF00A046, listOf("rozetka", "розетка")),
        Brand("Comfy", "C", 0xFFE5007E, listOf("comfy", "комфі")),
        Brand("Фокстрот", "Ф", 0xFFFF5800, listOf("foxtrot", "фокстрот")),
        Brand("Glovo", "G", 0xFFFFC244, listOf("glovo", "глово")),
        Brand("Bolt", "B", 0xFF34D186, listOf("bolt")),
        Brand("Uklon", "U", 0xFF2F54EB, listOf("uklon", "уклон")),
        Brand("Uber", "U", 0xFF8A8A8A, listOf("uber")),
        Brand("Нова пошта", "НП", 0xFFDA291C, listOf("nova poshta", "нова пошта", "novaposhta", "nova pay")),
        Brand("Укрпошта", "УП", 0xFFFFC72C, listOf("ukrposhta", "укрпошта")),
        Brand("McDonald's", "M", 0xFFFFBC0D, listOf("mcdonald", "макдональдс", "макдональц")),
        Brand("KFC", "KFC", 0xFFE4002B, listOf("kfc")),
        Brand("Пузата хата", "ПХ", 0xFF8B5A2B, listOf("puzata", "пузата")),
        Brand("Aroma Kava", "AK", 0xFF6F4E37, listOf("aroma kava", "арома кава")),
        Brand("Львівські круасани", "ЛК", 0xFFC8102E, listOf("круасани", "kruasany")),
        Brand("WOG", "W", 0xFF00843D, listOf("wog")),
        Brand("OKKO", "O", 0xFFE30613, listOf("okko", "окко")),
        Brand("SOCAR", "S", 0xFF0057B8, listOf("socar")),
        Brand("UPG", "U", 0xFFFDB913, listOf("upg")),
        Brand("Київстар", "КС", 0xFF0090D7, listOf("kyivstar", "київстар")),
        Brand("Vodafone", "V", 0xFFE60000, listOf("vodafone", "водафон")),
        Brand("lifecell", "L", 0xFFFFD100, listOf("lifecell", "лайфсел")),
        Brand("Netflix", "N", 0xFFE50914, listOf("netflix")),
        Brand("Spotify", "S", 0xFF1DB954, listOf("spotify")),
        Brand("YouTube", "YT", 0xFFFF0000, listOf("youtube")),
        Brand("Google", "G", 0xFF4285F4, listOf("google")),
        Brand("Apple", "A", 0xFF9AA0A6, listOf("apple.com", "itunes", "apple ")),
        Brand("Steam", "S", 0xFF66C0F4, listOf("steam")),
        Brand("Prom", "P", 0xFF7B2CBF, listOf("prom.ua", "prom ")),
        Brand("EVA", "EVA", 0xFFE6007E, listOf("eva ", "єва")),
        Brand("Watsons", "W", 0xFF00A39C, listOf("watsons", "ватсонс")),
        Brand("Аптека АНЦ", "АНЦ", 0xFF0072BC, listOf("анц", "anc ")),
        Brand("Аптека 9-1-1", "911", 0xFF00A651, listOf("9-1-1", "аптека 911")),
        Brand("Подорожник", "П", 0xFF00A651, listOf("подорожник", "podorozhnyk")),
        Brand("Космо", "К", 0xFFE6007E, listOf("kosmo", "космо")),
        Brand("Multiplex", "MX", 0xFF6A1B9A, listOf("multiplex", "мультиплекс")),
        Brand("Планета Кіно", "ПК", 0xFF1E88E5, listOf("planeta kino", "планета кіно")),
    )

    /** The brand named in [comment], or null. Whole-word-ish: keywords match after padding with spaces. */
    fun match(comment: String?): Brand? {
        if (comment.isNullOrBlank()) return null
        val text = " " + comment.lowercase().replace(Regex("[^\\p{L}\\p{Nd}.\\-]+"), " ") + " "
        return all.firstOrNull { b -> b.keywords.any { k -> text.contains(if (k.endsWith(" ")) " $k" else " $k") } }
    }
}

/** Monogram disc for a known merchant, same footprint and tint style as CategoryIconBadge. */
@Composable
fun MerchantBadge(brand: MerchantBrands.Brand, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val color = Color(brand.color)
    // Yellow/light brand colors would vanish as text on the light theme.
    // …and very dark ones (Aroma Kava brown) would vanish on the dark theme.
    val dark = ua.rytm.app.ui.theme.RytmSemantic.isDark
    val ink = when {
        !dark && color.luminance() > 0.45f -> androidx.compose.ui.graphics.lerp(color, Color.Black, 0.5f)
        dark && color.luminance() < 0.08f -> androidx.compose.ui.graphics.lerp(color, Color.White, 0.45f)
        else -> color
    }
    Box(modifier.size(size).background(ink.copy(alpha = 0.20f), CircleShape), contentAlignment = Alignment.Center) {
        Text(
            brand.monogram,
            color = ink,
            fontWeight = FontWeight.Black,
            fontSize = if (brand.monogram.length >= 3) 11.sp else if (brand.monogram.length == 2) 13.sp else 16.sp,
            maxLines = 1,
        )
    }
}
