package ua.rytm.app.ui.screens.finance

// Field shapes mirror the PWA's TransactionDraft (js/tx-validation.js) and
// Wallet typedef (js/state.js) — see ANDROID_MIGRATION.md §2.2/§2.3. This is
// the UI-layer model for this step only; the real Room entity comes with
// the Repository layer (a later step, see FINANCE_SCREEN_SPEC.md §7).
//
// @Immutable on every model below. Compose infers stability automatically for
// data classes of stable types, but `List<T>` is an interface whose backing
// implementation could be mutable, so a model carrying one (Transaction.tags)
// infers as UNSTABLE — and one unstable parameter disables recomposition
// skipping for the whole row that takes it. These are only ever built from
// immutable snapshots read out of Room, so the annotation states a fact
// rather than papering over one.
import androidx.compose.runtime.Immutable

@Immutable
enum class TxType { INCOME, EXPENSE, TRANSFER }

@Immutable
data class Wallet(
    val id: String,
    val name: String,
    val colorHex: Long,
    val currency: String = "UAH",
    // Kept for 1:1 field parity with the PWA's wallet shape ({id,name,color,
    // icon,currency}) — Android doesn't render per-wallet icons yet, but a
    // Firestore sync round-trip must not silently drop a field the PWA (or
    // a future Android version) actually uses. Default matches addWallet()'s
    // PWA default (js/settings-managers.js).
    val icon: String = "card",
    /** cash, card, deposit or credit. */
    val kind: String = "card",
    /** What the wallet held before its first entry. */
    val openingBalance: Double = 0.0,
    /** Off: left out of the total balance. */
    val inCapital: Boolean = true,
    /** Off: its entries are left out of totals, statistics and budgets, for all periods. */
    val inStats: Boolean = true,
)

val WALLET_KINDS = listOf("cash", "card", "deposit", "credit")

/** Entries that count in statistics. */
fun List<Transaction>.forStats(wallets: List<Wallet>): List<Transaction> {
    val off = wallets.filterNot { it.inStats }.map { it.id }.toSet()
    return if (off.isEmpty()) this else filterNot { it.walletId in off }
}

@Immutable
data class Transaction(
    val id: String,
    val type: TxType,
    val amount: Double,
    val currency: String = "UAH",
    val date: String, // "yyyy-MM-dd", matches PWA's TransactionDraft.date
    val walletId: String,
    val targetWalletId: String? = null,
    val targetAmount: Double? = null,
    val targetCurrency: String? = null,
    val category: String,
    val subcategory: String? = null,
    val comment: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val monobankId: String? = null,
    val createdBy: String? = null,
    val createdByName: String? = null,
)

@Immutable
data class Tag(
    val id: String,
    val name: String,
    val colorHex: Long,
)

// Mirrors AppState.recurring (js/state.js) — a scheduled tx template
// materialized into a real Transaction (js/color-picker.js's
// processRecurring()) each time nextDate falls due. `type` is never
// TRANSFER — the PWA's own recurring-modal type <select> only offers
// income/expense.
@Immutable
data class Recurring(
    val id: String,
    val type: TxType,
    val amount: Double,
    val category: String,
    val walletId: String,
    val frequency: String, // "daily" | "weekly" | "monthly"
    val nextDate: String, // "yyyy-MM-dd"
    val active: Boolean,
    val comment: String,
    val anchorDay: Int? = null,
)

// Mirrors AppState.goals (js/state.js) — progress is computed live against
// the linked wallet's current balance (js/goals-profile.js's
// renderGoals()/renderGoalsManagerList()), not stored as a separate field.
@Immutable
data class Goal(
    val id: String,
    val walletId: String,
    val targetAmount: Double,
    val targetDate: String,
)

@Immutable
enum class TxTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }
@Immutable
enum class PeriodFilter { DAY, MONTH, ALL }

/**
 * The history's «Фільтри» sheet beyond type, period and category: one wallet
 * (either side of a transfer), an amount range in the entry's own currency,
 * and a custom date range (ISO, inclusive) that replaces the period chips.
 */
data class HistoryFilter(
    val walletId: String? = null,
    val minAmount: Double? = null,
    val maxAmount: Double? = null,
    val from: String? = null,
    val to: String? = null,
) {
    val hasRange: Boolean get() = from != null || to != null
    val activeCount: Int get() = listOf(walletId != null, minAmount != null || maxAmount != null, hasRange).count { it }

    fun matches(t: Transaction): Boolean =
        (walletId == null || t.walletId == walletId || t.targetWalletId == walletId) &&
            (minAmount == null || t.amount >= minAmount) &&
            (maxAmount == null || t.amount <= maxAmount) &&
            (from == null || t.date >= from) &&
            (to == null || t.date <= to)
}

// 1:1 with js/core.js's PALETTE/CURRENCY_LIST constants.
val PALETTE = listOf(0xFF8B5CF6, 0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4, 0xFFEF4444, 0xFFA78BFA)
val CURRENCY_LIST = listOf("UAH") + ua.rytm.app.data.SEED_RATES.keys
