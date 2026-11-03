package ua.rytm.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.finance.AmountExpression

class AmountExpressionTest {
    @Test fun plainNumbersParseAsBefore() {
        assertEquals(1200.5, AmountExpression.evaluate("1 200,50")!!, 0.0)
        assertFalse(AmountExpression.isExpression("1 200,50"))
        assertNull(AmountExpression.evaluate("abc"))
    }

    @Test fun timesAndDivideBindTighter() {
        assertEquals(140.0, AmountExpression.evaluate("2 × 70")!!, 0.0)
        assertEquals(380.0, AmountExpression.evaluate("500 − 120")!!, 0.0)
        assertEquals(250.0, AmountExpression.evaluate("100 + 50 × 3")!!, 0.0)
        assertEquals(333.33, AmountExpression.evaluate("1000 ÷ 3")!!, 0.0)
        assertEquals(12.5, AmountExpression.evaluate("10,5+2")!!, 0.0)
    }

    @Test fun brokenExpressionsGiveNothing() {
        assertNull(AmountExpression.evaluate("100 + "))
        assertNull(AmountExpression.evaluate("100 ÷ 0"))
        assertTrue(AmountExpression.isExpression("100 + "))
    }

    @Test fun operatorButtonReplacesTrailingOperator() {
        assertEquals("70 × ", AmountExpression.append("70", AmountExpression.TIMES))
        assertEquals("70 + ", AmountExpression.append("70 × ", AmountExpression.PLUS))
        assertEquals("", AmountExpression.append("", AmountExpression.PLUS))
    }
}
