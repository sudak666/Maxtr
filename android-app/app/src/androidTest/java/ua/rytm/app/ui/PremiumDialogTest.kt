package ua.rytm.app.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.data.OffersState
import ua.rytm.app.data.PremiumPlanUi
import ua.rytm.app.ui.screens.PremiumDialogContent
import ua.rytm.app.ui.theme.RytmTheme

/**
 * The paywall as a not-yet-paying user sees it. Debug and QA builds are always
 * Premium and cannot load Play plans, so this is the only place the purchase
 * state can be looked at before a release (first-run audit 2026-11-07).
 */
class PremiumDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unpaidStateShowsPlansSavingAndTerms() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val plans = listOf(
            PremiumPlanUi("monthly", "99,00 ₴", hasTrial = true, recurringMicros = 99_000_000),
            PremiumPlanUi("yearly", "699,00 ₴", hasTrial = true, recurringMicros = 699_000_000),
        )
        compose.setContent {
            RytmTheme(darkTheme = true) {
                PremiumDialogContent(owned = false, plans = plans, offersState = OffersState.READY, purchaseError = null, onBuy = {}, onRetry = {}, onDismiss = {})
            }
        }
        val monthly = context.getString(R.string.premium_trial, context.getString(R.string.premium_monthly, "99,00 ₴"))
        val yearly = context.getString(R.string.premium_trial, context.getString(R.string.premium_saving, context.getString(R.string.premium_yearly, "699,00 ₴"), 41))
        compose.onNodeWithText(monthly).assertExists()
        compose.onNodeWithText(yearly).assertExists()
        compose.onNodeWithText(context.getString(R.string.premium_terms_note)).assertExists()
        compose.onNodeWithText(context.getString(R.string.settings_terms_row)).assertExists()

        // Saved for a human look: adb pull /sdcard/Android/data/<pkg>/cache/premium-unpaid.png
        val bitmap = compose.onNode(isDialog()).captureToImage().asAndroidBitmap()
        context.externalCacheDir?.let { dir ->
            java.io.File(dir, "premium-unpaid.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
