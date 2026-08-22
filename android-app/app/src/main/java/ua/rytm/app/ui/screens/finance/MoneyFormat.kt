package ua.rytm.app.ui.screens.finance

import java.text.NumberFormat
import java.util.Locale

fun formatMoney(amount: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(amount)

fun formatMoneyWithCurrency(amount: Double, code: String, locale: Locale = Locale.getDefault()): String =
    "${formatMoney(amount, locale)}\u00A0${currencySymbol(code)}"

fun currencySymbol(code: String): String = when (code) {
    "UAH" -> "₴"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> code
}
