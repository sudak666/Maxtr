package ua.rytm.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.ui.screens.DebtEntrySwipeContainer
import ua.rytm.app.ui.theme.RytmTheme

class DebtSwipeInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editableSwipeTriggersDelete() {
        var deletes = 0
        compose.setContent { RytmTheme { DebtEntrySwipeContainer(true, { deletes++ }) { Text("Payment") } } }
        compose.onNodeWithText("Payment").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun viewerSwipeIsDisabled() {
        var deletes = 0
        compose.setContent { RytmTheme { DebtEntrySwipeContainer(false, { deletes++ }) { Text("Payment") } } }
        compose.onNodeWithText("Payment").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, deletes) }
    }
}
