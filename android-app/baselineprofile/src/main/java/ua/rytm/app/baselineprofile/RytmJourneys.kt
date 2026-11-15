package ua.rytm.app.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import android.os.SystemClock

// -e targetPackage ua.rytm.app.qa runs the same journeys against the QA
// install (see baselineprofile/README.md: recording on a signed-in QA build).
internal val TARGET_PACKAGE: String =
    androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("targetPackage") ?: "ua.rytm.app"

internal fun MacrobenchmarkScope.startRytm(device: UiDevice) {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 10_000)
}

internal fun UiDevice.openFinanceHistory() {
    findObject(By.text("Фінанси"))?.click()
    SystemClock.sleep(600)
    findObject(By.text("Переглянути всі"))?.click()
    SystemClock.sleep(600)
}

internal fun UiDevice.scrollFinanceHistory(iterations: Int = 5) {
    repeat(iterations) {
        swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 3, 24)
        SystemClock.sleep(450)
    }
}
