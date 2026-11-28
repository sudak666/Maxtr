package ua.rytm.app.ui.screens.finance

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// Presentation follows the active app locale. Persisted amounts and currency
// codes stay locale-neutral; only grouping and the decimal separator change.
// NumberFormat instances are costly to create (ICU lookup) and this runs for
// every amount of every list row during composition — cached per thread and
// locale (NumberFormat itself isn't thread-safe, hence ThreadLocal).
private val moneyFormats = ThreadLocal.withInitial { HashMap<Locale, NumberFormat>() }

private val centsFormats = ThreadLocal.withInitial { HashMap<Locale, NumberFormat>() }

/** Whole amounts stay short ("35"); anything with kopecks always gets two
 * digits ("319,20", not "319,2") so a column of amounts reads the same. */
fun formatMoney(amount: Double, locale: Locale = Locale.getDefault()): String {
    val wholeAmount = kotlin.math.abs(amount * 100 - kotlin.math.round(amount) * 100) < 0.5
    val cache = if (wholeAmount) moneyFormats else centsFormats
    return cache.get().getOrPut(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = if (wholeAmount) 0 else 2
        }
    }.format(amount).replace('-', '−') // typographic minus, matching the "−" used for signed amounts
}

/** "+1 200" / "−1 200" / "0": a result that can go either way. */
fun signedMoney(amount: Double): String = (if (amount >= 0.005) "+" else "") + formatMoney(amount)

/** Accepts the decimal comma produced by Ukrainian keyboards while keeping
 * the value stored as a locale-neutral Double. Grouping spaces are ignored. */
fun parseMoneyInput(value: String): Double? = value
    .trim()
    .replace(" ", "")
    .replace("\u00A0", "")
    .replace("\u202F", "")
    .replace('−', '-')
    .replace(',', '.')
    .takeIf { it.count { char -> char == '.' } <= 1 }
    ?.toDoubleOrNull()
    ?.takeIf(Double::isFinite)

fun currencySymbol(code: String): String = when (code) {
    "UAH" -> "₴"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> code
}

/**
 * «27 %» in Ukrainian — a no-break space before the sign, as the standard
 * asks and as the Finance pace note already did — «27%» in English. One
 * helper, so the app stops mixing «27 %» and «27%» (outside review, 2026-11-27).
 */
fun formatPercent(value: Int, locale: Locale = Locale.getDefault()): String =
    formatPercent(NumberFormat.getIntegerInstance(locale).format(value).replace('-', '−'), locale) // «1 340 %», not «1340 %»

fun formatPercent(value: String, locale: Locale = Locale.getDefault()): String =
    if (locale.language == "en") "$value%" else "$value\u00A0%"

/** «жовтня» / «October»: the month in «ніж за ці дні жовтня». java.time's format form is the genitive in Ukrainian. */
@Composable
fun monthGenitive(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    return month.month.getDisplayName(TextStyle.FULL, locale)
}

/** «листопад» / «November»: the month in «Витрати за листопад», lowercase mid-sentence in Ukrainian. */
@Composable
fun monthForLabel(month: YearMonth): String {
    val locale = LocalConfiguration.current.locales[0]
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    return if (locale.language == "en") name else name.lowercase(locale)
}
