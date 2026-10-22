package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.finance.budgetExceededFeedback

class BudgetFeedbackTest {
    @Test fun noBudgetProducesNoWarning() = assertNull(budgetExceededFeedback("Food", null, listOf(90.0), 20.0))
    @Test fun equalToLimitDoesNotWarn() = assertNull(budgetExceededFeedback("Food", 100.0, listOf(80.0), 20.0))
    @Test fun crossingLimitReturnsExactTotals() {
        val feedback = budgetExceededFeedback("Food", 100.0, listOf(50.0, 40.0), 20.0)!!
        assertEquals("Food", feedback.category)
        assertEquals(110.0, feedback.spent, 0.0)
        assertEquals(100.0, feedback.limit, 0.0)
    }

    // AUDIT_2026-10 P1-05: float sums exactly at the limit are not over it.
    @Test fun exactlyAtLimitIsNotOver() {
        assertNull(budgetExceededFeedback("Їжа", 86.3, listOf(16.1), 70.2))
        assertEquals(86.31, budgetExceededFeedback("Їжа", 86.3, listOf(16.1), 70.21)!!.spent, 0.0001)
    }
}
