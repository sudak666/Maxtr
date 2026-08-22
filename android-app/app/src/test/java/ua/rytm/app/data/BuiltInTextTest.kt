package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.rytm.app.ui.builtInTextResource

class BuiltInTextTest {
    @Test
    fun ukrainianAndEnglishWireAliasesResolveToSamePresentationResource() {
        listOf(
            "Картка" to "Card", "Готівка" to "Cash", "Зарплата" to "Salary",
            "Продукти" to "Groceries", "Інше" to "Other", "Денна зміна" to "Day shift",
            "Вихідний" to "Day off", "Кредит" to "Loan", "Я" to "Me", "Банка" to "Jar",
        ).forEach { (uk, en) -> assertEquals(uk, builtInTextResource(uk), builtInTextResource(en)) }
        assertNull(builtInTextResource("User-defined value"))
    }
}
