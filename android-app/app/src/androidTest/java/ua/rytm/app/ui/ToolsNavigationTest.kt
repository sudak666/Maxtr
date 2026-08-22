package ua.rytm.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.data.FinanceRepository
import ua.rytm.app.data.local.RoomProfileScope
import ua.rytm.app.data.local.RytmDatabase
import ua.rytm.app.ui.screens.finance.ToolsSheet

class ToolsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: RytmDatabase

    @Before fun setUp() {
        RoomProfileScope.activate("tools-test", "default")
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            RytmDatabase::class.java,
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test fun fixedNavigationJumpsAcrossLongContent() {
        compose.setContent { MaterialTheme { ToolsSheet(FinanceRepository(db), onDismiss = {}) } }
        compose.onNodeWithTag("tools-nav-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tools-section-converter").assertIsDisplayed()
        compose.onNodeWithTag("tools-nav-0").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tools-section-analytics").assertIsDisplayed()
    }
}
