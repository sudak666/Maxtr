package ua.rytm.app.data

import ua.rytm.app.data.local.TransactionEntity
import kotlin.math.abs

/**
 * What a Monobank sync does with freshly fetched rows before saving them
 * (audit 2026-11, A-01 and A-02). Pure, so it is unit-tested.
 */
object MonobankMerge {
    /** Both halves of a move between the user's own cards land within this many seconds. */
    const val TRANSFER_WINDOW_SEC = 120L

    /** A fetched row plus the bank's timestamp, which the stored date drops. */
    data class Row(val tx: TransactionEntity, val timeSec: Long)

    data class Result(
        /** New rows to insert. */
        val added: List<TransactionEntity>,
        /** Existing rows that turned out to be the same operation; now carry the bank's id. */
        val claimed: List<TransactionEntity>,
    )

    /**
     * 1. An expense on one mapped card and an income of the same amount and
     *    currency on another within [TRANSFER_WINDOW_SEC] is one move of the
     *    user's own money (card → jar, card → card): it becomes a transfer
     *    instead of an expense plus an income that inflated both month totals.
     *    Its monobankId joins both statement ids, so neither half comes back.
     * 2. A row whose wallet already holds an operation of the same type, date
     *    and amount with no bank id (saved earlier from the bank's push
     *    notification) is that operation: the stored one is kept, with the
     *    user's category and comment, and only gains the bank id. Each stored
     *    operation is claimed once, so two equal purchases on one day stay two.
     *    Same wallet only: a cash purchase of the same sum must not move to the card.
     */
    fun merge(rows: List<Row>, existing: List<TransactionEntity>): Result {
        val paired = pairTransfers(rows)
        val free = existing.filter { it.monobankId == null }
            .groupBy { key(it) }
            .mapValues { (_, list) -> list.toMutableList() }
        val added = mutableListOf<TransactionEntity>()
        val claimed = mutableListOf<TransactionEntity>()
        paired.forEach { tx ->
            val match = free[key(tx)]?.removeFirstOrNull()
            if (match != null) claimed += match.copy(monobankId = tx.monobankId) else added += tx
        }
        return Result(added, claimed)
    }

    private fun key(t: TransactionEntity) = listOf(t.type, t.walletId, t.targetWalletId ?: "", t.date.take(10), Math.round(t.amount * 100))

    private fun pairTransfers(rows: List<Row>): List<TransactionEntity> {
        val used = HashSet<Int>()
        val out = mutableListOf<TransactionEntity>()
        rows.forEachIndexed { i, out1 ->
            if (i in used) return@forEachIndexed
            val e = out1.tx
            if (e.type != "EXPENSE") return@forEachIndexed
            val j = rows.indices.firstOrNull { k ->
                val inc = rows[k].tx
                k !in used && k != i && inc.type == "INCOME" && inc.walletId != e.walletId &&
                    inc.currency == e.currency && abs(inc.amount - e.amount) < 0.005 &&
                    abs(rows[k].timeSec - out1.timeSec) <= TRANSFER_WINDOW_SEC
            } ?: return@forEachIndexed
            used += i; used += j
            val inc = rows[j].tx
            out += e.copy(
                type = "TRANSFER",
                targetWalletId = inc.walletId,
                category = BuiltinCategories.TRANSFER,
                subcategory = null,
                monobankId = e.monobankId + ID_SEPARATOR + inc.monobankId,
            )
        }
        rows.forEachIndexed { i, r -> if (i !in used) out += r.tx }
        return out.sortedBy { it.createdAt }
    }

    /** Joins the two statement ids of a merged transfer. */
    const val ID_SEPARATOR = "|"

    /** Every statement id already stored, including both halves of merged transfers. */
    fun knownIds(stored: List<String>): MutableSet<String> = stored.flatMapTo(HashSet()) { it.split(ID_SEPARATOR) }
}
