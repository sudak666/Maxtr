package ua.zminka.app.data.model

/**
 * Mirrors the transaction shape written by js/finance.js's addTransaction()
 * (see CLAUDE.md's Firebase data model section) — field names/types must
 * stay in lockstep with the web client since both write into the same
 * users/{uid}/max_tracker/finance/transactions subcollection.
 *
 * All fields need defaults for Firestore's no-arg-constructor
 * deserialization (toObject<Transaction>()).
 */
data class Transaction(
    val id: Long = 0L,
    val type: String = "expense", // "income" | "expense" | "transfer"
    val amount: Double = 0.0,
    val currency: String = "UAH",
    val category: String = "",
    val subcategory: String? = null,
    val tags: List<String> = emptyList(),
    val wallet: String = "",
    val targetWallet: String? = null,
    val targetAmount: Double? = null,
    val targetCurrency: String? = null,
    val date: String = "",
    val comment: String = "",
)

/** Mirrors AppState.wallets entries (js/state.js / js/settings-managers.js addWallet()). */
data class Wallet(
    val id: String = "",
    val name: String = "",
    val color: String = "#8b5cf6",
    val icon: String = "card",
    val currency: String = "UAH",
)

/**
 * The subset of the `finance` doc this app's core screens need. The full
 * doc also carries budgets/subcategories/tags/autoRules/recurring/goals/
 * subscription/widgets/notifSettings/profile — not modeled here yet since
 * nothing in this app's MVP reads or writes them. Extend as more of the
 * web app's feature set gets ported.
 */
data class FinanceDoc(
    val wallets: List<Wallet> = emptyList(),
    val categories: Map<String, List<String>> = mapOf(
        "income" to emptyList(),
        "expense" to emptyList(),
    ),
    val currencyRates: Map<String, Double> = emptyMap(),
    val updatedAt: Long = 0L,
)
