package ua.rytm.app.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.nextDueDate

/** Same cases as nextDebtDueDate() in tests/functions-sweep.mjs. */
class DebtDueDateTest {
    private fun debt(due: String, monthly: Boolean = false) =
        Debt(1, "d", "", "USD", 100.0, due, emptyList(), "owe", monthly)

    private fun at(s: String) = LocalDate.parse(s)

    @Test fun oneOffDateStays() {
        assertEquals("2026-09-15", debt("2026-09-15").nextDueDate(at("2026-11-06")))
    }

    @Test fun monthlyRepeatsOnItsDay() {
        assertEquals("2026-11-15", debt("2026-09-15", true).nextDueDate(at("2026-11-06")))
        assertEquals("2026-11-15", debt("2026-09-15", true).nextDueDate(at("2026-11-15")))
        assertEquals("2026-12-15", debt("2026-09-15", true).nextDueDate(at("2026-11-16")))
        assertEquals("2026-12-20", debt("2026-12-20", true).nextDueDate(at("2026-11-06")))
        assertEquals("2027-01-20", debt("2026-11-20", true).nextDueDate(at("2026-12-25")))
    }

    @Test fun monthlyClampsToMonthEnd() {
        assertEquals("2027-02-28", debt("2026-01-31", true).nextDueDate(at("2027-02-10")))
        assertEquals("2026-12-31", debt("2026-10-31", true).nextDueDate(at("2026-12-31")))
    }

    @Test fun legacyCurrencySymbolsBecomeCodes() {
        assertEquals("USD", debtCurrencyCode("$"))
        assertEquals("EUR", debtCurrencyCode("€"))
        assertEquals("GBP", debtCurrencyCode("£"))
        assertEquals("UAH", debtCurrencyCode("грн"))
        assertEquals("UAH", debtCurrencyCode(null))
        assertEquals("PLN", debtCurrencyCode("PLN"))
    }
}
