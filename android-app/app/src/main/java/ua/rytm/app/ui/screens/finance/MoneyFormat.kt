package ua.rytm.app.ui.screens.finance

import java.text.NumberFormat
import java.util.Locale

// Presentation follows the active app locale. Persisted amounts and currency
// codes stay locale-neutral; only grouping and the decimal separator change.
// NumberFormat instances are costly to create (ICU lookup) and this runs for
// every amount of every list row during composition — cached per thread and
// locale (NumberFormat itself isn't thread-safe, hence ThreadLocal).
private val moneyFormats = ThreadLocal.withInitial { HashMap<Locale, NumberFormat>() }

fun formatMoney(amount: Double, locale: Locale = Locale.getDefault()): String =
    moneyFormats.get().getOrPut(locale) {
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }
    }.format(amount).replace('-', '−') // typographic minus, matching the "−" used for signed amounts

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
