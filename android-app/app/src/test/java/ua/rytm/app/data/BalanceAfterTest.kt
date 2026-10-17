package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType

class BalanceAfterTest {
    private fun tx(id: String, type: TxType, amount: Double, date: String, createdAt: Long, wallet: String = "a", target: String? = null) =
        Transaction(id = id, type = type, amount = amount, date = date, walletId = wallet, targetWalletId = target, category = "", createdAt = createdAt)

    private val history = listOf(
        tx("1", TxType.INCOME, 1000.0, "2026-10-01", 1),
        tx("2", TxType.EXPENSE, 200.0, "2026-10-05", 5),
        tx("3", TxType.EXPENSE, 50.0, "2026-10-05", 6),
        tx("4", TxType.TRANSFER, 100.0, "2026-10-07", 7, target = "b"),
    )

    @Test fun countsOnlyEarlierOperations() {
        assertEquals(800.0, FinanceRepository.balanceAfter(history, "a", history[1]), 0.001)
        assertEquals(750.0, FinanceRepository.balanceAfter(history, "a", history[2]), 0.001)
    }

    @Test fun transferMovesBothWallets() {
        assertEquals(650.0, FinanceRepository.balanceAfter(history, "a", history[3]), 0.001)
        assertEquals(100.0, FinanceRepository.balanceAfter(history, "b", history[3]), 0.001)
    }

    @Test fun editedDraftReplacesStoredCopy() {
        val edited = history[1].copy(amount = 300.0)
        assertEquals(700.0, FinanceRepository.balanceAfter(history, "a", edited), 0.001)
    }

    @Test fun runningBalancesMatchBalanceAfter() {
        val running = FinanceRepository.runningBalances(history.shuffled(java.util.Random(1)))
        history.forEach { t -> assertEquals(FinanceRepository.balanceAfter(history, t.walletId, t), running.getValue(t.id), 0.001) }
    }

    @Test fun newEntrySortsLastOnItsDate() {
        val draft = tx("new", TxType.EXPENSE, 10.0, "2026-10-05", Long.MAX_VALUE)
        assertEquals(740.0, FinanceRepository.balanceAfter(history, "a", draft), 0.001)
    }
}
