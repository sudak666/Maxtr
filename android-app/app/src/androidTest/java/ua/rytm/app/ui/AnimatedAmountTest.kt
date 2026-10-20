package ua.rytm.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AnimatedAmountTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countsToTheExactValue() {
        var target by mutableDoubleStateOf(100.0)
        compose.mainClock.autoAdvance = false
        compose.setContent { Text("v=" + rememberAnimatedAmount(target)) }
        compose.onNodeWithText("v=100.0").assertExists()
        target = 12_345_678.91
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("v=12345678.91").assertDoesNotExist() // mid-animation
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("v=1.234567891E7").assertExists() // exact Double, not Float-rounded
    }

    @Test fun reducedMotionJumps() {
        var target by mutableDoubleStateOf(1.0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) { Text("v=" + rememberAnimatedAmount(target)) }
        }
        target = 2.5
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("v=2.5").assertExists()
    }
}
