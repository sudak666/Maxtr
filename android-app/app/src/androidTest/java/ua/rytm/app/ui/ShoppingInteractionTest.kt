package ua.rytm.app.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.ui.screens.shopping.ShoppingItem
import ua.rytm.app.ui.screens.shopping.ShoppingRow
import ua.rytm.app.ui.theme.RytmTheme

class ShoppingInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editableRowSwipeTriggersDelete() {
        var deletes = 0
        compose.setContent {
            RytmTheme { ShoppingRow(ShoppingItem("1", "Milk", 1, false), true, {}, { deletes++ }) }
        }
        compose.onNodeWithText("Milk").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, deletes) }
    }

    @Test fun viewerRowDisablesCheckboxAndSwipeDelete() {
        var deletes = 0
        compose.setContent {
            RytmTheme { ShoppingRow(ShoppingItem("1", "Milk", 1, false), false, {}, { deletes++ }) }
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Milk").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, deletes) }
    }
}
