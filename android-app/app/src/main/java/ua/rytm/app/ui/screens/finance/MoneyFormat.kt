package ua.rytm.app.ui.screens.finance

import java.text.NumberFormat
import java.util.Locale

// Mirrors the PWA's `amount.toLocaleString('uk-UA')` calls throughout
// js/analytics-csv.js (grouped thousands, comma decimal point).
private val ukFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.Builder().setLanguage("uk").setRegion("UA").build()).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun formatMoney(amount: Double): String = ukFormat.format(amount)

fun currencySymbol(code: String): String = when (code) {
    "UAH" -> "₴"
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> code
}
