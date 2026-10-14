package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.Recurring
import ua.rytm.app.ui.screens.finance.SubscriptionDetector
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TxType
import java.time.LocalDate

class SubscriptionDetectorTest {
    private val today = LocalDate.parse("2026-10-14")
    private fun tx(date: String, amount: Double, comment: String = "Netflix", category: String = "Розваги") =
        Transaction(id = "$comment$date", type = TxType.EXPENSE, amount = amount, date = date, walletId = "w", category = category, comment = comment)

    @Test fun monthlySubscriptionFound() {
        val txs = listOf(tx("2026-07-05", 299.0), tx("2026-08-05", 299.0), tx("2026-09-05", 299.0), tx("2026-10-05", 309.0))
        val c = SubscriptionDetector.detect(txs, emptyList(), today).single()
        assertEquals("monthly", c.frequency)
        assertEquals(309.0, c.amount, 0.001)
        assertEquals("2026-11-05", c.nextDate)
        assertEquals(4, c.occurrences)
    }

    @Test fun weeklyFound() {
        val txs = listOf(tx("2026-09-23", 150.0, "Басейн"), tx("2026-09-30", 150.0, "басейн "), tx("2026-10-07", 150.0, "Басейн"))
        assertEquals("weekly", SubscriptionDetector.detect(txs, emptyList(), today).single().frequency)
    }

    @Test fun irregularGapsIgnored() {
        val txs = listOf(tx("2026-08-01", 100.0, "Кава"), tx("2026-08-20", 100.0, "Кава"), tx("2026-10-01", 100.0, "Кава"))
        assertTrue(SubscriptionDetector.detect(txs, emptyList(), today).isEmpty())
    }

    @Test fun amountsTooDifferentIgnored() {
        val txs = listOf(tx("2026-08-05", 300.0, "АТБ"), tx("2026-09-05", 820.0, "АТБ"), tx("2026-10-05", 150.0, "АТБ"))
        assertTrue(SubscriptionDetector.detect(txs, emptyList(), today).isEmpty())
    }

    @Test fun twoOccurrencesNotEnough() {
        assertTrue(SubscriptionDetector.detect(listOf(tx("2026-09-05", 299.0), tx("2026-10-05", 299.0)), emptyList(), today).isEmpty())
    }

    @Test fun stoppedSubscriptionIgnored() {
        val txs = listOf(tx("2026-04-05", 299.0), tx("2026-05-05", 299.0), tx("2026-06-05", 299.0))
        assertTrue(SubscriptionDetector.detect(txs, emptyList(), today).isEmpty())
    }

    @Test fun alreadyRecurringOrDismissedIgnored() {
        val txs = listOf(tx("2026-08-05", 299.0), tx("2026-09-05", 299.0), tx("2026-10-05", 299.0))
        val byName = Recurring("r", TxType.EXPENSE, 299.0, "Розваги", "w", "monthly", "2026-11-05", true, "netflix")
        assertTrue(SubscriptionDetector.detect(txs, listOf(byName), today).isEmpty())
        val byAmount = byName.copy(comment = "Кіно")
        assertTrue(SubscriptionDetector.detect(txs, listOf(byAmount), today).isEmpty())
        assertTrue(SubscriptionDetector.detect(txs, emptyList(), today, dismissed = setOf("netflix")).isEmpty())
    }
}
