package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PumbStatementTest {
    private fun row(y: Float, date: String, amount: String, kind: String, details: List<String>) = listOf(
        PdfWord(1, 38f, y, "$date $amount"), PdfWord(1, 44f, y + 7, "14:27:37"), PdfWord(1, 99f, y + 3, amount),
        PdfWord(1, 214f, y + 3, "$amount UAH"), PdfWord(1, 275f, y + 3, "0.00 UAH"),
        PdfWord(1, 330f, y + 3, "43141402****7343"),
    ) + details.mapIndexed { i, w -> PdfWord(1, 405f, y - 6 + i * 8, w) } + kind.split(' ').map { PdfWord(1, 530f, y + 3, it) }

    private val words = row(100f, "2026-10-01", "197.65", "Покупка", listOf("KOLO", "KIYEV")) +
        row(130f, "2026-10-02", "200.00", "Надходження", listOf("MONO")) +
        row(160f, "2026-10-03", "0.00", "Перевірка рахунку", listOf("CHECK")) +
        row(190f, "2026-10-04", "50.00", "Повернення коштів", listOf("SHOP"))

    @Test fun readsRowsByColumnAndSkipsZero() {
        val s = PumbStatement.parse(words, "")
        assertEquals("7343", s.cardLast4)
        assertEquals(listOf(
            StatementRow("2026-10-01", "14:27:37", "KOLO KIYEV", -197.65),
            StatementRow("2026-10-02", "14:27:37", "MONO", 200.0),
            StatementRow("2026-10-04", "14:27:37", "SHOP коштів", 50.0),
        ), s.rows)
    }

    @Test fun reportsGapToBankTotals() {
        val text = "Всього списань за період 197.65\nВсього зарахувань за період 150.00"
        assertEquals(0.0 to 100.0, PumbStatement.parse(words, text).totalsGap())
    }
}
