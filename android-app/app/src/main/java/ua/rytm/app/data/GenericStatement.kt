package ua.rytm.app.data

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Any bank's statement saved as a table (CSV or xlsx): the user says which
 * column is the date, the amount and the description; monobank's xlsx and
 * PUMB's PDF keep their own readers. Owner, 2026-11-03: people bank with
 * PrivatBank, A-Bank, Raiffeisen and many more, and each lays its file out
 * differently, so a fixed parser per bank cannot cover them.
 */
object StatementTable {
    /** Old binary Excel (.xls, OLE2): not readable here, the user saves it as xlsx or CSV. */
    fun isLegacyXls(bytes: ByteArray) = bytes.size > 8 &&
        bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

    /** Rows of cells, or null when the file is not a table. */
    fun read(bytes: ByteArray): List<List<String>>? {
        val rows = if (MonobankStatement.isXlsx(bytes)) runCatching { readXlsx(bytes) }.getOrNull() else readCsv(decode(bytes))
        return rows?.map { row -> row.map { it.trim() } }?.filter { row -> row.any { it.isNotEmpty() } }?.takeIf { it.size >= 2 }
    }

    /** UTF-8 when the bytes are valid UTF-8, else Windows-1251 (older Ukrainian bank exports). */
    internal fun decode(bytes: ByteArray): String {
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { e -> if (e is CharacterCodingException) null else throw e }
        return (utf8 ?: String(bytes, Charset.forName("windows-1251"))).removePrefix("\uFEFF")
    }

    internal fun readCsv(text: String): List<List<String>>? {
        val sample = text.lineSequence().take(20).toList()
        val delimiter = listOf(';', ',', '\t').maxByOrNull { d -> sample.sumOf { line -> line.count { it == d } } } ?: return null
        if (sample.none { delimiter in it }) return null
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == delimiter -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") files[entry.name] = zip.readBytes()
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { xml(it).elements("si").map(Element::getTextContent) }.orEmpty()
        val sheet = xml(files["xl/worksheets/sheet1.xml"] ?: error("no sheet"))
        return sheet.elements("row").map { row ->
            val cells = sortedMapOf<Int, String>()
            row.elements("c").forEach { c ->
                val column = columnIndex(c.getAttribute("r").takeWhile(Char::isLetter))
                cells[column] = when (c.getAttribute("t")) {
                    "s" -> shared.getOrElse(c.textContent.trim().toIntOrNull() ?: -1) { "" }
                    else -> c.textContent // inlineStr, n, str: the text is the value
                }
            }
            if (cells.isEmpty()) emptyList() else List(cells.lastKey() + 1) { cells[it].orEmpty() }
        }
    }

    private fun columnIndex(letters: String): Int = letters.fold(0) { acc, ch -> acc * 26 + (ch.uppercaseChar() - 'A' + 1) } - 1

    private fun xml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.elements(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> List(list.length) { list.item(it) as Element } }

    // ---- columns ----

    /** Which column holds what; [amount] is a signed sum, or [debit]/[credit] are two unsigned ones. */
    data class Mapping(val headerRow: Int, val date: Int, val amount: Int?, val debit: Int?, val credit: Int?, val details: Int?) {
        val complete: Boolean get() = amount != null || (debit != null && credit != null)
    }

    private val DATE_WORDS = listOf("дата", "date")
    private val DEBIT_WORDS = listOf("списан", "видат", "дебет", "debit", "витрат", "расход")
    private val CREDIT_WORDS = listOf("зарах", "надход", "кредит", "credit", "приход")
    private val AMOUNT_WORDS = listOf("сума", "сумма", "amount")
    private val DETAILS_WORDS = listOf("опис", "призначен", "деталі", "коментар", "назва", "description", "details", "описание", "назначение")

