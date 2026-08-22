package ua.rytm.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
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
        val retried = AtomicBoolean(false)
        compose.setContent {
            RytmTheme { Column {
                CompositionLocalProvider(LocalRealtimeState provides ProfileSyncCoordinator.RealtimeState.Offline) { RealtimeStateBanner() }
                CompositionLocalProvider(
                    LocalRealtimeState provides ProfileSyncCoordinator.RealtimeState.Error(ua.rytm.app.data.SyncFailure(ua.rytm.app.data.SyncFailure.Kind.NETWORK, true, "fixture")),
                    LocalSyncRetry provides { retried.set(true) },
                ) { RealtimeStateBanner() }
            } }
        }
        compose.onNodeWithText(context.getString(R.string.sync_status_offline)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.sync_error_network)).assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.action_retry))[1].performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(retried.get()) }
    }
}
