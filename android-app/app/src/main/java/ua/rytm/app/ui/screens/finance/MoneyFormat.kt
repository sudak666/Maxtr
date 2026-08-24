package ua.rytm.app.ui.screens.finance

import java.text.NumberFormat
import java.util.Locale

// Presentation follows the active app locale. Persisted amounts and currency
// codes stay locale-neutral; only grouping and the decimal separator change.
fun formatMoney(amount: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(amount)

/** Accepts the decimal comma produced by Ukrainian keyboards while keeping
 * the value stored as a locale-neutral Double. Grouping spaces are ignored. */
fun parseMoneyInput(value: String): Double? = value
    .trim()
    .replace(" ", "")
    .replace("\u00A0", "")
    .replace("\u202F", "")
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
