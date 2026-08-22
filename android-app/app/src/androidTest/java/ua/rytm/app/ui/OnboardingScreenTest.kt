package ua.rytm.app.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ua.rytm.app.R
import ua.rytm.app.ui.screens.onboarding.OnboardingScreen
import ua.rytm.app.ui.theme.RytmTheme

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pages_areNavigable_andPrimaryActionHasAccessibleSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var completed = false
        compose.setContent { RytmTheme { OnboardingScreen { completed = true } } }

        compose.onNodeWithText(context.getString(R.string.onboarding_finance_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_next)).assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_shifts_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_next)).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_security_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_done)).performClick()

        compose.runOnIdle { assertEquals(true, completed) }
    }
}
