package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvDialectTest {
    @Test
    fun exportDialectIsCompleteInBothLanguagesAndParserAcceptsQuotedFields() {
        val uk = csvDialect("uk")
        val en = csvDialect("en")
        assertEquals(11, uk.header.size)
        assertEquals(11, en.header.size)
        assertEquals(listOf("Дохід", "Витрата", "Переказ"), listOf(uk.income, uk.expense, uk.transfer))
        assertEquals(listOf("Income", "Expense", "Transfer"), listOf(en.income, en.expense, en.transfer))

        val parsed = parseCsv("\uFEFFDate;Type;Comment\r\n2026-08-22;Income;\"one; \"\"two\"\"\"")
        assertEquals(listOf("Date", "Type", "Comment"), parsed[0])
        assertEquals("one; \"two\"", parsed[1][2])
        assertTrue(parsed.none { it.isEmpty() })
    }

    // AUDIT_2026-10 P4-02: rows past firestore.rules' limits are rejected at preview, not by the server.
    @Test
    fun rowLimitsMatchTheServerRules() {
        assertNull(csvRowLimitError(999_999_999.0, null, "Їжа", null, "ok"))
        assertEquals(CsvImportErrorReason.AMOUNT_TOO_LARGE, csvRowLimitError(1_000_000_000.0, null, "Їжа", null, ""))
        assertEquals(CsvImportErrorReason.AMOUNT_TOO_LARGE, csvRowLimitError(10.0, 1_000_000_000.0, "", null, ""))
        assertEquals(CsvImportErrorReason.TEXT_TOO_LONG, csvRowLimitError(10.0, null, "Їжа", null, "x".repeat(501)))
        assertEquals(CsvImportErrorReason.TEXT_TOO_LONG, csvRowLimitError(10.0, null, "x".repeat(121), null, ""))
        assertEquals(CsvImportErrorReason.TEXT_TOO_LONG, csvRowLimitError(10.0, null, "Їжа", "x".repeat(121), ""))
    }

    // AUDIT_2026-10 P4-05: no scientific notation in exported amounts.
    @Test
    fun exportedNumbersArePlain() {
        assertEquals("15000000", 15_000_000.0.toCsvNumber())
        assertEquals("100", 100.0.toCsvNumber())
        assertEquals("12,5", 12.5.toCsvNumber())
        assertEquals("0,01", 0.01.toCsvNumber())
    }
}
