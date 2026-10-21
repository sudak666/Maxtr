package ua.rytm.app.ui.screens.finance

import java.text.NumberFormat
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
