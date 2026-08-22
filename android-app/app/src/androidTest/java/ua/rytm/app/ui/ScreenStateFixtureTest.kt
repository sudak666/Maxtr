package ua.rytm.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.R
import ua.rytm.app.data.ProfileSyncCoordinator
import ua.rytm.app.ui.theme.RytmTheme

class ScreenStateFixtureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun deterministicLoadingAndErrorFixturesRender() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { RytmTheme { Column { ScreenLoadingState(); ScreenLoadErrorState() } } }
        compose.onNodeWithText(context.getString(R.string.common_loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.common_data_load_failed)).assertIsDisplayed()
    }

    @Test fun deterministicOfflineAndSyncErrorFixturesRender() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            RytmTheme { Column {
                CompositionLocalProvider(LocalRealtimeState provides ProfileSyncCoordinator.RealtimeState.Offline) { RealtimeStateBanner() }
                CompositionLocalProvider(LocalRealtimeState provides ProfileSyncCoordinator.RealtimeState.Error("fixture")) { RealtimeStateBanner() }
            } }
        }
        compose.onNodeWithText(context.getString(R.string.sync_status_offline)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sync_status_error)).assertIsDisplayed()
    }
}
