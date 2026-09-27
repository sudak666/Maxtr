package ua.rytm.app.ui.screens.debt

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val plainPaymentPattern = Regex("^\\d+([.,]\\d+)?$")

/** The PWA's plain-number payment check, plus the decimal comma Ukrainian keyboards type (stored normalized to '.'). */
fun parsePlainDebtAmount(value: String): Double? {
    val trimmed = value.trim()
    if (!plainPaymentPattern.matches(trimmed)) return null
    return trimmed.replace(',', '.').toDoubleOrNull()
}

private val debtDisplayDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Payment dates remain textual dd.MM.yyyy like the PWA; existing free text is preserved. */
fun normalizeDebtEntryDate(value: String): String {
    val trimmed = value.trim()
    return runCatching { LocalDate.parse(trimmed).format(debtDisplayDate) }.getOrDefault(trimmed)
}

fun autoDebtBalance(currentBalance: Double, amount: String): String {
    val parsed = parsePlainDebtAmount(amount) ?: return ""
    return formatDebtNumber(currentBalance - parsed)
}
