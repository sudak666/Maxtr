package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TransactionEntity
import java.time.LocalDate
import java.util.UUID

enum class CsvImportErrorReason { TOO_FEW_COLUMNS, UNKNOWN_TYPE, UNKNOWN_WALLET, INVALID_AMOUNT, INVALID_DATE, SAME_WALLETS, INVALID_TRANSFER_AMOUNT }
data class CsvImportError(val row: Int, val reason: CsvImportErrorReason, val detail: String? = null)
data class CsvImportPreview(val transactions: List<TransactionEntity>, val errors: List<CsvImportError>)

internal data class CsvDialect(val header: List<String>, val income: String, val expense: String, val transfer: String)

internal fun csvDialect(language: String) = if (language == "en") CsvDialect(
    listOf("Date", "Type", "Category", "Subcategory", "Wallet", "Amount", "Currency", "To wallet", "Transfer amount", "Transfer currency", "Comment"),
    "Income", "Expense", "Transfer",
) else CsvDialect(
    listOf("Дата", "Тип", "Категорія", "Підкатегорія", "Гаманець", "Сума", "Валюта", "Куди", "Сума переказу", "Валюта переказу", "Коментар"),
    "Дохід", "Витрата", "Переказ",
)

/** The Android counterpart of analytics-csv.js's deliberately small CSV dialect. */
class TransactionsCsvRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
) {
    suspend fun export(language: String): String {
        val wallets = db.walletDao().getAllOnce().associateBy { it.id }
        val transactions = db.transactionDao().getAllOnce()
        require(transactions.isNotEmpty()) { "Немає транзакцій для експорту" }
        val dialect = csvDialect(language)
        val rows = transactions.sortedBy { it.date }.map { tx ->
            listOf(
                tx.date,
                when (tx.type.uppercase()) { "INCOME" -> dialect.income; "EXPENSE" -> dialect.expense; else -> dialect.transfer },
                tx.category,
                tx.subcategory.orEmpty(),
                wallets[tx.walletId]?.name.orEmpty(),
                tx.amount.toCsvNumber(),
                tx.currency,
                tx.targetWalletId?.let { wallets[it]?.name }.orEmpty(),
                tx.targetAmount?.toCsvNumber().orEmpty(),
                tx.targetCurrency.orEmpty(),
                tx.comment.orEmpty(),
            )
        }
        return "\uFEFF" + (listOf(dialect.header) + rows).joinToString("\r\n") { row -> row.joinToString(";") { csvEscape(it) } }
    }

    suspend fun parse(text: String): CsvImportPreview {
        val rows = parseCsv(text)
        require(rows.isNotEmpty()) { "Файл порожній" }
        require(rows.first().size == 11) { "Невірний формат CSV: очікується 11 колонок" }
        val wallets = db.walletDao().getAllOnce()
        val walletByName = wallets.associateBy { it.name }
        val valid = mutableListOf<TransactionEntity>()
        val errors = mutableListOf<CsvImportError>()
        rows.drop(1).forEachIndexed { index, row ->
            val rowNumber = index + 2
            fun reject(reason: CsvImportErrorReason, detail: String? = null) { errors += CsvImportError(rowNumber, reason, detail) }
            if (row.size < 11) { reject(CsvImportErrorReason.TOO_FEW_COLUMNS); return@forEachIndexed }
            val type = when (row[1].trim().lowercase()) {
                "дохід", "income" -> "INCOME"
                "витрата", "expense" -> "EXPENSE"
                "переказ", "transfer" -> "TRANSFER"
                else -> { reject(CsvImportErrorReason.UNKNOWN_TYPE, row[1]); return@forEachIndexed }
            }
            val wallet = walletByName[row[4].trim()]
            if (wallet == null) { reject(CsvImportErrorReason.UNKNOWN_WALLET, row[4]); return@forEachIndexed }
            val amount = row[5].csvNumber()
            if (amount == null || amount <= 0.0) { reject(CsvImportErrorReason.INVALID_AMOUNT); return@forEachIndexed }
            val date = runCatching { LocalDate.parse(row[0].trim()) }.getOrNull()
            if (date == null) { reject(CsvImportErrorReason.INVALID_DATE); return@forEachIndexed }
            val targetWallet = if (type == "TRANSFER") walletByName[row[7].trim()] else null
            if (type == "TRANSFER" && targetWallet == null) { reject(CsvImportErrorReason.UNKNOWN_WALLET, row[7]); return@forEachIndexed }
            if (targetWallet?.id == wallet.id) { reject(CsvImportErrorReason.SAME_WALLETS); return@forEachIndexed }
            val targetAmount = if (type == "TRANSFER") row[8].csvNumber() else null
            if (type == "TRANSFER" && (targetAmount == null || targetAmount <= 0.0)) { reject(CsvImportErrorReason.INVALID_TRANSFER_AMOUNT); return@forEachIndexed }
            val now = System.currentTimeMillis()
            valid += TransactionEntity(
                id = "tx_${now}_${UUID.randomUUID()}", createdAt = now + index, type = type,
                amount = amount, currency = row[6].trim().ifBlank { wallet.currency }, date = date.toString(),
                walletId = wallet.id, targetWalletId = targetWallet?.id, targetAmount = targetAmount,
                targetCurrency = if (type == "TRANSFER") row[9].trim().ifBlank { wallet.currency } else null,
                category = row[2].trim(), subcategory = row[3].trim().ifBlank { null },
                comment = row[10].trim(), tags = "",
            )
        }
        return CsvImportPreview(valid, errors)
    }

    suspend fun import(uid: String, profileId: String, transactions: List<TransactionEntity>) {
        val collection = firestore.collection("users").document(uid).collection("max_tracker")
            .document(profileDocName("finance", profileId)).collection("transactions")
        transactions.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.set(collection.document(it.id), it.toRemoteMap()) }
            batch.commit().await()
        }
        db.transactionDao().insertAll(transactions)
    }
}

private fun csvEscape(value: String): String =
    if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

private fun Double.toCsvNumber() = toString().replace('.', ',')
private fun String.csvNumber(): Double? = replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() }

internal fun parseCsv(input: String): List<List<String>> {
    val text = input.removePrefix("\uFEFF")
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>(); val field = StringBuilder(); var quoted = false; var i = 0
    while (i < text.length) {
        val c = text[i]
        if (quoted) {
            if (c == '"' && i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ }
            else if (c == '"') quoted = false else field.append(c)
        } else when (c) {
            '"' -> quoted = true
            ';' -> { row += field.toString(); field.clear() }
            '\r' -> Unit
            '\n' -> { row += field.toString(); field.clear(); rows += row; row = mutableListOf() }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows.filterNot { it.size == 1 && it[0].isBlank() }
}
