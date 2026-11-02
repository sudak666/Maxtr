package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.data.local.TransactionEntity

class MonobankMergeTest {
    private var seq = 0
    private fun tx(type: String, amount: Double, wallet: String, date: String = "2026-11-02", mono: String? = null, category: String = "Інше") =
        TransactionEntity(
            id = "t${seq++}", type = type, amount = amount, currency = "UAH", date = date, walletId = wallet,
            targetWalletId = null, targetAmount = null, targetCurrency = null, category = category, subcategory = null,
            comment = null, tags = "", createdAt = seq.toLong(), monobankId = mono,
        )

    private fun row(type: String, amount: Double, wallet: String, time: Long, id: String) =
        MonobankMerge.Row(tx(type, amount, wallet, mono = id), time)

    @Test fun cardToJarBecomesOneTransfer() {
        val r = MonobankMerge.merge(listOf(row("EXPENSE", 500.0, "card", 1000, "a"), row("INCOME", 500.0, "jar", 1030, "b")), emptyList())
        assertEquals(1, r.added.size)
        val t = r.added.single()
        assertEquals("TRANSFER", t.type)
        assertEquals("card", t.walletId)
        assertEquals("jar", t.targetWalletId)
        assertEquals(BuiltinCategories.TRANSFER, t.category)
        assertEquals(setOf("a", "b"), MonobankMerge.knownIds(listOf(t.monobankId!!)))
    }

    @Test fun farApartOrDifferentAmountsStaySeparate() {
        val late = MonobankMerge.merge(listOf(row("EXPENSE", 500.0, "card", 1000, "a"), row("INCOME", 500.0, "jar", 1000 + 121, "b")), emptyList())
        assertEquals(listOf("EXPENSE", "INCOME"), late.added.map { it.type })
        val other = MonobankMerge.merge(listOf(row("EXPENSE", 500.0, "card", 1000, "a"), row("INCOME", 499.0, "jar", 1010, "b")), emptyList())
        assertEquals(listOf("EXPENSE", "INCOME"), other.added.map { it.type })
        val sameCard = MonobankMerge.merge(listOf(row("EXPENSE", 500.0, "card", 1000, "a"), row("INCOME", 500.0, "card", 1010, "b")), emptyList())
        assertEquals(listOf("EXPENSE", "INCOME"), sameCard.added.map { it.type })
    }

    @Test fun pushSavedPurchaseIsClaimedNotDuplicated() {
        val saved = tx("EXPENSE", 89.5, "card", category = "Продукти")
        val r = MonobankMerge.merge(listOf(row("EXPENSE", 89.5, "card", 1000, "m1")), listOf(saved))
        assertTrue(r.added.isEmpty())
        val c = r.claimed.single()
        assertEquals(saved.id, c.id)
        assertEquals("Продукти", c.category)
        assertEquals("m1", c.monobankId)
    }

    @Test fun twoEqualPurchasesClaimOnlyOne() {
        val saved = tx("EXPENSE", 40.0, "card")
        val r = MonobankMerge.merge(listOf(row("EXPENSE", 40.0, "card", 1000, "m1"), row("EXPENSE", 40.0, "card", 5000, "m2")), listOf(saved))
        assertEquals(1, r.claimed.size)
        assertEquals(1, r.added.size)
    }

    @Test fun otherWalletOrDayOrAlreadyLinkedIsNotClaimed() {
        val cash = tx("EXPENSE", 40.0, "cash")
        val yesterday = tx("EXPENSE", 40.0, "card", date = "2026-11-01")
        val linked = tx("EXPENSE", 40.0, "card", mono = "old")
        val r = MonobankMerge.merge(listOf(row("EXPENSE", 40.0, "card", 1000, "m1")), listOf(cash, yesterday, linked))
        assertTrue(r.claimed.isEmpty())
        assertEquals(1, r.added.size)
    }
}
