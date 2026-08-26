package ua.rytm.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ua.rytm.app.ui.screens.onboarding.OnboardingScreen
import ua.rytm.app.ui.theme.RytmTheme

// Regenerated 2026-08-25 on a real device (Galaxy A51, SM-A515F, API 33)
// via `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ua.rytm.app.ui.OnboardingGoldenTest`,
// after the design-audit pass reworked OnboardingScreen (real HorizontalPager
// + swipe, a Back button on pages 2-3, heightIn instead of a fixed 52dp
// button) left the previous two hashes stale. Values below are the real
// `actual` output read from that run's assertion failures, not guessed.
class OnboardingGoldenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightUkrainianPhoneGolden() = verifyGolden("uk", dark = false, expected = "777bcd62895304aa127ba678291caf0559b9c3b35d77dafe5ea44e73edb58dcf")

    @Test fun darkEnglishPhoneGolden() = verifyGolden("en", dark = true, expected = "c3224ee3098d084510abbd7a8223ec1684698b224f7e98236976992a81d6f037")

    private fun verifyGolden(language: String, dark: Boolean, expected: String) {
        val base = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = base.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density = 3f, fontScale = 1f),
            ) {
                Box(Modifier.size(360.dp, 640.dp)) { RytmTheme(darkTheme = dark) { OnboardingScreen {} } }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag("onboarding-screen").captureToImage().asAndroidBitmap()
        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        val actual = MessageDigest.getInstance("SHA-256").digest(buffer.array()).joinToString("") { "%02x".format(it) }
        assertEquals("Golden drift for $language dark=$dark", expected, actual)
    }
}
