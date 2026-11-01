package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MonobankStatementTest {
    private fun xlsx(): ByteArray {
        val strings = listOf("Виписка по картці: 5375 **** **** 0158", "Дата i час операції", "12.03.2023 16:22:48", "АТБ", "10.03.2023 11:22:48", "Допомога")
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zip.write(("<sst>" + strings.joinToString("") { "<si><t>$it</t></si>" } + "</sst>").toByteArray())
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(
                """<worksheet><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c></row>
                <row r="2"><c r="A2" t="s"><v>1</v></c></row>
                <row r="3"><c r="A3" t="s"><v>2</v></c><c r="B3" t="s"><v>3</v></c><c r="D3"><v>-250.5</v></c></row>
                <row r="4"><c r="A4" t="s"><v>4</v></c><c r="B4" t="s"><v>5</v></c><c r="D4"><v>2000.0</v></c></row>
                </sheetData></worksheet>""".toByteArray(),
            )
        }
        return out.toByteArray()
    }

    @Test fun readsCardAndSignedRows() {
        val bytes = xlsx()
        assertTrue(MonobankStatement.isXlsx(bytes))
        assertFalse(MonobankStatement.isXlsx("Дата;Тип".toByteArray()))
        val statement = MonobankStatement.parse(bytes)
        assertEquals("0158", statement.cardLast4)
        assertEquals(
            listOf(StatementRow("2023-03-12", "16:22:48", "АТБ", -250.5), StatementRow("2023-03-10", "11:22:48", "Допомога", 2000.0)),
            statement.rows,
        )
    }
}
