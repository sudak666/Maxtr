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
import ua.rytm.app.ui.theme.AmberText
import ua.rytm.app.ui.theme.DarkErrorContainer
import ua.rytm.app.ui.theme.DarkOnErrorContainer
import ua.rytm.app.ui.theme.DarkOnPrimaryContainer
import ua.rytm.app.ui.theme.DarkPrimaryContainer
import ua.rytm.app.ui.theme.DarkWarnContainer
import ua.rytm.app.ui.theme.LightErrorContainer
import ua.rytm.app.ui.theme.LightInverseSurface
import ua.rytm.app.ui.theme.LightOnErrorContainer
import ua.rytm.app.ui.theme.LightOnPrimaryContainer
import ua.rytm.app.ui.theme.LightPrimaryContainer
import ua.rytm.app.ui.theme.LightWarnContainer
import ua.rytm.app.ui.theme.OrangeDark2
import ua.rytm.app.ui.theme.PetrolDeep
import ua.rytm.app.ui.theme.Petrol
import ua.rytm.app.ui.theme.OnPetrol
import ua.rytm.app.ui.theme.GreenDark2
import ua.rytm.app.ui.theme.GreenText
import ua.rytm.app.ui.theme.RedDarkText
import ua.rytm.app.ui.theme.RedText
import ua.rytm.app.ui.theme.LightBg3
import ua.rytm.app.ui.theme.PetrolLinkDark
import ua.rytm.app.ui.theme.DarkBg3
import ua.rytm.app.ui.theme.DarkBg2

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

    @Test fun pinnedContainerAndInverseRolesMeetWcagAa() {
        listOf(
            DarkOnPrimaryContainer to DarkPrimaryContainer,
            OrangeDark2 to DarkWarnContainer,
            DarkOnErrorContainer to DarkErrorContainer,
            DarkBg to DarkText, // inverse surface (snackbar)
            PetrolDeep to DarkText, // inversePrimary (snackbar action)
            LightOnPrimaryContainer to LightPrimaryContainer,
            AmberText to LightWarnContainer,
            LightOnErrorContainer to LightErrorContainer,
            LightBg to LightInverseSurface,
            PetrolLinkDark to LightInverseSurface,
        ).forEach { (foreground, background) ->
            val ratio = contrast(foreground, background)
            assertTrue("Contrast $ratio for $foreground on $background", ratio >= 4.5)
        }
    }

    // Outlined/text buttons use RytmSemantic.link in dark theme, not primary.
    @Test fun darkLinkTextMeetsWcagAaOnCardsAndSheets() {
        listOf(DarkBg1, DarkBg2, DarkBg3).forEach { bg ->
            val ratio = contrast(PetrolLinkDark, bg)
            assertTrue("Contrast $ratio for link on $bg", ratio >= 4.5)
        }
    }

    // The accent fill carries labels ("+", button text) and is itself used as
    // an icon tint on cards: AA for its ink, 3:1 (non-text) as a tint.
    @Test fun accentFillAndInkMeetWcag() {
        assertTrue(contrast(OnPetrol, Petrol) >= 4.5)
        assertTrue(contrast(Color.White, PetrolDeep) >= 4.5)
        listOf(DarkBg, DarkBg1, DarkBg2, DarkBg3).forEach { bg ->
            assertTrue("Accent tint on $bg", contrast(Petrol, bg) >= 3.0)
        }
        listOf(LightBg, LightBg1, LightBg3).forEach { bg ->
            assertTrue("Accent on $bg", contrast(PetrolDeep, bg) >= 4.5)
        }
    }

    @Test fun moneyTextMeetsWcagAaOnEveryCard() {
        listOf(DarkBg1, DarkBg2, DarkBg3).forEach { bg ->
            assertTrue("Income on $bg", contrast(GreenDark2, bg) >= 4.5)
            assertTrue("Expense on $bg", contrast(RedDarkText, bg) >= 4.5)
        }
        listOf(LightBg, LightBg1, LightBg3).forEach { bg ->
            assertTrue("Income on $bg", contrast(GreenText, bg) >= 4.5)
            assertTrue("Expense on $bg", contrast(RedText, bg) >= 4.5)
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
