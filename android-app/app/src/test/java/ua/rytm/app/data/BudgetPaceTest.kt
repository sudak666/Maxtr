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

class PaceVsLastMonthTest {
    @Test
    fun `pace note is a short signed percent`() {
        assertEquals("−28 %", ua.rytm.app.ui.screens.finance.paceVsLastMonth(50_800.60, 70_658.87))
        assertEquals("+10 %", ua.rytm.app.ui.screens.finance.paceVsLastMonth(110.0, 100.0))
        assertEquals(null, ua.rytm.app.ui.screens.finance.paceVsLastMonth(100.0, 0.0))
    }
}

class AnalyticsShareTest {
    @org.junit.Test
    fun `shares are rounded and a small one is not zero`() {
        org.junit.Assert.assertEquals("3", ua.rytm.app.ui.screens.finance.sharePercent(1517.0, 50800.6))
        org.junit.Assert.assertEquals("10", ua.rytm.app.ui.screens.finance.sharePercent(4806.92, 49806.92))
        org.junit.Assert.assertEquals("1", ua.rytm.app.ui.screens.finance.sharePercent(318.31, 50800.6))
        org.junit.Assert.assertEquals("<1", ua.rytm.app.ui.screens.finance.sharePercent(122.5, 50800.6))
        org.junit.Assert.assertEquals("0", ua.rytm.app.ui.screens.finance.sharePercent(0.0, 50800.6))
    }

    @org.junit.Test
    fun `growth is ranked by hryvnias, not percent`() {
        val g = ua.rytm.app.ui.screens.finance.topCategoryGrowth(
            current = mapOf("Підписки" to 310.0, "Житло" to 15440.0, "Їжа" to 900.0),
            previous = mapOf("Підписки" to 50.0, "Житло" to 12440.0, "Їжа" to 1000.0),
        )!!
        org.junit.Assert.assertEquals("Житло", g.category)
        org.junit.Assert.assertEquals(3000.0, g.increase, 0.001)
        org.junit.Assert.assertEquals(24, g.percent)
        org.junit.Assert.assertNull(ua.rytm.app.ui.screens.finance.topCategoryGrowth(mapOf("A" to 1002.0), mapOf("A" to 1000.0)))
    }
}
