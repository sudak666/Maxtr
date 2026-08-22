package ua.rytm.app.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.currencySymbol
import ua.rytm.app.ui.screens.finance.formatMoney

class MoneyFormatTest {
    @Test fun supportedCurrencySymbolsAreExact() {
        assertEquals("₴", currencySymbol("UAH"))
        assertEquals("$", currencySymbol("USD"))
        assertEquals("€", currencySymbol("EUR"))
        assertEquals("£", currencySymbol("GBP"))
        assertEquals("PLN", currencySymbol("PLN"))
    }

    @Test fun moneyAlwaysUsesUkUaGroupingRegardlessOfDeviceLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("1 234 567,89", formatMoney(1_234_567.89))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
