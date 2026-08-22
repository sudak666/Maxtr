package ua.rytm.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.screens.shopping.validateShoppingDraft

class ShoppingValidationTest {
    @Test fun emptyNameIsRejected() = assertTrue(validateShoppingDraft("  ", "1").nameInvalid)
    @Test fun blankQuantityDefaultsToOne() = assertTrue(validateShoppingDraft("Milk", "").valid)
    @Test fun zeroQuantityIsRejected() = assertTrue(validateShoppingDraft("Milk", "0").quantityInvalid)
    @Test fun nonIntegerQuantityIsRejected() = assertTrue(validateShoppingDraft("Milk", "1.5").quantityInvalid)
    @Test fun positiveIntegerIsAccepted() = assertFalse(validateShoppingDraft("Milk", "12").quantityInvalid)
}
