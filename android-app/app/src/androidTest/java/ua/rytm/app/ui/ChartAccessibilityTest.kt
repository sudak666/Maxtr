package ua.rytm.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.ui.screens.debt.Debt
import ua.rytm.app.ui.screens.debt.DebtEntry
import ua.rytm.app.ui.screens.debt.DebtForecastCard
import ua.rytm.app.ui.theme.RytmTheme

class ChartAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test fun debtCanvasHasMeaningfulTalkBackSummary() {
        val debt = Debt(
            id = 1,
            name = "Test",
            note = "",
            currency = "UAH",
            startAmount = 1_000.0,
            dueDate = "",
            entries = listOf(
                DebtEntry(1, "200", 800.0, "2026-07-01"),
                DebtEntry(2, "200", 600.0, "2026-08-01"),
            ),
        )
        compose.setContent { RytmTheme { DebtForecastCard(debt) } }
        val meaningfulChartDescription = SemanticsMatcher("meaningful chart description") { node ->
            node.config.contains(SemanticsProperties.ContentDescription) &&
                node.config[SemanticsProperties.ContentDescription].any { description -> description.length > 20 && "UAH" in description }
        }
        compose.onNode(meaningfulChartDescription).assertIsDisplayed()
    }
}
