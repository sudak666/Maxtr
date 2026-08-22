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
}
