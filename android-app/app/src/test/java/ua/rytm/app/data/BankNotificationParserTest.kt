package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.bank.BankNotificationParser

class BankNotificationParserTest {
    private val mono = "com.ftband.mono"
    private val pumb = "com.fuib.android.spot.online"

    @Test fun monoStyleExpenseWithBalance() {
        val s = BankNotificationParser.parse(mono, "−245.50₴", "Сільпо\nБаланс 12 345.67₴")!!
        assertFalse(s.isIncome)
        assertEquals(245.50, s.amount, 0.001)
        assertEquals("UAH", s.currency)
        assertEquals("Сільпо", s.merchant)
    }

    @Test fun keywordExpenseWithCardMaskAndSpacedThousands() {
        val s = BankNotificationParser.parse(pumb, "Списання", "Картка *1234. Оплата АТБ 1 250,00 грн. Доступно: 5 498,25 грн")!!
        assertFalse(s.isIncome)
        assertEquals(1250.0, s.amount, 0.001)
        assertTrue(s.merchant!!.contains("АТБ"))
    }

    @Test fun incomeByKeyword() {
        val s = BankNotificationParser.parse(pumb, "Зарахування", "+5 000.00 UAH від ТОВ Ромашка")!!
        assertTrue(s.isIncome)
        assertEquals(5000.0, s.amount, 0.001)
    }

    @Test fun foreignCurrencyBeforeNumber() {
        val s = BankNotificationParser.parse(mono, "Покупка", "Netflix $15.49")!!
        assertEquals("USD", s.currency)
        assertEquals(15.49, s.amount, 0.001)
        assertEquals("Netflix", s.merchant)
    }

    @Test fun nonBankAppsAndAmbiguousTextAreIgnored() {
        assertNull(BankNotificationParser.parse("org.telegram.messenger", "−245₴", "Сільпо"))
        assertNull("no direction → no guess", BankNotificationParser.parse(mono, "Інформація", "Ваш ліміт 10 000 грн"))
        assertNull("only a balance", BankNotificationParser.parse(mono, "Баланс", "Баланс 100 грн"))
    }

    // Real ПУМБ pushes captured on the owner's phone (2026-10-06): title only
    // a capitalised keyword, amount glued to "UAH", balance can be negative.
    @Test fun realPumbExpense() {
        val s = BankNotificationParser.parse(pumb, "Списання", "500.00UAH\n06-10-2026 22:11\nРахунок: *5536\nДоступно: 2093.53UAH")!!
        assertFalse(s.isIncome)
        assertEquals(500.0, s.amount, 0.001)
        assertNull(s.merchant)
    }

    @Test fun realPumbIncomeWithNegativeAvailable() {
        val s = BankNotificationParser.parse(pumb, "Надходження", "500.00UAH\n06-10-2026 22:11\nРахунок: *3924\nДоступно: -5135.82UAH")!!
        assertTrue(s.isIncome)
        assertEquals(500.0, s.amount, 0.001)
    }
}
