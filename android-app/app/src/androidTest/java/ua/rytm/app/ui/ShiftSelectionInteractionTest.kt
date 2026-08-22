package ua.rytm.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.ui.screens.shifts.ShiftSelectionRow
import ua.rytm.app.ui.screens.shifts.ShiftType
import ua.rytm.app.ui.theme.RytmTheme

class ShiftSelectionInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wholeRowIsAccessibleCheckboxTarget() {
        var toggles = 0
        val type = ShiftType("day", "Day shift", "D", "D", 0xFF7C3AED, 1000.0, 8.0, false)
        compose.setContent { RytmTheme { ShiftSelectionRow(type, checked = false) { toggles++ } } }

        compose.onNode(isToggleable()).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
    }
}
