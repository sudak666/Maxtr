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
        val endText = java.time.format.DateTimeFormatter.ofPattern("d MMMM", context.resources.configuration.locales[0]).format(forecast.end)
        compose.onNodeWithText(context.getString(R.string.cash_forecast_enough, endText)).assertIsDisplayed()

        val note = context.getString(R.string.cash_forecast_note)
        compose.onNodeWithText(note).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_how)).performScrollTo().performClick()
        compose.onNodeWithText(note).performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        assertEquals(1, backs)
    }

    // Outside review 2026-11-27: with a gap ahead the gap is the headline, and
    // the days list holds the day it starts, the deepest one and the way back.
    @Test
    fun gapLeadsAndKeyDaysAreListed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val forecast = CashForecast.compute(
            409.90,
            listOf(ua.rytm.app.ui.screens.finance.Recurring("r", ua.rytm.app.ui.screens.finance.TxType.INCOME, 15_000.0, "Зарплата", "w", "monthly", "2026-11-30", true, "")),
            { "UAH" },
            LocalDate.parse("2026-11-27"),
            LocalDate.parse("2026-11-30"),
            ua.rytm.app.ui.screens.finance.SpendingHabit(796.31, 60, 6),
        ) { a, _ -> a }
        compose.setContent {
            RytmTheme(darkTheme = true) {
                CashForecastScreen(forecast, shiftsLeft = 0.0, onBack = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.cash_forecast_short_label)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_day_gap), substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_day_lowest), substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.cash_forecast_day_back), substring = true).performScrollTo().assertIsDisplayed()
    }
}
