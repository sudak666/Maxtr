package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyConversionTest {
    @Test fun sameCurrencyIsUnchanged() = assertEquals(12.345, convertCurrencyAmount(12.345, "EUR", "EUR", emptyMap()), 0.0)
    @Test fun liveRatesOverrideFallbacks() = assertEquals(50.0, convertCurrencyAmount(1.0, "USD", "UAH", mapOf("USD" to 50.0)), 0.0)
    @Test fun crossRateUsesUahBaseAndRoundsToCents() = assertEquals(93.18, convertCurrencyAmount(100.0, "USD", "EUR", mapOf("USD" to 41.0, "EUR" to 44.0)), 0.0)
    @Test fun allSupportedFallbackCurrenciesConvert() {
        listOf("UAH", "USD", "EUR", "GBP", "PLN").forEach { currency ->
            val result = convertCurrencyAmount(1.0, currency, "UAH", emptyMap())
            assertEquals(true, result > 0)
        }
    }
}
