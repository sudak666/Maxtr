package ua.rytm.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.screens.finance.CATEGORY_NAME_MAX
import ua.rytm.app.ui.screens.finance.CategoryNameValidation
import ua.rytm.app.ui.screens.finance.validateCategoryName

class CategoryValidationTest {
    @Test fun whitespaceOnlyIsEmpty() = assertEquals(CategoryNameValidation.EMPTY, validateCategoryName("   "))
    @Test fun exactly120CharactersIsValid() = assertEquals(CategoryNameValidation.VALID, validateCategoryName("а".repeat(CATEGORY_NAME_MAX)))
    @Test fun over120CharactersIsRejected() = assertEquals(CategoryNameValidation.TOO_LONG, validateCategoryName("а".repeat(CATEGORY_NAME_MAX + 1)))
}
