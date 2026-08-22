package ua.rytm.app.data

import org.junit.Assert.assertEquals
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
}
