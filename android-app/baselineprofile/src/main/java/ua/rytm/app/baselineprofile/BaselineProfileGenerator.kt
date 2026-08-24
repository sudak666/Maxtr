package ua.rytm.app.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun criticalUserJourneys() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
        maxIterations = 1,
        stableIterations = 1,
        strictStability = false,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        startRytm(device)
        device.openFinanceHistory()
        device.scrollFinanceHistory()
    }
}
