package ua.rytm.app.ui.screens.finance

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Finds repeating expenses that look like subscriptions or regular bills
 * (Netflix, phone, rent) so the user can turn them into a recurring payment
 * with one tap. Local only, nothing leaves the device.
 *
 * A candidate: at least 3 expenses with the same comment, the last 3 gaps
 * all weekly (6–8 days) or all monthly (26–35 days), amounts within ±10% of
 * their median, the latest one recent enough that it still looks alive, and
 * no active recurring payment already covering it.
 */
object SubscriptionDetector {
    data class Candidate(
        val key: String,
        val comment: String,
        val amount: Double,
        val currency: String,
        val category: String,
        val walletId: String,
        val frequency: String, // "weekly" | "monthly"
        val nextDate: String,
        val occurrences: Int,
    )

    fun key(comment: String): String = comment.trim().lowercase().replace(Regex("""\s+"""), " ")

    fun detect(transactions: List<Transaction>, recurring: List<Recurring>, today: LocalDate, dismissed: Set<String> = emptySet()): List<Candidate> {
        val covered = recurring.filter { it.active }.map { key(it.comment) }.filter { it.isNotEmpty() }.toSet()
        return transactions.asSequence()
            .filter { it.type == TxType.EXPENSE && !it.comment.isNullOrBlank() }
            .mapNotNull { t -> t.comment?.let { key(it) to t } }
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.length >= 2 && it !in covered && it !in dismissed }
            .mapNotNull { (k, txs) -> candidate(k, txs, recurring, today) }
            .sortedByDescending { it.occurrences }
            .toList()
    }

    private fun candidate(key: String, txs: List<Transaction>, recurring: List<Recurring>, today: LocalDate): Candidate? {
        val sorted = txs.mapNotNull { t -> runCatching { LocalDate.parse(t.date) }.getOrNull()?.let { it to t } }
            .sortedBy { it.first }
            .distinctBy { it.first } // two on the same day are one occurrence
        if (sorted.size < 3) return null
        val last = sorted.takeLast(4)
        val gaps = last.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first) }
        val frequency = when {
            gaps.all { it in 26..35 } -> "monthly"
            gaps.all { it in 6..8 } -> "weekly"
            else -> return null
        }
        val amounts = last.map { it.second.amount }.sorted()
        val median = amounts[amounts.size / 2]
        if (median <= 0 || amounts.any { abs(it - median) > median * 0.10 }) return null
        val (lastDate, lastTx) = sorted.last()
        val period = if (frequency == "monthly") 31L else 7L
        // Stopped paying a while ago → not a live subscription any more.
        if (ChronoUnit.DAYS.between(lastDate, today) > period + period / 2) return null
        // Already a recurring payment for this amount and category, just named differently.
        if (recurring.any { it.active && it.category == lastTx.category && abs(it.amount - lastTx.amount) <= lastTx.amount * 0.10 }) return null
        val next = if (frequency == "monthly") lastDate.plusMonths(1) else lastDate.plusWeeks(1)
        return Candidate(
            key = key,
            comment = lastTx.comment.orEmpty().trim(),
            amount = lastTx.amount,
            currency = lastTx.currency,
            category = lastTx.category,
            walletId = lastTx.walletId,
            frequency = frequency,
            nextDate = (if (next.isBefore(today)) today else next).toString(),
            occurrences = sorted.size,
        )
    }
}
