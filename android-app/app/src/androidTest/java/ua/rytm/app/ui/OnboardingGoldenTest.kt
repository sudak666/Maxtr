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

class OnboardingGoldenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightUkrainianPhoneGolden() = verifyGolden("uk", dark = false, expected = "b54d8ec6273c5b350a0453bfe5066a33b19c070397337e7a3e41dc37b986c0f7")

    @Test fun darkEnglishPhoneGolden() = verifyGolden("en", dark = true, expected = "a80e1de629b4dd1e7951f131c430385c0fe8a6117a8b991433293d4a99f723bd")

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
