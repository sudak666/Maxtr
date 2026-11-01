package ua.rytm.app.data

import org.w3c.dom.Element
import ua.rytm.app.bank.BankQuickSaveReceiver
import ua.rytm.app.bank.WalletMatcher
import ua.rytm.app.data.local.TransactionEntity
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import ua.rytm.app.ui.screens.finance.Wallet
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class StatementRow(val date: String, val time: String, val details: String, val amount: Double)
const val MONO_PACKAGE = "com.ftband.mono"
const val PUMB_PACKAGE = "com.fuib.android.spot.online"

data class Statement(val cardLast4: String?, val rows: List<StatementRow>, val bankPackage: String = MONO_PACKAGE, val totals: BankTotals? = null)

/**
 * Reads monobank's card statement. The file is named .xls but is a real xlsx:
 * columns are date, details, MCC, signed amount in card currency, amount in
 * operation currency, and so on. The signed card amount is what moved the balance.
 */
object MonobankStatement {
    private val DATE = Regex("""(\d{2})\.(\d{2})\.(\d{4}) (\d{2}:\d{2}:\d{2})""")
    private val CARD = Regex("""\d{4} \*{4} \*{4} (\d{4})""")

    fun isXlsx(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun parse(bytes: ByteArray): Statement {
        val files = unzip(bytes)
        val shared = files["xl/sharedStrings.xml"]?.let { xml(it).byTag("si").map(Element::getTextContent) }.orEmpty()
        val sheet = xml(files["xl/worksheets/sheet1.xml"] ?: error("no sheet"))
        var card: String? = null
        val rows = mutableListOf<StatementRow>()
        sheet.byTag("row").forEach { row ->
            val cells = row.childElements("c").associate { c ->
                val raw = c.textContent
                c.getAttribute("r").takeWhile(Char::isLetter) to if (c.getAttribute("t") == "s") shared.getOrElse(raw.toInt()) { "" } else raw
            }
            val first = cells["A"].orEmpty()
            CARD.find(first)?.let { card = card ?: it.groupValues[1] }
            val date = DATE.matchEntire(first.trim()) ?: return@forEach
            val amount = cells["D"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it != 0.0 } ?: return@forEach
            val (d, m, y, time) = date.destructured
            rows += StatementRow("$y-$m-$d", time, cells["B"].orEmpty().trim(), amount)
        }
        return Statement(card, rows)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") out[entry.name] = zip.readBytes()
            }
        }
        return out
    }

    private fun xml(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance()
        // A statement is an untrusted file: no external entities.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.byTag(tag: String): List<Element> =
        getElementsByTagName(tag).let { list -> List(list.length) { list.item(it) as Element } }

    private fun Element.childElements(tag: String): List<Element> =
        childNodes.let { list -> List(list.length) { list.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag } }
}

/**
 * Turns a statement into new transactions. Ids are deterministic, so importing
 * the same file twice adds nothing; an existing operation of the same type,
 * date and amount (a bank-push quick-add) also counts as already there.
 */
fun Statement.toTransactions(
    wallets: List<Wallet>,
    existing: List<Transaction>,
    categories: Map<TxType, List<String>>,
    createdBy: String?,
    createdByName: String?,
): List<TransactionEntity> {
    val wallet = WalletMatcher.pick(wallets, cardLast4, bankPackage, "UAH") ?: wallets.firstOrNull() ?: return emptyList()
    val ids = existing.mapTo(HashSet()) { it.id }
    val taken = existing.groupingBy { Triple(it.type, it.date.take(10), it.amount) }.eachCount().toMutableMap()
    return rows.mapNotNull { row ->
        val type = if (row.amount > 0) TxType.INCOME else TxType.EXPENSE
        val amount = kotlin.math.abs(row.amount)
        val id = "stmt_" + MessageDigest.getInstance("SHA-256").digest("${row.date} ${row.time}|${row.amount}|${row.details}".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(24)
        if (id in ids) return@mapNotNull null
        val key = Triple(type, row.date, amount)
        val same = taken[key] ?: 0
        if (same > 0) { taken[key] = same - 1; return@mapNotNull null }
        val cats = categories[type].orEmpty()
        val category = BankQuickSaveReceiver.confidentCategory(existing, cats, type == TxType.INCOME, row.details)
            ?: cats.firstOrNull { it == "Інше" } ?: cats.lastOrNull() ?: "Інше"
        Transaction(
            id = id, type = type, amount = amount, currency = wallet.currency, date = row.date, walletId = wallet.id,
            category = category, comment = row.details.ifBlank { null }, createdBy = createdBy, createdByName = createdByName,
        ).toEntity()
    }
}
