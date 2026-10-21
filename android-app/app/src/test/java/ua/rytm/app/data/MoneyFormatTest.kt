package ua.rytm.app.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.currencySymbol
import ua.rytm.app.ui.screens.finance.formatMoney
import ua.rytm.app.ui.screens.finance.parseMoneyInput

class MoneyFormatTest {
    @Test
    fun `money input accepts Ukrainian decimal comma`() {
        assertEquals(233.10, parseMoneyInput("233,10"))
        assertEquals(1234.56, parseMoneyInput("1 234,56"))
        assertEquals(null, parseMoneyInput("12,3,4"))
    }

    @Test fun supportedCurrencySymbolsAreExact() {
        assertEquals("₴", currencySymbol("UAH"))
        assertEquals("$", currencySymbol("USD"))
        assertEquals("€", currencySymbol("EUR"))
        assertEquals("£", currencySymbol("GBP"))
        assertEquals("PLN", currencySymbol("PLN"))
    }

    @Test fun moneyUsesActivePresentationLocale() {
        assertEquals("1,234,567.89", formatMoney(1_234_567.89, Locale.US))
        assertEquals("1 234 567,89", formatMoney(1_234_567.89, Locale.forLanguageTag("uk-UA")))
    }

    @Test fun kopecksAlwaysTwoDigitsWholeAmountsShort() {
        val uk = Locale.forLanguageTag("uk-UA")
        assertEquals("319,20", formatMoney(319.2, uk))
        assertEquals("35", formatMoney(35.0, uk))
        assertEquals("−31,90", formatMoney(-31.9, uk))
        assertEquals("116,94", formatMoney(116.94, uk))
    }
}
