package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StatementTableTest {
    @Test fun amountsInBankFormats() {
        assertEquals(-1234.5, StatementTable.parseAmount("−1 234,50")!!, 0.001)
        assertEquals(-1234.5, StatementTable.parseAmount("-1234.50")!!, 0.001)
        assertEquals(1234.5, StatementTable.parseAmount("1 234,50 UAH")!!, 0.001)
        assertEquals(1234.5, StatementTable.parseAmount("1.234,50")!!, 0.001)
        assertEquals(1234.5, StatementTable.parseAmount("1,234.50")!!, 0.001)
        assertEquals(-250.0, StatementTable.parseAmount("(250.00)")!!, 0.001)
        assertNull(StatementTable.parseAmount(""))
        assertNull(StatementTable.parseAmount("—"))
    }

    @Test fun datesInBankFormats() {
        assertEquals(LocalDate.of(2026, 11, 3) to "14:05:00", StatementTable.parseDate("03.11.2026 14:05"))
        assertEquals(LocalDate.of(2026, 11, 3) to "00:00:00", StatementTable.parseDate("2026-11-03"))
        assertEquals(LocalDate.of(2026, 11, 3) to "09:07:30", StatementTable.parseDate("03/11/2026 9:07:30"))
        // Excel serial day: 46329.5 = 2026-11-03 12:00.
        assertEquals(LocalDate.of(2026, 11, 3) to "12:00:00", StatementTable.parseDate("46329.5"))
        assertNull(StatementTable.parseDate("Дата операції"))
        assertNull(StatementTable.parseDate("31.02.2026"))
        assertNull(StatementTable.parseDate("1250.00"))
    }

    @Test fun cp1251SemicolonCsvWithSignedAmount() {
        val csv = "Виписка по рахунку\r\nДата;Опис операції;Сума, грн\r\n03.11.2026 14:05;\"АТБ; Київ\";-320,50\r\n02.11.2026 10:00;Зарплата;15000,00\r\nРазом;;14679,50\r\n"
        val table = StatementTable.read(csv.toByteArray(charset("windows-1251")))!!
        val mapping = StatementTable.guess(table)
        assertEquals(StatementTable.Mapping(headerRow = 1, date = 0, amount = 2, debit = null, credit = null, details = 1), mapping)
        val result = StatementTable.apply(table, mapping)
        assertEquals(
            listOf(StatementRow("2026-11-03", "14:05:00", "АТБ; Київ", -320.5), StatementRow("2026-11-02", "10:00:00", "Зарплата", 15000.0)),
            result.statement.rows,
        )
        assertEquals(1, result.skipped) // «Разом» has no date
    }

    @Test fun separateDebitAndCreditColumns() {
        val csv = "Date,Description,Debit,Credit\n2026-11-03,Coffee,65.00,\n2026-11-02,Salary,,1500.00\n"
        val table = StatementTable.read(csv.toByteArray())!!
        val mapping = StatementTable.guess(table)
        assertEquals(StatementTable.Mapping(0, 0, null, 2, 3, 1), mapping)
        assertEquals(listOf(-65.0, 1500.0), StatementTable.apply(table, mapping).statement.rows.map { it.amount })
    }

    @Test fun xlsxWithInlineAndSharedStrings() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write("<sst><si><t>Дата</t></si><si><t>Сума</t></si><si><t>Призначення</t></si></sst>".toByteArray())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(
                """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                <row r="2"><c r="A2"><v>46329.5</v></c><c r="B2"><v>-99.9</v></c><c r="C2" t="inlineStr"><is><t>Аптека</t></is></c></row>
                </sheetData></worksheet>""".toByteArray(),
            )
        }
        val table = StatementTable.read(out.toByteArray())!!
        val result = StatementTable.apply(table, StatementTable.guess(table))
        assertEquals(listOf(StatementRow("2026-11-03", "12:00:00", "Аптека", -99.9)), result.statement.rows)
    }

    @Test fun legacyXlsAndNonTables() {
        assertTrue(StatementTable.isLegacyXls(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0, 0)))
        assertNull(StatementTable.read("просто текст без колонок".toByteArray()))
    }
}
