package ua.rytm.app.data

import androidx.annotation.StringRes
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import ua.rytm.app.R
import java.util.concurrent.TimeUnit

/**
 * AI month summary through the `insights` callable (functions/lib/insights.js).
 * Only per-category totals leave the phone — no transactions, comments,
 * merchants or wallets. Disclosed in privacy.html.
 */
class InsightsRepository(private val functions: () -> FirebaseFunctions = { FirebaseFunctions.getInstance() }) {
    data class Category(val name: String, val amount: Double, val previous: Double)
    data class Request(
        val month: String,
        val lang: String,
        val partial: Boolean,
        val income: Double,
        val expense: Double,
        val previousExpense: Double,
        val txCount: Int,
        val categories: List<Category>,
    )

    class InsightException(@StringRes val messageRes: Int, cause: Throwable? = null) : Exception(cause)

    suspend fun monthInsight(request: Request): String {
        val data = mapOf(
            "month" to request.month,
            "lang" to request.lang,
            "partial" to request.partial,
            "income" to request.income,
            "expense" to request.expense,
            "previousExpense" to request.previousExpense,
            "txCount" to request.txCount,
            "categories" to request.categories.map { mapOf("name" to it.name, "amount" to it.amount, "previous" to it.previous) },
        )
        val result = try {
            functions().getHttpsCallable("insights").apply { setTimeout(60, TimeUnit.SECONDS) }.call(data).await().getData() as? Map<*, *>
        } catch (e: FirebaseFunctionsException) {
            throw InsightException(if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) R.string.ai_summary_limit else R.string.ai_summary_failed, e)
        } catch (e: Exception) {
            throw InsightException(R.string.ai_summary_failed, e)
        }
        return (result?.get("text") as? String)?.takeIf { it.isNotBlank() } ?: throw InsightException(R.string.ai_summary_failed)
    }
}
