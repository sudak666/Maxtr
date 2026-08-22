package ua.rytm.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReducedMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chartProgressSnapsWhenMotionIsReduced() {
        var observed = 0f
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                observed = motionProgress("chart", 500)
            }
        }
        compose.runOnIdle { assertEquals(1f, observed, 0f) }
    }

    @Test fun chartProgressAnimatesOtherwise() {
        compose.mainClock.autoAdvance = false
        var observed = 1f
        compose.setContent { observed = motionProgress("chart", 500) }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertTrue(observed < 1f) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(1f, observed, 0.001f) }
    }
}
