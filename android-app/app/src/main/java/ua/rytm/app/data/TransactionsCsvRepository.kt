package ua.rytm.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.data.local.TransactionEntity
import java.time.LocalDate
import java.util.UUID

data class CsvImportError(val row: Int, val reason: String)
data class CsvImportPreview(val transactions: List<TransactionEntity>, val errors: List<CsvImportError>)

/** The Android counterpart of analytics-csv.js's deliberately small CSV dialect. */
class TransactionsCsvRepository(
    private val db: RytmDatabase,
    private val firestore: FirebaseFirestore,
) {
    suspend fun export(): String {
        val wallets = db.walletDao().getAllOnce().associateBy { it.id }
        val transactions = db.transactionDao().getAllOnce()
        require(transactions.isNotEmpty()) { "Немає транзакцій для експорту" }
        val header = listOf("Дата", "Тип", "Категорія", "Підкатегорія", "Гаманець", "Сума", "Валюта", "Куди", "Сума переказу", "Валюта переказу", "Коментар")
        val rows = transactions.sortedBy { it.date }.map { tx ->
            listOf(
                tx.date,
                when (tx.type.uppercase()) { "INCOME" -> "Дохід"; "EXPENSE" -> "Витрата"; else -> "Переказ" },
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
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(";") { csvEscape(it) } }
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
            fun reject(reason: String) { errors += CsvImportError(rowNumber, reason) }
            if (row.size < 11) { reject("недостатньо колонок"); return@forEachIndexed }
            val type = when (row[1].trim().lowercase()) {
                "дохід", "income" -> "INCOME"
                "витрата", "expense" -> "EXPENSE"
                "переказ", "transfer" -> "TRANSFER"
                else -> { reject("невідомий тип: ${row[1]}"); return@forEachIndexed }
            }
            val wallet = walletByName[row[4].trim()]
            if (wallet == null) { reject("невідомий гаманець: ${row[4]}"); return@forEachIndexed }
            val amount = row[5].csvNumber()
            if (amount == null || amount <= 0.0) { reject("невірна сума"); return@forEachIndexed }
            val date = runCatching { LocalDate.parse(row[0].trim()) }.getOrNull()
            if (date == null) { reject("невірна дата"); return@forEachIndexed }
            val targetWallet = if (type == "TRANSFER") walletByName[row[7].trim()] else null
            if (type == "TRANSFER" && targetWallet == null) { reject("невідомий гаманець: ${row[7]}"); return@forEachIndexed }
            if (targetWallet?.id == wallet.id) { reject("гаманці переказу мають відрізнятися"); return@forEachIndexed }
            val targetAmount = if (type == "TRANSFER") row[8].csvNumber() else null
            if (type == "TRANSFER" && (targetAmount == null || targetAmount <= 0.0)) { reject("невірна сума переказу"); return@forEachIndexed }
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
