package ua.rytm.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.ui.screens.finance.CashForecast
import ua.rytm.app.ui.screens.finance.CashForecastScreen
import ua.rytm.app.ui.theme.RytmTheme
import java.time.LocalDate

/**
 * The forecast as a full screen (2026-11-27): the title sits in the top bar,
 * «Назад» leaves, and the method stays folded until asked for.
 */
class CashForecastScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titleBackAndFoldedMethod() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val forecast = CashForecast(
            end = LocalDate.parse("2026-11-30"),
            startBalance = 5000.0,
            habit = null,
            plannedIn = 0.0,
            plannedOut = 0.0,
            unplannedSpend = 0.0,
            unplannedDays = 3,
            events = emptyList(),
            endBalance = 5000.0,
            shortfallDate = null,
            shortfall = 0.0,
            lowestDate = null,
        )
        var backs = 0
        compose.setContent {
            RytmTheme(darkTheme = true) {
                CashForecastScreen(forecast, shiftsLeft = 0.0, onBack = { backs++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.cash_forecast_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_enough)).assertIsDisplayed()

        val note = context.getString(R.string.cash_forecast_note)
        compose.onNodeWithText(note).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_how)).performScrollTo().performClick()
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        assertEquals(1, backs)
    }
}
