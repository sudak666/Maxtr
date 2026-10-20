package ua.rytm.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guard for the spacing scale: a padding/spacedBy/Spacer written as a raw
 * 4/8/12/16/20/24 dp must use RytmSpacing instead. Off-scale values (6, 10,
 * 14 …) are still allowed — they are deliberate, ported from the PWA's CSS.
 */
class SpacingTokensTest {
    @Test fun ladderSpacingUsesTokens() {
        val root = File("src/main/java")
        assertTrue("run from the app module", root.isDirectory)
        val call = Regex("""(\.padding\(|\bPaddingValues\(|spacedBy\(|Spacer\((?:modifier = )?Modifier\.(?:height|width)\()([^()]*)\)""")
        val ladder = Regex("""(?<![\w.])(4|8|12|16|20|24)\.dp\b""")
        val offenders = root.walkTopDown()
            .filter { it.extension == "kt" && !it.path.replace('\\', '/').contains("/ui/theme/") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (call.findAll(line).any { ladder.containsMatchIn(it.groupValues[2]) }) "${f.path}:${i + 1}" else null
            } }
            .toList()
        assertTrue("Use RytmSpacing for on-scale spacing:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
