package ua.rytm.app.ui.screens.finance

import ua.rytm.app.data.AMOUNT_MAX

// 1:1 port of js/tx-validation.js's validateTransactionDraft() — same rules,
// same order, same UK copy from js/classic-globals.js's I18N.uk (see
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

/** Returns a UK error message, or null if the draft is valid. */
fun validateTransactionDraft(draft: TransactionDraft, isTransfer: Boolean): String? {
    if (!draft.amount.isFinite() || draft.amount <= 0) return "Введіть коректну суму"
    if (draft.amount >= TX_AMOUNT_MAX) return "Сума завелика"
    if (draft.date.isBlank()) return "Оберіть дату"
    if (!dateRegex.matches(draft.date)) return "Некоректний формат дати"
    if (draft.walletId.isBlank()) return "Оберіть гаманець"
    if (draft.comment.length > TX_COMMENT_MAX) return "Коментар занадто довгий"
    if ((draft.category?.length ?: 0) > 120 || (draft.subcategory?.length ?: 0) > 120) return "Назва категорії або підкатегорії занадто довга"
    if (isTransfer) {
        if (draft.targetWalletId.isNullOrBlank()) return "Оберіть гаманець"
        if (draft.walletId == draft.targetWalletId) return "Однакові рахунки"
    }
    return null
}
