package ua.rytm.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one place dates become user-facing text.
 *
 * The app had three different approaches: this `dd.MM.yyyy` pattern in
 * DatePickerField, a hand-rolled ISO-string reversal in the transaction list,
 * and — in the shift day sheet — the raw `LocalDate.toString()`, i.e. the
 * user was shown `2026-08-25`, a technical format.
 */
val NumericDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** "25 серпня, понеділок" — for headers where the day matters more than the year. */
@Composable
@ReadOnlyComposable
fun formatLongDate(date: LocalDate): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    return date.format(DateTimeFormatter.ofPattern("d MMMM, EEEE", locale))
}

/** "25.08.2026" — compact, locale-independent. */
fun formatNumericDate(date: LocalDate): String = date.format(NumericDateFormatter)
