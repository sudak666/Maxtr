package ua.rytm.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.ui.screens.finance.Transaction
import ua.rytm.app.ui.screens.finance.TransactionRow
import ua.rytm.app.ui.screens.finance.TxType

class TransactionBulkSelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun longPressSelectsAndNextTapTogglesSelection() {
        var selected by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                TransactionRow(
                    tx = Transaction("tx", TxType.EXPENSE, 10.0, "UAH", "2026-08-22", "wallet", category = "Food"),
                    walletName = { "Card" },
                    tagLookup = { null },
                    iconOverride = null,
                    canEdit = true,
                    selected = selected,
                    selectionMode = selected,
                    onDelete = {},
                    onClick = {},
                    onToggleSelection = { selected = !selected },
                )
            }
        }
        compose.onNodeWithTag("transaction-row-tx").performTouchInput { longClick() }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.transaction_selected)).assertExists()
        compose.onNodeWithTag("transaction-row-tx").performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.transaction_selected)).assertDoesNotExist()
    }
}
