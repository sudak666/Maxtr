package ua.rytm.app.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupBaselineProfile() = benchmarkStartup(CompilationMode.Partial())

    @Test
    fun startupNoCompilation() = benchmarkStartup(CompilationMode.None())

    @Test
    fun financeScrollBaselineProfile() = benchmarkScroll(CompilationMode.Partial())

    @Test
    fun financeScrollNoCompilation() = benchmarkScroll(CompilationMode.None())

    private fun benchmarkStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private fun benchmarkScroll(compilationMode: CompilationMode) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                startRytm(device)
                device.openFinanceHistory()
            },
        ) {
            device.scrollFinanceHistory(4)
        }
    }
}
