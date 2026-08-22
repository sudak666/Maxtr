package ua.rytm.app.data

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test
import ua.rytm.app.ui.theme.RytmDimens
import ua.rytm.app.ui.theme.RytmRadii

class DesignTokensTest {
    @Test
    fun sharedGeometryMatchesPwaCss() {
        assertEquals(22.dp, RytmRadii.Card)
        assertEquals(16.dp, RytmRadii.Row)
        assertEquals(32.dp, RytmRadii.Sheet)
        assertEquals(84.dp, RytmDimens.QuickActionMinHeight)
        assertEquals(60.dp, RytmDimens.SwipeReveal)
        assertEquals(30.dp, RytmDimens.SwipeThreshold)
        assertEquals(48.dp, RytmDimens.TouchTarget)
    }
}
