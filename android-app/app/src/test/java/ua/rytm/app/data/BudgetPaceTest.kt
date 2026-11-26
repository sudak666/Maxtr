package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.BudgetPace
import ua.rytm.app.ui.screens.finance.budgetPace

class BudgetPaceTest {
    @Test
    fun `spending in step with the month stays calm`() {
        // Продукти: 3 898,94 of 5 000 on day 26 of 30.
        assertEquals(BudgetPace.ON_TRACK, budgetPace(3898.94, 5000.0, 26.0 / 30))
        assertEquals(BudgetPace.ON_TRACK, budgetPace(100.0, 1000.0, 0.1))
    }

    @Test
    fun `running ahead of the month warns`() {
        assertEquals(BudgetPace.AHEAD, budgetPace(600.0, 1000.0, 5.0 / 30))
        assertEquals(BudgetPace.AHEAD, budgetPace(920.0, 1000.0, 0.97))
    }

    @Test
    fun `over the limit is over`() {
        assertEquals(BudgetPace.OVER, budgetPace(1000.01, 1000.0, 0.5))
        assertEquals(BudgetPace.AHEAD, budgetPace(1000.0, 1000.0, 1.0))
    }
}