    /** A first guess from the header names; the user confirms or changes it. */
    fun guess(table: List<List<String>>): Mapping {
        val headerRow = table.take(30).indexOfFirst { row -> row.count { cell -> cell.isNotBlank() } >= 2 && row.any { it.lowercase().containsAny(DATE_WORDS) } }
            .takeIf { it >= 0 } ?: 0
        val header = table[headerRow].map { it.lowercase() }
        fun find(words: List<String>, exclude: Set<Int> = emptySet()) = header.indices.firstOrNull { it !in exclude && header[it].containsAny(words) }
        val date = find(DATE_WORDS) ?: 0
        val debit = find(DEBIT_WORDS, setOf(date))
        val credit = find(CREDIT_WORDS, setOfNotNull(date, debit))
        val amount = if (debit != null && credit != null) null else find(AMOUNT_WORDS, setOf(date))
        val details = find(DETAILS_WORDS, setOfNotNull(date, amount, debit, credit))
        return Mapping(headerRow, date, amount, if (amount == null) debit else null, if (amount == null) credit else null, details)
    }

    /** Remembers a mapping for files with the same header line. */
    fun signature(table: List<List<String>>, headerRow: Int): String =
        table.getOrNull(headerRow).orEmpty().joinToString("|") { it.lowercase() }

    private fun String.containsAny(words: List<String>) = words.any { it in this }

    // ---- rows ----

    data class Result(val statement: Statement, val skipped: Int)

    fun apply(table: List<List<String>>, mapping: Mapping): Result {
        val rows = mutableListOf<StatementRow>()
        var skipped = 0
        table.drop(mapping.headerRow + 1).forEach { row ->
            val parsed = parseDate(row.getOrNull(mapping.date).orEmpty())
            val amount = when {
                mapping.amount != null -> parseAmount(row.getOrNull(mapping.amount).orEmpty())
                mapping.debit != null && mapping.credit != null -> {
                    val debit = parseAmount(row.getOrNull(mapping.debit).orEmpty())
                    val credit = parseAmount(row.getOrNull(mapping.credit).orEmpty())
                    if (debit == null && credit == null) null else (credit?.let(Math::abs) ?: 0.0) - (debit?.let(Math::abs) ?: 0.0)
                }
                else -> null
            }
            if (parsed == null || amount == null || amount == 0.0) { skipped++; return@forEach }
            rows += StatementRow(parsed.first.toString(), parsed.second, mapping.details?.let { row.getOrNull(it) }.orEmpty(), amount)
        }
        return Result(Statement(cardLast4 = null, rows = rows, bankPackage = ""), skipped)
    }

    private val DATE_TIME = Regex("""^(\d{1,4})[./-](\d{1,2})[./-](\d{1,4})(?:[ T,]+(\d{1,2}:\d{2}(?::\d{2})?))?""")

    /** dd.MM.yyyy, yyyy-MM-dd, dd/MM/yyyy (with an optional time), or an Excel serial day number. */
    internal fun parseDate(raw: String): Pair<LocalDate, String>? {
        val text = raw.trim()
        DATE_TIME.find(text)?.let { m ->
            val (a, b, c, time) = m.destructured
            val (y, mo, d) = if (a.length == 4) Triple(a, b, c) else Triple(c, b, a)
            val year = y.toIntOrNull()?.let { if (y.length == 2) 2000 + it else it } ?: return null
            val date = runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull() ?: return null
            return date to time.ifBlank { "00:00:00" }.let { if (it.length <= 5) "$it:00" else it }.padStart(8, '0')
        }
        val serial = text.replace(',', '.').toDoubleOrNull() ?: return null
        if (serial !in 20000.0..80000.0) return null // 1954..2119: anything else is not a date
        val date = LocalDate.of(1899, 12, 30).plusDays(serial.toLong())
        val seconds = Math.round((serial - serial.toLong()) * 86400).toInt().coerceIn(0, 86399)
        return date to "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    }

    /** «\u22121 234,50», «-1234.50», «1 234,50 UAH», «(250.00)» → signed number. */
    internal fun parseAmount(raw: String): Double? {
        var text = raw.trim().replace('\u2212', '-').replace("\u00A0", "").replace("\u202F", "").replace(" ", "")
        val negative = text.startsWith("(") && text.endsWith(")")
        text = text.removePrefix("(").removeSuffix(")").replace(Regex("""[^\d,.\-+]"""), "")
        if (text.isEmpty()) return null
        // «1.234,50» or «1,234.50»: the last separator is the decimal one.
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')
        text = when {
            lastComma > lastDot -> text.replace(".", "").replace(',', '.')
            else -> text.replace(",", "")
        }
        val value = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return if (negative) -kotlin.math.abs(value) else value
    }

    /** Short day format for the mapping preview. */
    val PREVIEW_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
}
