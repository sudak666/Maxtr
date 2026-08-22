package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.screens.debt.parsePlainDebtAmount
import ua.rytm.app.ui.screens.debt.normalizeDebtEntryDate
import ua.rytm.app.ui.screens.debt.autoDebtBalance

class DebtParsingTest {
    @Test fun positiveIntegerParses() = assertEquals(250.0, parsePlainDebtAmount("250")!!, 0.0)
    @Test fun decimalParses() = assertEquals(250.75, parsePlainDebtAmount(" 250.75 ")!!, 0.0)
    @Test fun commaDecimalIsNotPlainPwaNumber() = assertNull(parsePlainDebtAmount("250,75"))
    @Test fun negativeIsNotPlainPwaNumber() = assertNull(parsePlainDebtAmount("-20"))
    @Test fun scientificNotationIsNotPlainPwaNumber() = assertNull(parsePlainDebtAmount("2e3"))
    @Test fun textualAmountIsPreservedRatherThanAutoParsed() = assertNull(parsePlainDebtAmount("cash 250"))
    @Test fun isoPickerDateBecomesPwaTextDate() = assertEquals("22.08.2026", normalizeDebtEntryDate("2026-08-22"))
    @Test fun existingTextDateIsPreserved() = assertEquals("22 серпня", normalizeDebtEntryDate("22 серпня"))
    @Test fun plainPaymentAutoCalculatesBalance() = assertEquals("750", autoDebtBalance(1_000.0, "250"))
    @Test fun textualPaymentDoesNotGuessBalance() = assertEquals("", autoDebtBalance(1_000.0, "cash 250"))
}
