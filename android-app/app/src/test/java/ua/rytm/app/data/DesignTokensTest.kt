package ua.rytm.app.data

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii
import ua.rytm.app.ui.theme.RytmInteraction

class DesignTokensTest {
    @Test
    fun sharedGeometryMatchesPwaCss() {
        assertEquals(22.dp, RytmRadii.Card)
        assertEquals(16.dp, RytmRadii.Row)
        assertEquals(32.dp, RytmRadii.Sheet)
        assertEquals(76.dp, RytmDimens.QuickActionMinHeight)
        assertEquals(60.dp, RytmDimens.SwipeReveal)
        assertEquals(30.dp, RytmDimens.SwipeThreshold)
        assertEquals(48.dp, RytmDimens.TouchTarget)
        assertEquals(0.88f, RytmInteraction.TabPressedScale)
        assertEquals(0.97f, RytmInteraction.ButtonPressedScale)
        assertEquals(0.4f, RytmInteraction.DisabledAlpha)
        assertEquals(2.dp, RytmInteraction.FocusOutline)
        assertEquals(3.dp, RytmInteraction.FocusOffset)
        assertEquals(4.dp, RytmInteraction.FocusGlow)
    }
}
