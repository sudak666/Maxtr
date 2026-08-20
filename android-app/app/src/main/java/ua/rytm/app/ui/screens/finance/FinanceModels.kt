package ua.rytm.app.ui.screens.finance

// Field shapes mirror the PWA's TransactionDraft (js/tx-validation.js) and
// Wallet typedef (js/state.js) — see ANDROID_MIGRATION.md §2.2/§2.3. This is
// the UI-layer model for this step only; the real Room entity comes with
// the Repository layer (a later step, see FINANCE_SCREEN_SPEC.md §7).

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
)

data class Tag(
    val id: String,
    val name: String,
    val colorHex: Long,
)

enum class TxTypeFilter { ALL, INCOME, EXPENSE, TRANSFER }
enum class PeriodFilter { DAY, MONTH, ALL }

// 1:1 with js/core.js's PALETTE/CURRENCY_LIST constants.
val PALETTE = listOf(0xFF8B5CF6, 0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899, 0xFF06B6D4, 0xFFEF4444, 0xFFA78BFA)
val CURRENCY_LIST = listOf("UAH", "USD", "EUR", "GBP", "PLN")
