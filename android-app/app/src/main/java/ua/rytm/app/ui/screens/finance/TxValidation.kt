package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.AMOUNT_MAX

// 1:1 port of js/tx-validation.js's validateTransactionDraft() — same rules,
// same order as the PWA. Locale-independent error codes are mapped to resources
// FINANCE_SCREEN_SPEC.md §9). Not a reinterpretation.

const val TX_AMOUNT_MAX = AMOUNT_MAX
const val TX_COMMENT_MAX = 500

data class TransactionDraft(
    val amount: Double,
    val date: String,
    val walletId: String,
    val targetWalletId: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    val comment: String,
)

private val dateRegex = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

/** Returns a locale-independent error code, or null if the draft is valid. */
enum class TxValidationError { INVALID_AMOUNT, AMOUNT_TOO_LARGE, DATE_REQUIRED, INVALID_DATE, WALLET_REQUIRED, COMMENT_TOO_LONG, CATEGORY_TOO_LONG, SAME_WALLETS }

fun validateTransactionDraft(draft: TransactionDraft, isTransfer: Boolean): TxValidationError? {
    if (!draft.amount.isFinite() || draft.amount <= 0) return TxValidationError.INVALID_AMOUNT
    if (draft.amount >= TX_AMOUNT_MAX) return TxValidationError.AMOUNT_TOO_LARGE
    if (draft.date.isBlank()) return TxValidationError.DATE_REQUIRED
    if (!dateRegex.matches(draft.date)) return TxValidationError.INVALID_DATE
    if (draft.walletId.isBlank()) return TxValidationError.WALLET_REQUIRED
    if (draft.comment.length > TX_COMMENT_MAX) return TxValidationError.COMMENT_TOO_LONG
    if ((draft.category?.length ?: 0) > 120 || (draft.subcategory?.length ?: 0) > 120) return TxValidationError.CATEGORY_TOO_LONG
    if (isTransfer) {
        if (draft.targetWalletId.isNullOrBlank()) return TxValidationError.WALLET_REQUIRED
        if (draft.walletId == draft.targetWalletId) return TxValidationError.SAME_WALLETS
    }
    return null
}
