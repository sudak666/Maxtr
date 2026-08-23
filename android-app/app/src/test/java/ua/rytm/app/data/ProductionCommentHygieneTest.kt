package ua.rytm.app.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionCommentHygieneTest {
    @Test fun productionCommentsDoNotContainMigrationHistory() {
        val history = Regex("ANDROID_MIGRATION|real bug found|chesno|migration step|step[- ]\\d+", RegexOption.IGNORE_CASE)
        val violations = findSourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence().mapIndexedNotNull { index, line ->
                    line.takeIf { it.trimStart().startsWith("//") && history.containsMatchIn(it) }
                        ?.let { "${file.name}:${index + 1}" }
                }
            }
            .toList()
        assertTrue("Migration history belongs in project docs: $violations", violations.isEmpty())
    }

    private fun findSourceRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            File(current, "app/src/main/java").takeIf(File::isDirectory)?.let { return it }
            current = current.parentFile
        }
        error("Cannot find app/src/main/java")
    }
}
