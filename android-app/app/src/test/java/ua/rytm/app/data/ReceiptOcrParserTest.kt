package ua.rytm.app.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReceiptOcrParserTest {
    @Test fun prioritizesTotalLine() = assertEquals(97.5, parseReceiptText("Хліб 120.00\nРАЗОМ 97,50").amount)
    @Test fun supportsThousandsSeparator() = assertEquals(1250.0, parseReceiptText("До сплати 1 250,00").amount)
    @Test fun extractsIsoDate() = assertEquals("2026-08-22", parseReceiptText("22.08.2026\nTOTAL 20.00").date)
    @Test fun rejectsImpossibleDate() = assertNull(parseReceiptText("40.18.2026\nTOTAL 20.00").date)
    @Test fun dateYearDoesNotWinAmountFallback() = assertEquals(97.5, parseReceiptText("22.08.2026\n97.50").amount)

    // AUDIT_2026-10 P4-04: same keywords and date window as js/receipt-ocr.js.
    @Test fun itogoKeywordAndYearWindow() {
        assertEquals(120.0, parseReceiptText("Хліб 250,00\nІтого 120,00").amount)
        assertNull(parseReceiptText("12.12.1999\nСума 10,00").date)
        assertNull(parseReceiptText("31.02.2026\nСума 10,00").date)
    }
}
