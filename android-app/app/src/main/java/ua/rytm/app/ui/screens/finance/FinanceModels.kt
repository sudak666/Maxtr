package ua.rytm.app.ui.screens.finance

// Domain shapes mirror the PWA transaction draft and wallet contracts.

enum class TxType { INCOME, EXPENSE, TRANSFER }

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
)

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
)

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
)

// Mirrors AppState.goals (js/state.js) — progress is computed live against
// the linked wallet's current balance (js/goals-profile.js's
// renderGoals()/renderGoalsManagerList()), not stored as a separate field.
data class Goal(
    val id: String,
    val walletId: String,
    val targetAmount: Double,
    val targetDate: String,
)

enum class TxTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }
enum class PeriodFilter { DAY, MONTH, ALL }

// 1:1 with js/core.js's PALETTE/CURRENCY_LIST constants.
val PALETTE = listOf(0xFF8B5CF6, 0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4, 0xFFEF4444, 0xFFA78BFA)
val CURRENCY_LIST = listOf("UAH", "USD", "EUR", "GBP", "PLN")
