package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.compactCardNumber
import ua.rytm.app.ui.screens.finance.StatementText

/** Statement lines and card numbers as the history row and pickers show them (2026-11-28). */
class StatementTextTest {
    @Test fun dropsCityCountryAndGateway() {
        assertEquals("KOLO", StatementText.clean("KOLO KIYEV UA"))
        assertEquals("VODAFONE", StatementText.clean("RPAY *VODAFONE KYIV"))
        assertEquals("Daineko Yuliia", StatementText.clean("Daineko Yuliia DNIPRO UA"))
        assertEquals("Avrora Multimarket", StatementText.clean("Avrora Multimarket KIYEV UA"))
    }

    @Test fun dropsReferencesAndForeignCities() {
        assertEquals("NovaPay", StatementText.clean("NovaPay 380444962757"))
        assertEquals("Google Play Pass", StatementText.clean("Google Play Pass London GB"))
    }

    // «Продукти · Коло» over «KOLO» said it twice; the check crosses alphabets.
    @Test fun mentionsAcrossAlphabets() {
        assertEquals(true, StatementText.mentions("Продукти · Коло", "KOLO"))
        assertEquals(true, StatementText.mentions("Мобільний зв'язок · Vodafone", "VODAFONE"))
        assertEquals(true, StatementText.mentions("Сільпо", "SILPO"))
        assertEquals(false, StatementText.mentions("Продукти", "KOLO"))
    }

    @Test fun leavesOwnCommentsAlone() {
        assertEquals("Сергій ключі", StatementText.clean("Сергій ключі"))
        assertEquals("UA", StatementText.clean("UA"))
    }

    @Test fun compactsMaskedCardNumbers() {
        assertEquals("Monobank ••5129", compactCardNumber("Monobank 444111******5129"))
        assertEquals("Кредитка Пумб 3151", compactCardNumber("Кредитка Пумб 3151"))
        assertEquals("ПУМБ", compactCardNumber("ПУМБ"))
    }
}
