package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.TransactionDraft
import ua.rytm.app.ui.screens.finance.TxValidationError
import ua.rytm.app.ui.screens.finance.validateTransactionDraft

class TransactionValidationTest {
    private fun draft(amount: Double = 10.0, date: String = "2026-08-22", wallet: String = "a", target: String? = "b", comment: String = "", category: String? = "Food") =
        TransactionDraft(amount, date, wallet, target, category, null, comment)

    @Test fun returnsStableLocaleIndependentErrorCodes() {
        assertEquals(TxValidationError.INVALID_AMOUNT, validateTransactionDraft(draft(amount = 0.0), false))
        assertEquals(TxValidationError.AMOUNT_TOO_LARGE, validateTransactionDraft(draft(amount = 1_000_000_000.0), false))
        assertEquals(TxValidationError.DATE_REQUIRED, validateTransactionDraft(draft(date = ""), false))
        assertEquals(TxValidationError.INVALID_DATE, validateTransactionDraft(draft(date = "22.08.2026"), false))
        assertEquals(TxValidationError.WALLET_REQUIRED, validateTransactionDraft(draft(wallet = ""), false))
        assertEquals(TxValidationError.COMMENT_TOO_LONG, validateTransactionDraft(draft(comment = "x".repeat(501)), false))
        assertEquals(TxValidationError.CATEGORY_TOO_LONG, validateTransactionDraft(draft(category = "x".repeat(121)), false))
        assertEquals(TxValidationError.SAME_WALLETS, validateTransactionDraft(draft(target = "a"), true))
        assertNull(validateTransactionDraft(draft(), true))
    }
}
