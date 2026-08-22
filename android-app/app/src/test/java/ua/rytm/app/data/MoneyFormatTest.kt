package ua.rytm.app.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.currencySymbol
import ua.rytm.app.ui.screens.finance.formatMoney
import ua.rytm.app.ui.screens.finance.formatMoneyWithCurrency
import ua.rytm.app.ui.screens.finance.formatMoneyInputWithCurrency
import ua.rytm.app.ui.screens.finance.formatSignedMoneyWithCurrency

class MoneyFormatTest {
    @Test fun supportedCurrencySymbolsAreExact() {
        assertEquals("₴", currencySymbol("UAH"))
        assertEquals("$", currencySymbol("USD"))
        assertEquals("€", currencySymbol("EUR"))
        assertEquals("£", currencySymbol("GBP"))
        assertEquals("PLN", currencySymbol("PLN"))
    }

    @Test fun moneyUsesRequestedLocale() {
        assertEquals("1 234 567,89", formatMoney(1_234_567.89, Locale.forLanguageTag("uk-UA")))
        assertEquals("1,234,567.89", formatMoney(1_234_567.89, Locale.US))
    }

    @Test fun amountAndCurrencyAreFormattedCentrally() {
        assertEquals("1 234,5 ₴", formatMoneyWithCurrency(1_234.5, "UAH", Locale.forLanguageTag("uk-UA")))
        assertEquals("1,234.5 PLN", formatMoneyWithCurrency(1_234.5, "PLN", Locale.US))
    }

    @Test fun signedAmountsUseOneConsistentSign() {
        assertEquals("+12.5\u00A0$", formatSignedMoneyWithCurrency(12.5, "USD", showPlus = true, locale = Locale.US))
        assertEquals("−12.5\u00A0$", formatSignedMoneyWithCurrency(-12.5, "USD", showPlus = true, locale = Locale.US))
        assertEquals("0\u00A0$", formatSignedMoneyWithCurrency(0.0, "USD", showPlus = true, locale = Locale.US))
    }

    @Test fun editableDecimalInputAcceptsBothSeparators() {
        assertEquals("12.5\u00A0$", formatMoneyInputWithCurrency("12,5", "USD", Locale.US))
        assertEquals("—\u00A0$", formatMoneyInputWithCurrency(" — ", "USD", Locale.US))
    }
}
