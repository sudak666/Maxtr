package ua.rytm.app.data

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import ua.rytm.app.ui.theme.DarkBg
import ua.rytm.app.ui.theme.DarkBg1
import ua.rytm.app.ui.theme.DarkBg2
import ua.rytm.app.ui.theme.DarkBg3
import ua.rytm.app.ui.theme.DarkBorder
import ua.rytm.app.ui.theme.DarkBorder2
import ua.rytm.app.ui.theme.DarkText
import ua.rytm.app.ui.theme.DarkTextStrong
import ua.rytm.app.ui.theme.Purple3
import ua.rytm.app.ui.theme.PurpleDark

class PwaSourceParityTest {
    private val html by lazy { repoFile("index.html").readText() }

    @Test fun darkPaletteMatchesLivePwaTokens() {
        mapOf(
            "bg" to DarkBg, "bg1" to DarkBg1, "bg2" to DarkBg2, "bg3" to DarkBg3,
            "border" to DarkBorder, "border2" to DarkBorder2,
            "text" to DarkText, "text-strong" to DarkTextStrong,
            "purple" to PurpleDark, "purple3" to Purple3,
        ).forEach { (token, color) ->
            val expected = "--$token:#${color.hex()};"
            assertTrue("PWA token --$token drifted; expected $expected", html.contains(expected, ignoreCase = true))
        }
    }

    @Test fun interactionGeometryAndTimingMatchLivePwaCss() {
        listOf(
            "left:14px;right:14px;bottom:calc(14px + env(safe-area-inset-bottom))",
            ".tab-btn:active{transform:scale(.88)}",
            "translateY(4px)",
            ".tab-content.tab-in{animation:tabContentIn .18s ease-out both}",
            ".chart-bar{width:100%;border-radius:6px 6px 2px 2px;transition:height .5s ease",
            ".analytics-donut.donut-anim{animation:donutIn .6s",
            "outline:2px solid var(--purple2);outline-offset:3px;box-shadow:0 0 0 4px rgba(139,92,246,.22)",
        ).forEach { contract -> assertTrue("Missing PWA contract: $contract", html.contains(contract)) }
    }

    private fun Color.hex(): String = "%02x%02x%02x".format(
        (red * 255).roundToInt(),
        (green * 255).roundToInt(),
        (blue * 255).roundToInt(),
    )

    private fun repoFile(name: String): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val candidate = File(current, name)
            if (candidate.isFile) return candidate
            current = current.parentFile
        }
        error("Cannot find $name from ${System.getProperty("user.dir")}")
    }
}
