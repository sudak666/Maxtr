package ua.rytm.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeTokensTest {
    @Test fun shortDragSettles() {
        assertEquals(SwipeReleaseAction.Settle, swipeReleaseAction(-12f, 320f, 30f, 0f))
    }

    @Test fun deliberateShortSwipeRevealsAction() {
        assertEquals(SwipeReleaseAction.Reveal, swipeReleaseAction(-45f, 320f, 30f, 0f))
    }

    @Test fun fastFlickRevealsActionWithoutDeleting() {
        assertEquals(SwipeReleaseAction.Reveal, swipeReleaseAction(-18f, 320f, 30f, -1_200f))
    }

    @Test fun halfWidthSwipeDeletes() {
        assertEquals(SwipeReleaseAction.Delete, swipeReleaseAction(-160f, 320f, 30f, 0f))
    }
}
