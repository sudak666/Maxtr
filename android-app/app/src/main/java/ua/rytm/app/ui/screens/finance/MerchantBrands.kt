package ua.rytm.app.ui.screens.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * category icon, so a long history scans by eye.
 *
 * Logos (owner's call, 2026-11-26): the 16 most common brands carry a real
 * mark, bundled (offline, nothing about the user's spending leaves the phone),
 * rendered once onto a white 160px tile. 15 come from Wikimedia Commons files —
 * Silpo outline logo, Novus Ukraina logo, ROZETKA-Logo-L3-B-RGB, Glovo Logo,
 * Bolt Technology Logo 2019, Nova Poshta 2022 logo, McDonald's Golden Arches,
 * WOG Logo, Kyivstar logo, Lifecell-logo-2025-blue-on-yellow, Netflix 2016 N
 * logo, Spotify icon, YouTube full-color icon (2017), Google "G" logo, Apple
 * logo black. Wordmark brands keep only their mark, cropped to its measured
 * bounds (Kyivstar star, Rozetka smile, WOG «W», Nova Poshta sign). ATB is its
 * red «АТБ» in Inter Black on white, as on its signs: the Commons «ATB Logo»
 * (an orange sail) was not recognisable to the owner. Added the same day at
 * the owner's request (brands he pays often): Vodafone logo 2017 (the red
 * disc), ChatGPT logo (the app tile), Claude AI symbol, Fora logo (the green
 * «фора» plate) — all Commons — and Prom, whose public-domain mark (uk.wikipedia
 * Prom_logo.png, only 216×80) was redrawn as a vector square frame with the
 * sampled #4E4D9F→#7D2391 gradient. Shown only to name where
 * a payment went; every other brand keeps its monogram in the brand colour.
 */
object MerchantBrands {
    /** [logo]: a bundled logo tile (res/drawable-nodpi/merchant_*.png); null = the monogram. */
    data class Brand(val name: String, val monogram: String, val color: Long, val keywords: List<String>, val logo: Int? = null, val logoFullBleed: Boolean = false)

    val all = listOf(
        Brand("АТБ", "АТБ", 0xFFE30613, listOf("атб", "atb"), logo = ua.rytm.app.R.drawable.merchant_atb),
        Brand("Сільпо", "С", 0xFFFF6A13, listOf("сільпо", "silpo"), logo = ua.rytm.app.R.drawable.merchant_silpo),
        Brand("Novus", "N", 0xFF00A651, listOf("novus", "новус"), logo = ua.rytm.app.R.drawable.merchant_novus),
        Brand("Фора", "Ф", 0xFFE4002B, listOf("фора", "fora"), logo = ua.rytm.app.R.drawable.merchant_fora, logoFullBleed = true),
        Brand("Varus", "V", 0xFF009639, listOf("varus", "варус")),
        Brand("Metro", "M", 0xFF003D7C, listOf("metro cash", "метро кеш", "metro ")),
        Brand("Auchan", "A", 0xFFE2001A, listOf("auchan", "ашан")),
        Brand("Коло", "К", 0xFF2E7D32, listOf("kolo ", "коло ", "kolo kiyev")),
        Brand("Велмарт", "В", 0xFF0072BC, listOf("velmart", "велмарт")),
        Brand("Таврія В", "ТВ", 0xFFD71920, listOf("tavria", "таврія")),
        Brand("Епіцентр", "Е", 0xFFF39200, listOf("epicentr", "епіцентр")),
        Brand("Rozetka", "R", 0xFF00A046, listOf("rozetka", "розетка"), logo = ua.rytm.app.R.drawable.merchant_rozetka, logoFullBleed = true),
        Brand("Comfy", "C", 0xFFE5007E, listOf("comfy", "комфі")),
        Brand("Фокстрот", "Ф", 0xFFFF5800, listOf("foxtrot", "фокстрот")),
        Brand("Glovo", "G", 0xFFFFC244, listOf("glovo", "глово"), logo = ua.rytm.app.R.drawable.merchant_glovo),
        Brand("Bolt", "B", 0xFF34D186, listOf("bolt"), logo = ua.rytm.app.R.drawable.merchant_bolt),
        Brand("Uklon", "U", 0xFF2F54EB, listOf("uklon", "уклон")),
        Brand("Uber", "U", 0xFF8A8A8A, listOf("uber")),
        Brand("Нова пошта", "НП", 0xFFDA291C, listOf("nova poshta", "нова пошта", "novaposhta", "nova pay"), logo = ua.rytm.app.R.drawable.merchant_novaposhta),
        Brand("Укрпошта", "УП", 0xFFFFC72C, listOf("ukrposhta", "укрпошта")),
        Brand("McDonald's", "M", 0xFFFFBC0D, listOf("mcdonald", "макдональдс", "макдональц"), logo = ua.rytm.app.R.drawable.merchant_mcdonalds),
        Brand("KFC", "KFC", 0xFFE4002B, listOf("kfc")),
        Brand("Пузата хата", "ПХ", 0xFF8B5A2B, listOf("puzata", "пузата")),
        Brand("Aroma Kava", "AK", 0xFF6F4E37, listOf("aroma kava", "арома кава")),
        Brand("Львівські круасани", "ЛК", 0xFFC8102E, listOf("круасани", "kruasany")),
        Brand("WOG", "W", 0xFF00843D, listOf("wog"), logo = ua.rytm.app.R.drawable.merchant_wog),
        Brand("OKKO", "O", 0xFFE30613, listOf("okko", "окко")),
        Brand("SOCAR", "S", 0xFF0057B8, listOf("socar")),
        Brand("UPG", "U", 0xFFFDB913, listOf("upg")),
        Brand("Київстар", "КС", 0xFF0090D7, listOf("kyivstar", "київстар"), logo = ua.rytm.app.R.drawable.merchant_kyivstar),
        Brand("Vodafone", "V", 0xFFE60000, listOf("vodafone", "водафон"), logo = ua.rytm.app.R.drawable.merchant_vodafone, logoFullBleed = true),
        Brand("lifecell", "L", 0xFFFFD100, listOf("lifecell", "лайфсел"), logo = ua.rytm.app.R.drawable.merchant_lifecell, logoFullBleed = true),
        Brand("Netflix", "N", 0xFFE50914, listOf("netflix"), logo = ua.rytm.app.R.drawable.merchant_netflix),
        Brand("Spotify", "S", 0xFF1DB954, listOf("spotify"), logo = ua.rytm.app.R.drawable.merchant_spotify),
        Brand("YouTube", "YT", 0xFFFF0000, listOf("youtube"), logo = ua.rytm.app.R.drawable.merchant_youtube),
        Brand("Google", "G", 0xFF4285F4, listOf("google"), logo = ua.rytm.app.R.drawable.merchant_google),
        Brand("Apple", "A", 0xFF9AA0A6, listOf("apple.com", "itunes", "apple "), logo = ua.rytm.app.R.drawable.merchant_apple),
        Brand("Steam", "S", 0xFF66C0F4, listOf("steam")),
        Brand("Prom", "P", 0xFF7B2CBF, listOf("prom.ua", "prom "), logo = ua.rytm.app.R.drawable.merchant_prom),
        Brand("EVA", "EVA", 0xFFE6007E, listOf("eva ", "єва")),
        Brand("Watsons", "W", 0xFF00A39C, listOf("watsons", "ватсонс")),
        Brand("Аптека АНЦ", "АНЦ", 0xFF0072BC, listOf("анц", "anc ")),
        Brand("Аптека 9-1-1", "911", 0xFF00A651, listOf("9-1-1", "аптека 911")),
        Brand("Подорожник", "П", 0xFF00A651, listOf("подорожник", "podorozhnyk")),
        Brand("Космо", "К", 0xFFE6007E, listOf("kosmo", "космо")),
        Brand("Multiplex", "MX", 0xFF6A1B9A, listOf("multiplex", "мультиплекс")),
        Brand("Планета Кіно", "ПК", 0xFF1E88E5, listOf("planeta kino", "планета кіно")),
        // 2026-11-26: electronics, clothes, marketplaces, food delivery,
        // transport, subscriptions and utilities that were missing. Words that
        // are also ordinary Ukrainian («простор», «аврора», «цитрус») match
        // only as a whole word (trailing space), never as a prefix.
        Brand("Аврора", "А", 0xFFE4002B, listOf("avrora", "аврора ")),
        Brand("Allo", "A", 0xFFE30613, listOf("allo.ua", "allo ")),
        Brand("Citrus", "C", 0xFFFF7A00, listOf("citrus", "цитрус ")),
        Brand("Moyo", "M", 0xFF00A651, listOf("moyo", "мойо")),
        Brand("Brocard", "B", 0xFF1A1A1A, listOf("brocard", "брокард")),
        Brand("Prostor", "P", 0xFF00A0E3, listOf("prostor", "простор ")),
        Brand("Intertop", "I", 0xFF1A1A1A, listOf("intertop", "інтертоп")),
        Brand("Sinsay", "S", 0xFF1A1A1A, listOf("sinsay")),
        Brand("JYSK", "J", 0xFF0A3B7C, listOf("jysk")),
        Brand("Kasta", "K", 0xFF000000, listOf("kasta")),
        Brand("MAKEUP", "MU", 0xFFE6007E, listOf("makeup.com", "makeup ")),
        Brand("OLX", "OLX", 0xFF23E5DB, listOf("olx")),
        Brand("Temu", "T", 0xFFFB7701, listOf("temu")),
        Brand("AliExpress", "AE", 0xFFE62E04, listOf("aliexpress")),
        Brand("Domino's", "D", 0xFF006491, listOf("domino")),
        Brand("Челентано", "Ч", 0xFFE30613, listOf("celentano", "челентано")),
        Brand("Sushi Master", "SM", 0xFFE30613, listOf("sushi master", "суші майстер")),
        Brand("Укрзалізниця", "УЗ", 0xFF0057B8, listOf("uz.gov", "ukrzaliznytsia", "укрзалізниця")),
        Brand("Київ Цифровий", "КЦ", 0xFF00A0E3, listOf("kyiv digital", "київ цифровий")),
        Brand("Megogo", "MG", 0xFF0E1A2B, listOf("megogo", "мегого")),
        Brand("ChatGPT", "AI", 0xFF10A37F, listOf("openai", "chatgpt"), logo = ua.rytm.app.R.drawable.merchant_chatgpt, logoFullBleed = true),
        Brand("Claude", "C", 0xFFD97757, listOf("anthropic", "claude.ai", "claude "), logo = ua.rytm.app.R.drawable.merchant_claude),
        Brand("ДТЕК", "Д", 0xFFFFC72C, listOf("dtek", "дтек")),
        Brand("Нафтогаз", "НГ", 0xFF0057B8, listOf("naftogaz", "нафтогаз")),
        Brand("Укртелеком", "УТ", 0xFF0072BC, listOf("ukrtelecom", "укртелеком")),
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
    brand.logo?.let { logo ->
        // The tile is white already; the circle clip makes it the same disc
        // as every other row badge.
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(logo),
            contentDescription = null,
            modifier = modifier.size(size).clip(CircleShape).then(
                // Light rows are white too: a hairline keeps a white disc a disc.
                // A tile coloured to its edge (Vodafone, Fora…) needs none.
                if (ua.rytm.app.ui.theme.RytmSemantic.isDark || brand.logoFullBleed) Modifier
                else Modifier.border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ),
        )
        return
    }
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
