package ua.rytm.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ua.rytm.app.R

/** Presentation-only translation for PWA-compatible built-in values stored on the wire by name. */
@StringRes
fun builtInTextResource(value: String): Int? = when (value) {
    "Картка", "Card" -> R.string.builtin_wallet_card
    "Готівка", "Cash" -> R.string.builtin_wallet_cash
    "Зарплата", "Salary" -> R.string.builtin_category_salary
    "Премія", "Bonus" -> R.string.builtin_category_bonus
    "Підробіток", "Side job" -> R.string.builtin_category_side_job
    "Продукти", "Groceries" -> R.string.builtin_category_groceries
    "Кафе", "Cafe" -> R.string.builtin_category_cafe
    "Транспорт", "Transport" -> R.string.builtin_category_transport
    "Покупки", "Shopping" -> R.string.builtin_category_shopping
    "Комунальні", "Utilities" -> R.string.builtin_category_utilities
    "Здоров'я", "Health" -> R.string.builtin_category_health
    "Розваги", "Entertainment" -> R.string.builtin_category_entertainment
    "Інше", "Other" -> R.string.builtin_category_other
    "Внутрішній переказ", "Internal transfer" -> R.string.builtin_category_transfer
    "Денна зміна", "Day shift" -> R.string.builtin_shift_day
    "Нічна зміна", "Night shift" -> R.string.builtin_shift_night
    "Вихідний", "Day off" -> R.string.builtin_shift_day_off
    "День", "Day" -> R.string.builtin_shift_day_short
    "Ніч", "Night" -> R.string.builtin_shift_night_short
    "Вих", "Off" -> R.string.builtin_shift_off_short
    // The PWA's legacy 6-type seed set (js/core.js's LEGACY_SHIFT_TYPES,
    // used by accounts created before DEFAULT_SHIFT_TYPES was trimmed to 3)
    // — real accounts still carry these names verbatim in Firestore. Missing
    // here meant they rendered untranslated in English (reported live via
    // screenshot: "Ніч пізня" stayed Ukrainian while "Day off" translated
    // fine right next to it — the PWA itself doesn't translate these either,
    // but on a platform where every other label switches language, the
    // inconsistency reads as a bug rather than a shared, disclosed gap).
    "Денна (підвищена)", "Day (enhanced)" -> R.string.builtin_shift_day_enhanced
    "Нічна повна", "Night (full)" -> R.string.builtin_shift_night_full
    "Ніч рання", "Night (early)" -> R.string.builtin_shift_night_early
    "Ніч пізня", "Night (late)" -> R.string.builtin_shift_night_late
    "Кредит", "Loan" -> R.string.builtin_debt_credit
    "Я", "Me" -> R.string.builtin_profile_me
    "Банка", "Jar" -> R.string.builtin_monobank_jar
    else -> null
}

@Composable
fun localizedDomainText(value: String): String = builtInTextResource(value)?.let { stringResource(it) } ?: value
