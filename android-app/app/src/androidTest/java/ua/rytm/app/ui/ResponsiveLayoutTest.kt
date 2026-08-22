package ua.rytm.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.ui.screens.onboarding.OnboardingScreen
import ua.rytm.app.ui.theme.RytmTheme

class ResponsiveLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactPortraitAtLargeFontKeepsPrimaryContentVisible() = verify(320, 568, 1.3f)

    @Test fun landscapeKeepsPrimaryContentVisible() = verify(640, 360, 1f)

    private fun verify(width: Int, height: Int, fontScale: Float) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3f, fontScale)) {
                Box(Modifier.size(width.dp, height.dp)) { RytmTheme { OnboardingScreen {} } }
            }
        }
        compose.onNodeWithText(context.getString(R.string.onboarding_finance_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.action_next)).assertIsDisplayed()
    }
}
