package ua.rytm.app.data

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.rytm.app.ui.theme.DarkBg
import ua.rytm.app.ui.theme.DarkBg1
import ua.rytm.app.ui.theme.DarkMuted
import ua.rytm.app.ui.theme.DarkMuted2
import ua.rytm.app.ui.theme.DarkText
import ua.rytm.app.ui.theme.LightBg
import ua.rytm.app.ui.theme.LightBg1
import ua.rytm.app.ui.theme.LightMuted
import ua.rytm.app.ui.theme.LightMuted2
import ua.rytm.app.ui.theme.LightText

class ThemeContrastTest {
    @Test fun bodyAndMutedTextMeetWcagAaInBothThemes() {
        listOf(
            DarkText to DarkBg,
            DarkText to DarkBg1,
            DarkMuted to DarkBg1,
            DarkMuted2 to DarkBg1,
            LightText to LightBg,
            LightText to LightBg1,
            LightMuted to LightBg1,
            LightMuted2 to LightBg1,
        ).forEach { (foreground, background) ->
            val ratio = contrast(foreground, background)
            assertTrue("Contrast $ratio for $foreground on $background", ratio >= 4.5)
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val light = maxOf(luminance(first), luminance(second))
        val dark = minOf(luminance(first), luminance(second))
        return (light + 0.05) / (dark + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun channel(value: Float): Double {
        val component = value.toDouble()
        return if (component <= 0.04045) component / 12.92 else Math.pow((component + 0.055) / 1.055, 2.4)
    }
}
