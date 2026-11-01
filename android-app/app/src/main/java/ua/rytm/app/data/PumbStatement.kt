package ua.rytm.app.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.StringWriter

data class PdfWord(val page: Int, val x: Float, val y: Float, val text: String)

/**
 * Reads PUMB's PDF statement by column position: its plain text order mixes
 * neighbouring rows. The operation date anchors each row, since the posting
 * date is empty for an operation still in progress. A row's other cells are
 * vertically centred around it, so rows split halfway between anchors.
 */
object PumbStatement {
    private val SPACES = Regex("""s+""")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val TIME = Regex("""\d{2}:\d{2}:\d{2}""")
    private val AMOUNT = Regex("""\d+\.\d{2}""")
    private val CARD = Regex("""\d{8}\*{4}(\d{4})""")
    private val DEBITS = Regex("""Всього\s+списань\s+за\s+період\s+([\d.]+)""")
    private val CREDITS = Regex("""Всього\s+зарахувань\s+за\s+період\s+([\d.]+)""")
    private val INCOME = setOf("Надходження", "Повернення") // "коштів" can land on another line
    private val KINDS = INCOME + setOf("Покупка", "Списання", "Оплата", "Перевірка рахунку")
    private const val DATE_MAX_X = 60f
    private const val TIME_DROP = 7f // the time sits one line under the date
    private const val MARGIN = 45f // first and last row of a page
    private const val AMOUNT_X = 205f
    private const val AMOUNT_MAX_X = 235f
    private const val DETAILS_X = 325f

    fun isPdf(bytes: ByteArray) = bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "%PDF"

    fun read(context: Context, bytes: ByteArray): Statement {
        PDFBoxResourceLoader.init(context.applicationContext)
        return PDDocument.load(bytes).use { doc ->
            val words = mutableListOf<PdfWord>()
            val stripper = object : PDFTextStripper() {
                override fun writeString(text: String, positions: MutableList<TextPosition>) {
                    positions.firstOrNull()?.let { words += PdfWord(currentPageNo, it.xDirAdj, it.yDirAdj, text.trim().replace(SPACES, " ")) }
                    super.writeString(text, positions)
                }
            }
            stripper.sortByPosition = true
            val out = StringWriter()
            stripper.writeText(doc, out)
            parse(words, out.toString())
        }
    }

    internal fun parse(words: List<PdfWord>, text: String): Statement {
        var card: String? = null
        val rows = mutableListOf<StatementRow>()
        words.groupBy { it.page }.toSortedMap().values.forEach { page ->
            val dates = page.filter { it.x < DATE_MAX_X && DATE.matchesAt(it.text, 0) }.sortedBy { it.y }
            dates.forEachIndexed { i, date ->
                val top = dates.getOrNull(i - 1)?.let { (it.y + date.y) / 2 } ?: (date.y - MARGIN)
                val bottom = dates.getOrNull(i + 1)?.let { (it.y + date.y) / 2 } ?: (date.y + MARGIN)
                val band = page.filter { it.y > top && it.y <= bottom }.sortedWith(compareBy({ it.y }, { it.x }))
                val time = band.firstOrNull { it.x < DATE_MAX_X && TIME.matches(it.text) && it.y - date.y < TIME_DROP * 2 }?.text.orEmpty()
                val amount = band.firstOrNull { it.x >= AMOUNT_X && it.x < AMOUNT_MAX_X && AMOUNT.matchesAt(it.text, 0) }?.let { AMOUNT.find(it.text)?.value?.toDoubleOrNull() }
                if (amount == null || amount == 0.0) return@forEachIndexed
                band.firstNotNullOfOrNull { CARD.find(it.text) }?.let { card = card ?: it.groupValues[1] }
                // PDFBox glues the kind onto the details cell it shares a line with,
                // so it is read from the text; the last match wins, since details
                // can start with a word like "Оплата".
                val cell = band.filter { it.x >= DETAILS_X && !CARD.containsMatchIn(it.text) }.joinToString(" ") { it.text }
                val found = KINDS.mapNotNull { kind -> cell.lastIndexOf(kind).takeIf { it >= 0 }?.let { it to kind } }.maxByOrNull { it.first }
                val details = found?.let { (at, kind) -> cell.removeRange(at, at + kind.length).replace(SPACES, " ").trim() } ?: cell
                rows += StatementRow(date.text.take(10), time, details, if (found?.second in INCOME) amount else -amount)
            }
        }
        val debits = DEBITS.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        val credits = CREDITS.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        return Statement(card, rows, PUMB_PACKAGE, if (debits != null && credits != null) BankTotals(debits, credits) else null)
    }
}

data class BankTotals(val debits: Double, val credits: Double)

/** What the rows sum to minus what the bank's own summary says, as (debits, credits). */
fun Statement.totalsGap(): Pair<Double, Double>? {
    val bank = totals ?: return null
    val debits = -rows.filter { it.amount < 0 }.sumOf { it.amount } - bank.debits
    val credits = rows.filter { it.amount > 0 }.sumOf { it.amount } - bank.credits
    return (debits to credits).takeIf { kotlin.math.abs(debits) > 0.01 || kotlin.math.abs(credits) > 0.01 }
}
