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

/**
 * Mirrors AppState.shiftTypes entries (js/settings-managers.js addShiftType()
 * / js/core.js's DEFAULT_SHIFT_TYPES) — `isOff` marks a day-off type (e.g.
 * "Вихідний"), excluded from the shifts-worked count and defaulting to
 * amount=0/hours=0.
 */
data class ShiftType(
    val id: String = "",
    val name: String = "",
    val short: String = "",
    val color: String = "#8b5cf6",
    val amount: Double = 0.0,
    val hours: Double = 0.0,
    val isOff: Boolean = false,
)

/**
 * Mirrors the `shifts` doc (users/{uid}/max_tracker/shifts — see
 * js/color-picker.js's fbSaveNow()/fbLoadNow(), which read/write this
 * exact field set with `{merge:false}`). `data` maps a "yyyy-MM-dd" date
 * key to the list of shift-type ids assigned that day (mirrors
 * AppState.shifts) — a plain field named `data` to match the Firestore
 * document's actual field name exactly, since toObject() maps by name.
 * `autoFillSchedule` isn't modeled here — this app's MVP doesn't read/
 * write it, and ShiftsRepository's writes use dot-path field updates
 * scoped to `data.<dateKey>` specifically so they never touch it.
 */
data class ShiftsDoc(
    val data: Map<String, List<String>> = emptyMap(),
    val shiftTypes: List<ShiftType> = emptyList(),
    val updatedAt: Long = 0L,
)
