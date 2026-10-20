package ua.rytm.app.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.data.local.AutoRuleEntity
import ua.rytm.app.ui.screens.finance.FinanceMessage
import ua.rytm.app.ui.screens.finance.SplitPart
import ua.rytm.app.ui.screens.finance.TransactionFormState
import ua.rytm.app.ui.screens.finance.TxType

class TransactionFormStateTest {
    private val messages = mutableListOf<FinanceMessage>()
    private fun state(saved: SavedStateHandle = SavedStateHandle(), rules: List<AutoRuleEntity> = emptyList()) = TransactionFormState(
        saved,
        categoriesOf = { if (it == TxType.INCOME) listOf("Зарплата") else listOf("Продукти", "Кафе", "Транспорт") },
        frequentOf = { if (it == TxType.EXPENSE) listOf("Кафе") else emptyList() },
        autoRules = { rules },
        onMessage = { messages += it },
    )

    @Test fun openNewPicksTheMostUsedCategory() {
        val f = state()
        f.openNew(TxType.EXPENSE, "w", "w2", "")
        assertEquals("Кафе", f.category)
        f.onTypeChange(TxType.INCOME)
        assertEquals("Зарплата", f.category)
    }

    @Test fun draftSurvivesProcessDeathIncludingSplits() {
        val saved = SavedStateHandle()
        val f = state(saved)
        f.openNew(TxType.EXPENSE, "w", "w2", "")
        f.onAmountChange("1 2a3,5")
        f.onCommentChange("кава")
        f.toggleTag("t1")
        f.addSplitPart()
        f.updateSplitPart(0, SplitPart("Транспорт", "20x"))
        val restored = state(saved)
        assertTrue(restored.sheetVisible)
        assertEquals("1 23,5", restored.amountText)
        assertEquals("кава", restored.comment)
        assertEquals(listOf("t1"), restored.selectedTagIds)
        assertEquals(listOf(SplitPart("Транспорт", "20")), restored.splits)
    }

    @Test fun autoRuleSetsCategoryAndReports() {
        val f = state(rules = listOf(AutoRuleEntity("r", "EXPENSE", "uber", "Транспорт", 0)))
        f.openNew(TxType.EXPENSE, "w", "w2", "")
        f.onCommentChange("Uber trip")
        assertEquals("Транспорт", f.category)
        assertEquals(R.string.transaction_auto_category, messages.single().resource)
    }

    @Test fun splitRemainderAndTypeChangeDropsParts() {
        val f = state()
        f.openNew(TxType.EXPENSE, "w", "w2", "100")
        assertNull(f.splitRemainder)
        f.addSplitPart()
        f.updateSplitPart(0, SplitPart("Продукти", "30"))
        assertEquals(70.0, f.splitRemainder!!, 0.001)
        f.onTypeChange(TxType.TRANSFER)
        assertTrue(f.splits.isEmpty())
        assertTrue(!f.canSplit)
    }
}
